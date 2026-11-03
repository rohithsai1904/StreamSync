FROM alpine:latest

RUN apk add --no-cache bash curl

COPY register-connector.sh /register-connector.sh

RUN chmod +x /register-connector.sh

ENTRYPOINT ["/register-connector.sh"]