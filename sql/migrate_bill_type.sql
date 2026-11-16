-- 收支类型改造迁移脚本（可重复执行）
USE `bill_db`;

-- 1. 账单表增加收支类型字段
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bill_db' AND TABLE_NAME = 't_bill' AND COLUMN_NAME = 'bill_type');
SET @ddl := IF(@col = 0,
    'ALTER TABLE `t_bill` ADD COLUMN `bill_type` VARCHAR(20) NOT NULL DEFAULT ''EXPENSE'' COMMENT ''收支类型：EXPENSE-支出，INCOME-收入'' AFTER `category_name`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 分类表增加分类类型字段
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'bill_db' AND TABLE_NAME = 't_category' AND COLUMN_NAME = 'type');
SET @ddl := IF(@col = 0,
    'ALTER TABLE `t_category` ADD COLUMN `type` VARCHAR(20) NOT NULL DEFAULT ''EXPENSE'' COMMENT ''分类类型：EXPENSE-支出，INCOME-收入'' AFTER `name`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 补充收入分类（按名称去重）
INSERT INTO `t_category` (`name`, `type`, `icon`, `sort`)
SELECT t.`name`, t.`type`, t.`icon`, t.`sort`
FROM (
    SELECT '工资' AS `name`, 'INCOME' AS `type`, '💰' AS `icon`, 10 AS `sort`
    UNION ALL SELECT '奖金', 'INCOME', '🎁', 11
    UNION ALL SELECT '理财', 'INCOME', '📈', 12
    UNION ALL SELECT '兼职', 'INCOME', '💼', 13
    UNION ALL SELECT '红包', 'INCOME', '🧧', 14
    UNION ALL SELECT '退款', 'INCOME', '↩️', 15
    UNION ALL SELECT '其他收入', 'INCOME', '💵', 16
) AS t
WHERE NOT EXISTS (
    SELECT 1 FROM `t_category` c WHERE c.`name` = t.`name`
);
