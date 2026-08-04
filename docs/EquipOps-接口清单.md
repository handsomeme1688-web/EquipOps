# EquipOps 接口清单

| 项 | 内容 |
|---|---|
| 版本 | v1.0 |
| 日期 | 2026-07-21 |
| 基础路径 | `/api/v1` |
| 关联文档 | 《需求规格说明书》《工单状态机图》 |

> 本文是需求到实现的中间层。每条接口向上追溯到需求编号，向下决定权限码、数据范围与测试用例。
> 请求体、响应体的字段结构不在本文定义，由接口文档（Knife4j）在实现时生成。

---

## 一、通用约定

**认证方式**：除认证类接口外，全部要求请求头携带 `Authorization: Bearer <access_token>`。

**响应结构**：统一 `Result<T>` 包装，包含业务码、消息、数据体。

**状态码约定**：

| 码 | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数校验失败，响应体含字段级错误信息 |
| 401 | 未认证：无令牌、令牌过期、签名无效 |
| 403 | 已认证但无权限，或数据范围外 |
| 404 | 资源不存在 |
| 409 | 业务冲突（编号重复、状态不允许该操作） |

**401 与 403 不得混用**——这是需求 FR-1-07、FR-1-08 明确要求的，也是一条常见的实现缺陷。

---

## 二、权限码规范

**格式**：`资源:动作`，业务域两段，系统管理域三段。

**动作词表固定**，不得自造同义词：

| 词 | 含义 |
|---|---|
| `view` | 查询（列表与详情共用一个权限码） |
| `create` | 创建 |
| `update` | 修改 |
| `delete` | 删除 |
| `manage` | 增删改的合集（系统管理域使用） |

**HTTP 方法约定**：全项目只使用 `GET` / `POST` / `PUT` / `DELETE` 四个方法。

| 方法 | 用途 | 幂等 |
|---|---|:---:|
| `GET` | 查询 | ✓ |
| `POST` | 创建资源，或执行一个业务动作 | ✗ |
| `PUT` | 全量更新指定资源 | ✓ |
| `DELETE` | 删除指定资源 | ✓ |

不使用 `PATCH`。「停用账户」「接单」「验收」这类操作**是业务动作而非字段更新**——停用账户除了改状态位，还要撤销该用户全部令牌——因此统一用 `POST /资源/{id}/动作` 表达。

**POST 不幂等是 HTTP 语义层面的事实**：POST 创建的资源身份由服务端分配，客户端无法在请求中表达「我要的是哪一个」。这正是创建类接口需要显式设计幂等键的根本原因（见 FR-4-02、ADR-015）。而 `PUT /depts/5` 的 URI 已经携带资源身份，幂等性是语义自带的。

工单流转动作直接取自状态机图的边标签：`accept` / `repair` / `outsource` / `submit` / `audit` / `cancel`。

**权限码与接口不是一一对应。** `device:view` 同时覆盖列表和详情——它们是同一件事的两种粒度。不要为凑数把权限拆得比接口还细，粒度过细会让角色配置变成噩梦。

---

## 三、接口清单

### 3.1 认证（4 条）

认证类接口**没有权限码**——请求发起时用户尚无身份，谈不上授权。

| 编号 | 方法 + 路径 | 说明 | 权限码 | 数据范围 | 需求 |
|---|---|---|---|---|---|
| API-1-01 | `POST /auth/login` | 账号密码登录，返回访问令牌与刷新令牌 | 无 | — | FR-1-01 |
| API-1-02 | `POST /auth/refresh` | 用刷新令牌换取新令牌对，旧刷新令牌立即失效 | 无 | — | FR-1-03 |
| API-1-03 | `POST /auth/logout` | 注销，撤销当前令牌 | 需登录 | 本人 | FR-1-04 |
| API-1-04 | `GET /auth/me` | 当前用户信息与权限码集合 | 需登录 | 本人 | FR-2-06 |

> `/auth/me` 返回的权限集**仅供前端渲染，不构成授权依据**（需求 R4.3）。服务端每个接口必须独立校验。

