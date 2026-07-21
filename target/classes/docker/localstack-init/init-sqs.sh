#!/bin/bash
echo "Initializing LocalStack SQS FIFO Queue..."
awslocal sqs create-queue \
    --queue-name telemetry-events.fifo \
    --attributes FifoQueue=true,ContentBasedDeduplication=true
echo "LocalStack SQS FIFO Queue created successfully."
