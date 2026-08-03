-- Day 19 实验 B 优化组：恢复生产使用的 (dept_id, status) 复合索引。
-- 只能在隔离库 equipops_benchmark 执行。

DROP PROCEDURE IF EXISTS assert_equipops_benchmark;
DELIMITER //
CREATE PROCEDURE assert_equipops_benchmark()
BEGIN
    IF DATABASE() IS NULL OR DATABASE() <> 'equipops_benchmark' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing to run outside equipops_benchmark';
    END IF;
END//
DELIMITER ;

CALL assert_equipops_benchmark();
DROP PROCEDURE assert_equipops_benchmark;

SET @has_dept_status = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'device'
      AND index_name = 'idx_dept_status'
);
SET @ddl = IF(
    @has_dept_status = 0,
    'ALTER TABLE device ADD INDEX idx_dept_status (dept_id, status)',
    'DO 0'
);
PREPARE benchmark_ddl FROM @ddl;
EXECUTE benchmark_ddl;
DEALLOCATE PREPARE benchmark_ddl;

SET @has_dept_id = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'device'
      AND index_name = 'idx_dept_id'
);
SET @ddl = IF(
    @has_dept_id > 0,
    'ALTER TABLE device DROP INDEX idx_dept_id',
    'DO 0'
);
PREPARE benchmark_ddl FROM @ddl;
EXECUTE benchmark_ddl;
DEALLOCATE PREPARE benchmark_ddl;

EXPLAIN ANALYZE
SELECT d.id, d.code, d.name, d.model, d.location, d.status, d.description,
       d.dept_id, d.owner_id, dp.name AS dept_name, op.real_name AS owner_name,
       d.create_time, d.update_time
FROM device d
LEFT JOIN dept dp ON d.dept_id = dp.id
LEFT JOIN `user` op ON d.owner_id = op.id
WHERE d.dept_id = 2 AND d.status = 1
LIMIT 20;
