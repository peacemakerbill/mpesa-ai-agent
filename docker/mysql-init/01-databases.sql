-- Runs once, the first time the mysql volume is created. agentx-api gets its own database.
CREATE DATABASE IF NOT EXISTS agentx CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
GRANT ALL PRIVILEGES ON agentx.* TO 'app'@'%';
GRANT ALL PRIVILEGES ON payments.* TO 'app'@'%';
FLUSH PRIVILEGES;
