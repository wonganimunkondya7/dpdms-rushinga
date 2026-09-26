CREATE TABLE IF NOT EXISTS alert_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, hazard VARCHAR(30), channel VARCHAR(30), recipient VARCHAR(200), message VARCHAR(1000), delivery_status VARCHAR(50), created_at TIMESTAMP NOT NULL, delivered_at TIMESTAMP NULL);
CREATE TABLE IF NOT EXISTS whatsapp_message_receipt (provider_message_id VARCHAR(200) PRIMARY KEY, alert_id BIGINT NOT NULL);
