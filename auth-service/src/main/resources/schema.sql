CREATE TABLE IF NOT EXISTS app_users (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(150) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, role VARCHAR(30) NOT NULL, hazard VARCHAR(30), ward VARCHAR(100));
SET @province_column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'app_users' AND column_name = 'province');
SET @province_column_ddl = IF(@province_column_exists = 0, 'ALTER TABLE app_users ADD COLUMN province VARCHAR(100) NULL', 'SELECT 1');
PREPARE province_column_stmt FROM @province_column_ddl;
EXECUTE province_column_stmt;
DEALLOCATE PREPARE province_column_stmt;
