package com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.mongodb;

import com.telecom.analytics.platform.domain.model.AntennaEvent;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "antenna_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AntennaEventMongoDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("event_id")
    private String eventId;

    @Field("antenna_id")
    private String antennaId;

    @Field("event_type")
    private String eventType;

    @Field("duration_ms")
    private Long durationMs;

    @Field("bytes_transferred")
    private Long bytesTransferred;

    @Field("timestamp")
    private Instant timestamp;

    @Version
    private Integer version;

    public static AntennaEventMongoDocument fromDomain(AntennaEvent event) {
        if (event == null) {
            return null;
        }
        return AntennaEventMongoDocument.builder()
                .eventId(event.eventId())
                .antennaId(event.antennaId())
                .eventType(event.eventType())
                .durationMs(event.durationMs())
                .bytesTransferred(event.bytesTransferred())
                .timestamp(event.timestamp())
                .version(event.version())
                .build();
    }

    public AntennaEvent toDomain() {
        return AntennaEvent.builder()
                .eventId(this.eventId)
                .antennaId(this.antennaId)
                .eventType(this.eventType)
                .durationMs(this.durationMs)
                .bytesTransferred(this.bytesTransferred)
                .timestamp(this.timestamp)
                .version(this.version)
                .build();
    }
}
