CREATE TABLE IF NOT EXISTS alert_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, hazard VARCHAR(30), channel VARCHAR(30), recipient VARCHAR(200), message VARCHAR(1000), delivery_status VARCHAR(50), delivery_error VARCHAR(1000), created_at TIMESTAMP NOT NULL, delivered_at TIMESTAMP NULL);
SET @dpdms_delivery_error_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'alert_log' AND column_name = 'delivery_error');
SET @dpdms_delivery_error_ddl = IF(@dpdms_delivery_error_exists = 0, 'ALTER TABLE alert_log ADD COLUMN delivery_error VARCHAR(1000)', 'SELECT 1');
PREPARE dpdms_delivery_error_stmt FROM @dpdms_delivery_error_ddl;
EXECUTE dpdms_delivery_error_stmt;
DEALLOCATE PREPARE dpdms_delivery_error_stmt;
CREATE TABLE IF NOT EXISTS whatsapp_message_receipt (provider_message_id VARCHAR(200) PRIMARY KEY, alert_id BIGINT NOT NULL);