### 3.2 部门管理（5 条）

| 编号 | 方法 + 路径 | 说明 | 权限码 | 数据范围 | 需求 |
|---|---|---|---|---|---|
| API-2-01 | `GET /depts` | 部门列表 | `system:dept:view` | 全部 | FR-2-01 |
| API-2-02 | `GET /depts/{id}` | 部门详情 | `system:dept:view` | 全部 | FR-2-01 |
| API-2-03 | `POST /depts` | 创建部门 | `system:dept:manage` | — | FR-2-01 |
| API-2-04 | `PUT /depts/{id}` | 修改部门 | `system:dept:manage` | — | FR-2-01 |
| API-2-05 | `DELETE /depts/{id}` | 删除部门 | `system:dept:manage` | — | FR-2-01 |

> 删除时若该部门下存在用户或设备，返回 409（FR-2-01）。

### 3.3 账户管理（5 条）

| 编号 | 方法 + 路径 | 说明 | 权限码 | 数据范围 | 需求 |
|---|---|---|---|---|---|
| API-2-06 | `GET /users` | 账户分页列表 | `system:user:view` | 全部 | FR-2-02 |
| API-2-07 | `GET /users/{id}` | 账户详情 | `system:user:view` | 全部 | FR-2-02 |
| API-2-08 | `POST /users` | 创建账户 | `system:user:manage` | — | FR-2-02 |
| API-2-09 | `PUT /users/{id}` | 修改账户 | `system:user:manage` | — | FR-2-02 |
| API-2-10 | `POST /users/{id}/enable` | 启用账户 | `system:user:manage` | — | FR-1-06 |
| API-2-11 | `POST /users/{id}/disable` | 停用账户，同时撤销该用户已签发的全部令牌 | `system:user:manage` | — | FR-1-06 |

> 账户**不提供删除接口**——用户被历史工单与审计日志引用，只能停用不能删除。这是设计决定，见 ADR-008。

### 3.4 角色与权限（6 条）

| 编号 | 方法 + 路径 | 说明 | 权限码 | 数据范围 | 需求 |
|---|---|---|---|---|---|
| API-2-11 | `GET /roles` | 角色列表 | `system:role:view` | 全部 | FR-2-03 |
| API-2-12 | `POST /roles` | 创建角色 | `system:role:manage` | — | FR-2-03 |
| API-2-13 | `PUT /roles/{id}` | 修改角色 | `system:role:manage` | — | FR-2-03 |
| API-2-14 | `DELETE /roles/{id}` | 删除角色 | `system:role:manage` | — | FR-2-03 |
| API-2-15 | `PUT /roles/{id}/permissions` | 为角色分配权限（全量覆盖） | `system:role:manage` | — | FR-2-04 |
| API-2-16 | `PUT /users/{id}/roles` | 为用户分配角色（全量覆盖，支持多角色） | `system:role:manage` | — | FR-2-05 |
| API-2-17 | `GET /permissions` | 全部权限码列表，供配置角色时选择 | `system:role:view` | 全部 | FR-2-04 |

> API-2-15 与 API-2-16 采用**全量覆盖**语义：先删旧关系再批量插新的，整个过程包在 Service 层 `@Transactional` 内。
> 内置角色（`is_built_in = 1`）不允许删除，返回 409。

### 3.5 设备台账（5 条）

| 编号 | 方法 + 路径 | 说明 | 权限码 | 数据范围 | 需求 |
|---|---|---|---|---|---|
| API-3-01 | `POST /devices` | 创建设备 | `device:create` | 仅本部门 | FR-3-01 |
| API-3-02 | `PUT /devices/{id}` | 修改设备（`code` 不可改） | `device:update` | 仅本部门 | FR-3-02 |
| API-3-03 | `DELETE /devices/{id}` | 删除设备 | `device:delete` | 仅本部门 | FR-3-03 |
| API-3-04 | `GET /devices/{id}` | 设备详情 | `device:view` | 见下 | FR-3-04 |
| API-3-05 | `GET /devices` | 分页组合检索（名称模糊 / 编号 / 状态 / 部门 / 责任人） | `device:view` | 见下 | FR-3-05, FR-3-06 |

