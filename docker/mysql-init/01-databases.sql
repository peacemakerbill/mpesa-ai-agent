-- Runs once, the first time the mysql volume is created. payments is made by MYSQL_DATABASE.
-- ganji-api gets its own database because it and payment-service both use Flyway and must not share one history.
CREATE DATABASE IF NOT EXISTS ganji CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
GRANT ALL PRIVILEGES ON ganji.* TO 'app'@'%';
GRANT ALL PRIVILEGES ON payments.* TO 'app'@'%';
FLUSH PRIVILEGES;
