# 与脱敏学习资料中的旧实现对比

这份表只用于说明独立重构时解决了哪些通用工程风险。它不复制企业源码，不使用真实业务数据，也不代表对现实生产系统当前版本的评价。

| # | 学习资料中的风险 | EquipOps 的处理 | 证据 |
|---|---|---|---|
| 1 | 把用户 ID 当 Token 存在进程内 Map，重启丢失且多实例不共享 | JWT 签名、过期时间、无状态 Security Filter Chain | `JwtUtil`、`JwtAuthenticationFilter` |
| 2 | 登录与接口权限混在业务 Controller 手工判断 | 认证过滤器、401/403 处理器、方法级 Authorities | `SecurityConfig`、`@PreAuthorize` |
| 3 | 只有角色名判断，没有细粒度权限码 | user-role-permission 多对多与权限码集合 | `PermissionService`、Flyway V1/V2 |
| 4 | 只做菜单隐藏，没有服务端部门数据范围 | Service 强制写入/校验 deptId，按 ID 越权返回 404 | `DeviceServiceImpl`、`DeviceIsolationIT` |
| 5 | Controller 手工 `readTree` 解析 JSON | DTO + Jackson 绑定 + Bean Validation | 各领域 `domain/dto`、`GlobalExceptionHandler` |
| 6 | 更新接口直接接收数据库实体，存在越权字段写入 | Create/Update DTO 白名单，服务端生成审计与隔离字段 | `DeviceCreateDTO`、`DeviceUpdateDTO` |
| 7 | 手工改库，环境表结构容易漂移 | 版本化 Flyway 迁移，从空库可重建 | `src/main/resources/db/migration` |
| 8 | 状态值可被任意覆盖，允许跳状态 | 显式状态迁移表 + 旧状态条件更新 | `OrderStateService`、`RepairOrderServiceImpl` |
| 9 | 事务边界放 Controller 或多步写入没有统一事务 | Service 层 `@Transactional(rollbackFor=Exception.class)` + 回滚测试 | `RolePermissionServiceIT`、ADR-011 |
| 10 | 并发接单先查后改，可能一单多人 | `UPDATE ... WHERE status=PENDING`，影响行数决定胜者 | `accept`、并发实验 |
| 11 | 重复提交和缓存故障缺少最终一致性防线 | Idempotency-Key + 请求摘要 + Redis 快路径 + MySQL 复合唯一键 | V4、`RepairOrderControllerIT`、ADR-015 |

本仓库仍有明确边界：Refresh Token、可靠缓存删除补偿、完整监控、MQ、RAG 和全部前端联调尚未完成，详见根 README。