**`device:view` 的数据范围因角色而异**：

| 角色 | 可见范围 |
|---|---|
| 普通员工、部门主管 | 本部门设备 |
| 维修工程师、维保主管、系统管理员 | 全部设备 |

> API-3-04 是**防按 ID 越权**的重点测试对象（需求 R4.6）：范围外的 ID 必须返回 403 或 404，不得返回数据。

### 3.6 设备附件（3 条）

| 编号 | 方法 + 路径 | 说明 | 权限码 | 数据范围 | 需求 |
|---|---|---|---|---|---|
| API-3-06 | `POST /devices/{id}/files` | 上传附件 | `device:update` | 仅本部门 | FR-3-07 ~ FR-3-09 |
| API-3-07 | `GET /devices/{id}/files` | 附件列表 | `device:view` | 同设备可见范围 | FR-3-07 |
| API-3-08 | `GET /files/{id}/download` | 下载附件 | `device:view` | **同设备可见范围** | FR-3-10 |

> API-3-08 必须在**返回文件流之前**校验对所属设备的数据访问权限。跨部门下载是需求 9.3 攻击清单中的一条。

### 3.7 工单（10 条）

**查询类**

| 编号 | 方法 + 路径 | 说明 | 权限码 | 数据范围 | 需求 |
|---|---|---|---|---|---|
| API-4-01 | `POST /orders` | 创建报修工单 | `order:create` | 仅可对可见设备报修 | FR-4-01, FR-4-02 |
| API-4-02 | `GET /orders` | 工单分页列表 | `order:view` | 见下 | FR-4-09 |
| API-4-03 | `GET /orders/{id}` | 工单详情 | `order:view` | 见下 | FR-4-09 |
| API-4-04 | `GET /orders/{id}/records` | 工单流转记录 | `order:view` | 同工单可见范围 | R5.4 |

**`order:view` 的数据范围**：

| 角色 | 可见范围 |
|---|---|
| 普通员工 | 自己发起的工单 |
| 部门主管 | 本部门发起的全部工单 |
| 维修工程师 | 待受理工单 + 自己承接的工单 |
| 维保主管 | 全部工单 |

**流转类**——6 个接口覆盖状态机的 10 条边：

| 编号 | 方法 + 路径 | 覆盖的流转边 | 权限码 | 需求 |
|---|---|---|---|---|
| API-4-05 | `POST /orders/{id}/accept` | 待受理 → 已接单 | `order:accept` | FR-4-03, FR-4-04 |
| API-4-06 | `POST /orders/{id}/start` | 已接单 → 维修中 | `order:repair` | FR-4-05 |
| API-4-07 | `POST /orders/{id}/outsource` | 维修中 → 委外中 | `order:outsource` | FR-4-06 |
| API-4-08 | `POST /orders/{id}/submit` | 维修中 → 待验收<br>委外中 → 待验收 | `order:submit` | FR-4-07 |
| API-4-09 | `POST /orders/{id}/audit` | 待验收 → 已完成（通过）<br>待验收 → 维修中（退回） | `order:audit` | FR-4-08 |
| API-4-10 | `POST /orders/{id}/cancel` | 待受理 → 已关闭<br>已接单 → 已关闭<br>委外中 → 已关闭 | `order:cancel` | 5.3 |

> **一个接口可覆盖多条边**，由当前状态决定走哪一条。API-4-09 的通过 / 退回由请求体字段区分。
> API-4-05 是并发抢单的战场（FR-4-04）：N 人同时调用，必须有且仅有一人成功。

---

## 四、三层授权校验

工单流转接口的授权判定**必须三层俱全**，缺一层就是漏洞：

```
1. 权限码校验     用户有没有 order:audit ?        → 没有，403
2. 状态合法性校验  当前状态允许这条转移吗 ?         → 不允许，409
3. 身份关系校验    该用户和这张工单是什么关系 ?      → 关系不对，403
```

