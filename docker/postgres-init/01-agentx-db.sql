-- Runs once, the first time the postgres volume is created. agentx-api gets its own database
-- because it and payment-service both use Flyway and must not share one migration history.
CREATE DATABASE agentx OWNER app;
