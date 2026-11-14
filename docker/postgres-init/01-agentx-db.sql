-- Runs once, the first time the postgres volume is created. agentx-api gets its own database.
CREATE DATABASE agentx OWNER app;