第三层最容易遗漏。举例：张三有 `order:audit` 权限，但他不能验收李四发起的工单——验收只能由报修方本人或其部门主管执行。同理，`order:cancel` 挂在三条边上，若不校验身份关系，报修人就能关闭一张与自己无关的「委外中」工单。

第三层是需求 R4.6「按主键访问也须校验数据范围」在状态机上的体现，也是 Day 16 实现 R5.5 时的核心。

---

## 五、permission 表初始数据

由上表权限码去重得出，共 18 条。写入 Flyway `V2__init_data.sql`。

| code | name | resource |
|---|---|---|
| `device:view` | 查看设备 | device |
| `device:create` | 新增设备 | device |
| `device:update` | 修改设备 | device |
| `device:delete` | 删除设备 | device |
| `order:view` | 查看工单 | order |
| `order:create` | 发起报修 | order |
| `order:accept` | 接单 | order |
| `order:repair` | 开始维修 | order |
| `order:outsource` | 委外处理 | order |
| `order:submit` | 提交验收 | order |
| `order:audit` | 验收工单 | order |
| `order:cancel` | 撤销 / 关闭工单 | order |
| `system:dept:view` | 查看部门 | system |
| `system:dept:manage` | 管理部门 | system |
| `system:user:view` | 查看账户 | system |
| `system:user:manage` | 管理账户 | system |
| `system:role:view` | 查看角色 | system |
| `system:role:manage` | 管理角色 | system |

---

## 六、内置角色权限矩阵

五个内置角色（`is_built_in = 1`）及其权限分配，构成 `role_permission` 初始数据。

| 权限码 | 普通员工<br>`EMPLOYEE` | 部门主管<br>`DEPT_MANAGER` | 维修工程师<br>`ENGINEER` | 维保主管<br>`MAINT_MANAGER` | 系统管理员<br>`ADMIN` |
|---|:---:|:---:|:---:|:---:|:---:|
| `device:view` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `device:create` | | ✓ | | ✓ | |
| `device:update` | | ✓ | | ✓ | |
| `device:delete` | | ✓ | | ✓ | |
| `order:view` | ✓ | ✓ | ✓ | ✓ | |
| `order:create` | ✓ | ✓ | | | |
| `order:accept` | | | ✓ | ✓ | |
| `order:repair` | | | ✓ | ✓ | |
| `order:outsource` | | | | ✓ | |
| `order:submit` | | | ✓ | ✓ | |
| `order:audit` | ✓ | ✓ | | | |
| `order:cancel` | ✓ | ✓ | ✓ | ✓ | |
| `system:dept:view` | | ✓ | | | ✓ |
| `system:dept:manage` | | | | | ✓ |
| `system:user:view` | | ✓ | | | ✓ |
| `system:user:manage` | | | | | ✓ |
| `system:role:view` | | | | | ✓ |
| `system:role:manage` | | | | | ✓ |

**这张矩阵里有四个决定，需要能解释：**

1. **系统管理员不参与业务流转。** 他没有任何 `order:*` 权限。管理员的职责是管账号和权限，不是修设备——这是最小权限原则的直接应用，也符合需求 4.1 对该角色的定义。

2. **`order:audit` 给的是报修方，不是维修方。** 验收是「报修人确认修好了」，自己修自己验就失去了验收的意义。所以员工和部门主管有，工程师和维保主管没有。

3. **`order:outsource` 只给维保主管。** 需求 FR-4-06 要求委外需其批准，而我们决定不建模「申请—批准」两步流程（见需求文档第 10 节已知限制），因此转移的执行者就是批准人，工程师线下发起。

4. **`order:cancel` 四个角色都有，靠第三层身份校验区分。** 员工撤自己的单、主管判定误报、工程师同意撤单、维保主管处理厂商无法维修——同一权限码，四种场景，靠身份关系与当前状态区分。这正是三层校验中第三层存在的理由。
