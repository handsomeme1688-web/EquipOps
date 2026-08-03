-- 幂等键按用户隔离，并保存请求摘要用于识别“同键不同请求”。
-- 增加超时标记；超时扫描使用 (status, timed_out, request_time) 索引。

ALTER TABLE `repair_order`
    DROP INDEX `idempotency_key`,
    ADD COLUMN `request_hash` CHAR(64) NULL COMMENT '创建请求 SHA-256 摘要' AFTER `idempotency_key`,
    ADD COLUMN `timed_out` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已标记超时:0-否,1-是' AFTER `close_time`,
    ADD COLUMN `timeout_time` DATETIME NULL COMMENT '标记超时时间' AFTER `timed_out`;

-- 兼容迁移前已经存在的工单，算法与 OrderRequestFingerprint 的规范串保持一致。
UPDATE `repair_order`
SET `request_hash` = SHA2(
        CONCAT(`device_id`, CHAR(10), TRIM(`description`), CHAR(10), `priority`),
        256
    )
WHERE `request_hash` IS NULL;

ALTER TABLE `repair_order`
    MODIFY COLUMN `request_hash` CHAR(64) NOT NULL COMMENT '创建请求 SHA-256 摘要',
    ADD UNIQUE KEY `uk_repair_order_user_idempotency` (`request_user_id`, `idempotency_key`),
    ADD KEY `idx_order_pending_timeout` (`status`, `timed_out`, `request_time`);
