package com.telecom.analytics.platform.infrastructure.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.analytics.platform.domain.model.Antenna;
import com.telecom.analytics.platform.domain.model.AntennaEvent;
import com.telecom.analytics.platform.domain.repository.AntennaRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@Profile("simulate")
@RequiredArgsConstructor
public class TelemetrySimulationRunner implements CommandLineRunner {

    private final AntennaRepository antennaRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== INICIANDO SIMULACIÓN DE TRÁFICO CONTINUO DE ANTENAS ===");

        // 1. Sembrar antenas de prueba en PostgreSQL si no existen
        List<Antenna> antennas = List.of(
                Antenna.builder().id("antenna-madrid-01").location("Madrid").type("5G").theoreticalCapacity(1000).status("ACTIVE").build(),
                Antenna.builder().id("antenna-madrid-02").location("Madrid").type("4G").theoreticalCapacity(300).status("ACTIVE").build(),
                Antenna.builder().id("antenna-barcelona-01").location("Barcelona").type("5G").theoreticalCapacity(1200).status("ACTIVE").build(),
                Antenna.builder().id("antenna-valencia-01").location("Valencia").type("5G").theoreticalCapacity(800).status("ACTIVE").build(),
                Antenna.builder().id("antenna-sevilla-01").location("Sevilla").type("4G").theoreticalCapacity(250).status("ACTIVE").build(),
                Antenna.builder().id("antenna-bilbao-01").location("Bilbao").type("5G").theoreticalCapacity(600).status("INACTIVE").build()
        );

        log.info("Verificando y sembrando antenas en PostgreSQL...");
        for (Antenna antenna : antennas) {
            if (antennaRepository.findById(antenna.id()).isEmpty()) {
                antennaRepository.save(antenna);
                log.info("Antena sembrada: {}", antenna.id());
            }
        }

        // 2. Loop de generación de carga continua
        Random random = new Random();
        List<String> eventTypes = List.of("VOICE_CALL", "DATA_SESSION");
        long count = 0;

        log.info("Streaming de eventos de carga continua activo. Presione Ctrl+C para detener...");

        try {
            while (!Thread.currentThread().isInterrupted()) {
                // Selecciona una antena activa al azar (evitando Bilbao que es INACTIVE)
                Antenna antenna = antennas.get(random.nextInt(antennas.size() - 1));
                
                String eventType = eventTypes.get(random.nextInt(eventTypes.size()));
                long duration = 100 + random.nextInt(3600 * 1000); // 100ms a 1 hora
                long bytes = eventType.equals("DATA_SESSION") ? (long) (1024L * 1024L * random.nextDouble() * 200) : 0; // 0 a 200 MB

                AntennaEvent event = AntennaEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .antennaId(antenna.id())
                        .eventType(eventType)
                        .durationMs(duration)
                        .bytesTransferred(bytes)
                        .timestamp(Instant.now())
                        .version(null)
                        .build();

                String payload = objectMapper.writeValueAsString(event);

                sqsTemplate.send(to -> to
                        .queue("telemetry-events.fifo")
                        .payload(payload)
                        .header("message-group-id", "antenna-group-" + antenna.id())
                        .header("message-deduplication-id", event.eventId())
                );

                count++;
                if (count % 200 == 0) {
                    log.info("Enviados {} eventos de telemetría a la cola SQS...", count);
                }

                Thread.sleep(50); // ~1200 mensajes por minuto
            }
        } catch (InterruptedException e) {
            log.info("Simulación interrumpida por el usuario.");
            Thread.currentThread().interrupt();
        }

        log.info("=== SIMULACIÓN FINALIZADA ===");
    }
}
