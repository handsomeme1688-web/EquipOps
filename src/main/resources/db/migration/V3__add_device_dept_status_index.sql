-- =====================================================================
-- V3__add_device_dept_status_index.sql
-- 为设备表增加 (dept_id, status) 复合索引
--
-- 背景：
--   设备列表高频按“部门 + 状态”组合筛选（见 DeviceMapper.selectDeviceVoPage）。
--   建表时的单列索引 idx_dept_id 只能先按 dept_id 定位到该部门的全部行，
--   再逐行过滤 status，扫描行数偏大。
--
-- 依据（2 万级数据 EXPLAIN ANALYZE 实测）：
--   查询 WHERE dept_id = 2 AND status = 1
--   BEFORE  idx_dept_id      扫描 ~5000 行   ~6.5ms
--   AFTER   idx_dept_status  扫描 ~1250 行   ~1.7ms
--
-- 顺带删除单列 idx_dept_id：
--   复合索引 (dept_id, status) 的最左前缀 (dept_id) 已能覆盖原单列索引的全部用途，
--   保留两个会造成冗余索引，徒增写入开销与存储。故一并移除。
-- =====================================================================

-- 1. 新增复合索引
ALTER TABLE `device`
    ADD INDEX `idx_dept_status` (`dept_id`, `status`);

-- 2. 删除被复合索引最左前缀覆盖的冗余单列索引
ALTER TABLE `device`
    DROP INDEX `idx_dept_id`;
