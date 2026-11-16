-- =============================================================
-- 智能记账系统 数据库初始化脚本
-- 可重复执行：所有建库/建表语句均带 IF NOT EXISTS，
-- 索引内联在建表语句中，初始分类按名称去重插入。
-- 执行方式：DB_PASSWORD=<你的密码> bash sql/init.sh（脚本自动替换占位符）
-- =============================================================

-- 1. 建库（utf8mb4）
CREATE DATABASE IF NOT EXISTS `bill_db`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. 应用账号与授权
CREATE USER IF NOT EXISTS 'bill_user'@'localhost' IDENTIFIED BY '__DB_PASSWORD__';
-- 已存在时也确保密码与脚本一致
ALTER USER 'bill_user'@'localhost' IDENTIFIED BY '__DB_PASSWORD__';
GRANT ALL PRIVILEGES ON `bill_db`.* TO 'bill_user'@'localhost';
FLUSH PRIVILEGES;

USE `bill_db`;

-- 3. 分类表
CREATE TABLE IF NOT EXISTS `t_category` (
    `id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID，主键自增',
    `name` VARCHAR(50)  NOT NULL                COMMENT '分类名称',
    `icon` VARCHAR(255) DEFAULT NULL            COMMENT '分类图标（emoji 或图标名）',
    `sort` INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单分类表';

-- 4. 账单表
CREATE TABLE IF NOT EXISTS `t_bill` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '账单ID，主键自增',
    `amount`        DECIMAL(10,2) NOT NULL                COMMENT '金额（元）',
    `category_id`   BIGINT        NOT NULL                COMMENT '分类ID',
    `category_name` VARCHAR(50)   NOT NULL                COMMENT '分类名称（冗余存储，便于查询展示）',
    `bill_date`     DATE          NOT NULL                COMMENT '账单日期',
    `note`          VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    `source_type`   VARCHAR(20)   NOT NULL DEFAULT 'MANUAL' COMMENT '录入来源：MANUAL-手动录入，AI-AI识别',
    `ai_raw`        TEXT          DEFAULT NULL            COMMENT 'AI 提取的原始文本',
    `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_date` (`bill_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账单表';

-- 5. 初始分类（按名称去重，可重复执行）
INSERT INTO `t_category` (`name`, `icon`, `sort`)
SELECT t.`name`, t.`icon`, t.`sort`
FROM (
    SELECT '餐饮' AS `name`, '🍜' AS `icon`, 1 AS `sort`
    UNION ALL SELECT '交通', '🚗', 2
    UNION ALL SELECT '购物', '🛒', 3
    UNION ALL SELECT '住房', '🏠', 4
    UNION ALL SELECT '医疗', '🏥', 5
    UNION ALL SELECT '娱乐', '🎮', 6
    UNION ALL SELECT '通讯', '📱', 7
    UNION ALL SELECT '教育', '📚', 8
    UNION ALL SELECT '其他', '📌', 9
) AS t
WHERE NOT EXISTS (
    SELECT 1 FROM `t_category` c WHERE c.`name` = t.`name`
);
