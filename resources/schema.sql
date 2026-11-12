CREATE TABLE IF NOT EXISTS policy_og (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    org_name VARCHAR(255) NOT NULL,
    sample_url VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_org (org_name)
);

CREATE TABLE IF NOT EXISTS policy_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    zh_title VARCHAR(500) NOT NULL,
    original_title VARCHAR(500),
    summary TEXT,
    publish_date DATE,
    source_url VARCHAR(1000),
    pdf_url VARCHAR(1000),
    org_id BIGINT NULL,
    clicks BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 限定前缀长度以避免 utf8mb4 下索引过长
    UNIQUE KEY uk_pdf (pdf_url(255)),
    INDEX idx_publish_date (publish_date),
    INDEX idx_title (zh_title(100)),
    INDEX idx_source (source_url(255)),
    INDEX idx_org (org_id),
    CONSTRAINT fk_policy_org FOREIGN KEY (org_id) REFERENCES policy_og(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(100),
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_action_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    policy_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    extra_json JSON NULL,
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_log_policy FOREIGN KEY (policy_id) REFERENCES policy_data(id) ON DELETE SET NULL,
    INDEX idx_action_type (action_type),
    INDEX idx_user (user_id),
    INDEX idx_policy (policy_id)
);

CREATE TABLE IF NOT EXISTS user_favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    policy_data_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_policy (user_id, policy_data_id),
    CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_fav_policy FOREIGN KEY (policy_data_id) REFERENCES policy_data(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_policy_id (policy_data_id),
    INDEX idx_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `rating` (
                                        `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                        `user_id` BIGINT NULL,
                                        `policy_id` BIGINT NULL,
                                        `rating` DECIMAL(3,2) NULL,
    `action_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_policy_id` (`policy_id`),
    INDEX `idx_action_time` (`action_time`),
    CONSTRAINT `fk_rating_user` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_rating_policy` FOREIGN KEY (`policy_id`) REFERENCES `policy_data`(`id`) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 创建pdf_validation表
CREATE TABLE IF NOT EXISTS pdf_validation (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                policy_id BIGINT NOT NULL UNIQUE,
                                status INT NOT NULL DEFAULT 0 COMMENT '0:未验证, 1:有效, 2:失效',
                                check_result VARCHAR(500) COMMENT '验证结果描述',
                                last_check_time DATETIME,
                                check_time BIGINT COMMENT '验证耗时(毫秒)',
                                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                INDEX idx_policy_id (policy_id),
                                INDEX idx_status (status),
                                INDEX idx_last_check_time (last_check_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建validation_task表
CREATE TABLE IF NOT EXISTS validation_task (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 task_name VARCHAR(200) NOT NULL,
                                 status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, RUNNING, COMPLETED, FAILED, CANCELLED',
                                 total_count INT DEFAULT 0,
                                 processed_count INT DEFAULT 0,
                                 valid_count INT DEFAULT 0,
                                 invalid_count INT DEFAULT 0,
                                 error_message VARCHAR(1000),
                                 start_time DATETIME,
                                 end_time DATETIME,
                                 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 INDEX idx_status (status),
                                 INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建用户-政策关联表
CREATE TABLE `user_policy_relation` (
                                        `id` bigint NOT NULL AUTO_INCREMENT,
                                        `user_id` bigint NOT NULL,
                                        `policy_id` bigint NOT NULL,
                                        `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                                        `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                        PRIMARY KEY (`id`),
                                        UNIQUE KEY `uk_user_policy` (`user_id`,`policy_id`),
                                        KEY `idx_user_id` (`user_id`),
                                        KEY `idx_policy_id` (`policy_id`),
                                        CONSTRAINT `fk_relation_policy` FOREIGN KEY (`policy_id`) REFERENCES `policy_data` (`id`) ON DELETE CASCADE,
                                        CONSTRAINT `fk_relation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-政策关联表（记录用户查询/入库的政策）';
-- 初始角色
INSERT IGNORE INTO roles(code, name) VALUES ('ADMIN','管理员'),('USER','普通用户');

-- 调度说明：默认定时任务在每月1日 02:00 运行，抓取上一个自然月数据
-- 可通过配置或管理接口调整
