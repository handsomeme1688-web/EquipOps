# 建表按依赖顺序：dept → role → permission → user
# → user_role → role_permission → device → device_file
# → repair_order → repair_record → operation_log → message_outbox


DROP TABLE IF EXISTS `dept`;
CREATE TABLE `dept`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `parent_id` BIGINT COMMENT '上级部门id',
    `name` VARCHAR(50) UNIQUE NOT NULL COMMENT '部门名称',
    `status` TINYINT NOT NULL COMMENT '部门当前状态: 0-停用, 1-正常',
    `description` VARCHAR(255) COMMENT '部门描述',
    `create_by` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_by` BIGINT COMMENT '更新人',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    KEY `idx_parent_id` (`parent_id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';


DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `name` VARCHAR(50) UNIQUE NOT NULL COMMENT '角色名称',
    `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码,程序标识,如 DEPT_MANAGER',
    `is_built_in` TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置角色',
    `description` VARCHAR(255) COMMENT '角色描述',
    `create_by` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_by` BIGINT COMMENT '更新人',
    `update_time` DATETIME NOT NULL COMMENT '更新时间'
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';


DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `name` VARCHAR(50) UNIQUE NOT NULL COMMENT '给配权限的管理员看',
    `code` VARCHAR(50) NOT NULL UNIQUE COMMENT '给机器看的权限码',
    `resource` VARCHAR(50) COMMENT '资源前缀，方便按组查询',
    `description` VARCHAR(255) COMMENT '权限说明',
    `create_by` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_by` BIGINT COMMENT '更新人',
    `update_time` DATETIME NOT NULL COMMENT '更新时间'
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';


DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `dept_id` BIGINT NOT NULL COMMENT '部门id',
    `username` VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
    `phone` VARCHAR(20) COMMENT '手机号',
    `email` VARCHAR(50) COMMENT '邮箱',
    `status` TINYINT NOT NULL COMMENT '用户当前状态: 0-停用, 1-正常',
    `remark` VARCHAR(255) COMMENT '用户描述',
    `login_ip` VARCHAR(64) COMMENT '登录ip',
    `login_time` DATETIME COMMENT '登录时间',
    `create_by` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_by` BIGINT COMMENT '更新人',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    `pwd_update_time` DATETIME NOT NULL COMMENT '密码更新时间',
    KEY `idx_dept_id` (`dept_id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';


DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户id',
    `role_id` BIGINT NOT NULL COMMENT '角色id',
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
#   按最左前缀原则,查"某用户有哪些角色"能直接用它,不需要再给 user_id 单独建索引。
    KEY `idx_role_id` (`role_id`) COMMENT '加速查询，例如：查拥有“管理员”角色的所有用户有哪些'
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色表';


DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `permission_id` BIGINT NOT NULL COMMENT '权限id',
    `role_id` BIGINT NOT NULL COMMENT '角色id',
    UNIQUE KEY `uk_role_permission` (`permission_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限表';


DROP TABLE IF EXISTS `device`;
CREATE TABLE `device`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `code` VARCHAR(50) UNIQUE NOT NULL COMMENT '设备编号',
    `dept_id` BIGINT NOT NULL COMMENT '部门id，必须有索引',
    `owner_id` BIGINT NOT NULL COMMENT '责任人，指向user',
    `name` VARCHAR(50) NOT NULL COMMENT '设备名可以重名，通过设备编号code区分即可',
    `model` VARCHAR(50) NOT NULL COMMENT '设备型号',
    `location` VARCHAR(50) NOT NULL COMMENT '安装位置',
    `status` TINYINT NOT NULL COMMENT '设备当前状态: 0-停用, 1-正常, 2-维修, 3-报废',
    `description` VARCHAR(255) COMMENT '设备描述',
    `create_by` BIGINT COMMENT '创建人',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_by` BIGINT COMMENT '更新人',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_owner_id` (`owner_id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备表';


DROP TABLE IF EXISTS `device_file`;
CREATE TABLE `device_file`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `device_id` BIGINT NOT NULL COMMENT '设备ID,不能加UNIQUE，每台设备不是只能上传一次文件',
    `file_name` VARCHAR(500) NOT NULL COMMENT '原始文件名',
    `storage_key` VARCHAR(255) NOT NULL COMMENT '存储key',
    `size` BIGINT NOT NULL COMMENT '文件大小',
    `content_type` VARCHAR(50) NOT NULL COMMENT '文件内容类型',
    `upload_by` BIGINT NOT NULL COMMENT '上传人',
    `upload_time` DATETIME NOT NULL COMMENT '上传时间',
    KEY `idx_device_id` (`device_id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备文档';


DROP TABLE IF EXISTS `repair_order`;
CREATE TABLE `repair_order`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `device_id` BIGINT NOT NULL COMMENT '设备ID,不能加UNIQUE，每台设备不是只能报修一次',
    `request_user_id` BIGINT NOT NULL COMMENT '用户ID,报修人',
    `request_time` DATETIME NOT NULL COMMENT '报修时间',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `idempotency_key` VARCHAR(255) NOT NULL UNIQUE COMMENT '幂等键',
    `dept_id` BIGINT NOT NULL COMMENT '保修部门ID',
    `assign_id` BIGINT COMMENT '承接人ID',
    `status` TINYINT NOT NULL COMMENT '工单状态: 0-待受理, 1-已接单, 2-维修中, 3-委外中, 4-待验收, 5-已完成, 6-已关闭',
    `description` VARCHAR(255) NOT NULL COMMENT '报修内容',
    `priority` TINYINT NOT NULL COMMENT '优先级: 1-低, 2-中, 3-高',
    `create_by` BIGINT NOT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_by` BIGINT COMMENT '更新人',
    `update_time` DATETIME COMMENT '更新时间',
    `accept_time` DATETIME COMMENT '接单时间',
    `finish_time` DATETIME COMMENT '完成时间',
    `check_time` DATETIME COMMENT '验收时间',
    `close_time` DATETIME COMMENT '关闭时间',
    KEY `idx_dept_id` (`dept_id`),              -- 数据隔离
    KEY `idx_device_id` (`device_id`),          -- 查某设备的报修历史
    KEY `idx_request_user_id` (`request_user_id`), -- 员工查自己发起的单
    KEY `idx_assign_id` (`assign_id`),          -- 工程师查自己承接的单
    KEY `idx_status_request_time` (`status`, `request_time`)  -- Day 18 扫超时工单
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报修单';


DROP TABLE IF EXISTS `repair_record`;
CREATE TABLE `repair_record`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `order_id` BIGINT NOT NULL COMMENT '报修单ID',
    `operator_id` BIGINT COMMENT '操作人',
    `from_status` TINYINT NOT NULL COMMENT '工单状态: 0-待受理, 1-已接单, 2-维修中, 3-委外中, 4-待验收, 5-已完成, 6-已关闭',
    `to_status` TINYINT NOT NULL COMMENT '工单状态: 0-待受理, 1-已接单, 2-维修中, 3-委外中, 4-待验收, 5-已完成, 6-已关闭',
    `description` VARCHAR(255) COMMENT '流转备注，可以没有',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',-- 流转记录一旦创建不能更改
    `action` VARCHAR(50) NOT NULL COMMENT '动作：接单 / 开始维修 / 申请委外 / 提交验收 / 验收通过 / 验收退回',
    KEY `idx_order_id` (`order_id`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单流转记录';


DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '请求追踪标识，建索引',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID，建索引，指向user',
    `operator_name` VARCHAR(50) NOT NULL COMMENT '冗余存姓名快照。用户改名或停用后，日志要保留当时的名字，不能靠JOIN查',
    `resource_type` VARCHAR(50) NOT NULL COMMENT '资源类型',
    `resource_id` BIGINT COMMENT '资源ID,允许null，登录登出操作没有资源ID',
    `action` VARCHAR(50) NOT NULL COMMENT '动作，create、update、delete',
    `result` TINYINT NOT NULL COMMENT '成功或失败：0-失败, 1-成功',
    `error_msg` VARCHAR(500) COMMENT '失败原因',
    `ip` VARCHAR(64) COMMENT '来源IP',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_create_time` (`create_time`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日志表';


DROP TABLE IF EXISTS `message_outbox`;
CREATE TABLE `message_outbox`(
    `id` BIGINT NOT NULL PRIMARY KEY COMMENT '主键',
    `aggregate_id` BIGINT NOT NULL COMMENT '业务主键，如工单ID',
    `aggregate_type` VARCHAR(50) NOT NULL COMMENT '聚合类型',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型',
    `payload` JSON NOT NULL COMMENT '消息体，如JSON',
    `status` TINYINT NOT NULL COMMENT '消息状态:0-待发送, 1-发送中, 2-已发送, 3-失败',
    `retry_count` TINYINT NOT NULL COMMENT '重试次数',
    `next_retry_time` DATETIME COMMENT '下次重试时间',
    `create_time` DATETIME NOT NULL COMMENT '写入时间',
    `sent_time` DATETIME COMMENT '发送成功时间',
    KEY `idx_status_next_retry` (`status`, `next_retry_time`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息表';








