#!/bin/bash

CONNECT_URL="http://localhost:8083"
CONNECTOR_NAME="postgres-connector"

# Wait for Kafka Connect to become available
until curl -fsS "$CONNECT_URL/connectors" >/dev/null; do
    sleep 2
done

# Create or update the connector
if curl -fsS "$CONNECT_URL/connectors/$CONNECTOR_NAME" >/dev/null; then
    echo "Connector exists. Updating configuration..."

    curl -fsS -X PUT \
        "$CONNECT_URL/connectors/$CONNECTOR_NAME/config" \
        -H "Content-Type: application/json" \
        --data @postgres-connector.json
else
    echo "Registering connector..."

    curl -fsS -X POST \
        "$CONNECT_URL/connectors" \
        -H "Content-Type: application/json" \
        --data @postgres-connector.json
fi