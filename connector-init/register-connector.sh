#!/bin/bash

CONNECT_URL="http://connect:8083"
CONNECTOR_NAME="postgres-cdc"
CONFIG_FILE="/config/postgres-connector.json"

# Wait for Kafka Connect
until curl -fsS "$CONNECT_URL/connectors" >/dev/null; do
    echo "Waiting for Kafka Connect..."
    sleep 2
done

echo "Kafka Connect is available."

# Extract connector configuration
CONFIG=$(jq -c '.config' "$CONFIG_FILE")

# Check if connector exists
if curl -fsS "$CONNECT_URL/connectors/$CONNECTOR_NAME" >/dev/null; then

    echo "Connector exists. Updating configuration..."

    curl -fsS -X PUT \
        "$CONNECT_URL/connectors/$CONNECTOR_NAME/config" \
        -H "Content-Type: application/json" \
        --data "$CONFIG"

else

    echo "Registering connector..."

    curl -fsS -X POST \
        "$CONNECT_URL/connectors" \
        -H "Content-Type: application/json" \
        --data @"$CONFIG_FILE"

fi

echo "Connector registration completed."