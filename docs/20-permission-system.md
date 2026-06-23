# Permission System

Date: 2026-06-23
Status: current code-aligned reference

本文档梳理 SkillHub 的双层角色权限体系与 API Token 机制，以当前代码实现为准。

## 1. 设计原则

- 平台角色（全局）与命名空间角色（局部）独立管理，互不覆盖
- `SUPER_ADMIN` 是系统万能钥匙，bypass 一切权限检查
- 权限三层实施：路由策略 → 方法级注解 → 服务层编程式校验
- API Token 是用户级凭证，独立作用域体系，与 RBAC 角色解耦
- 前端与后端保持同一权限源；服务端计算 `can*` 布尔字段，前端仅消费

## 2. 角色总览

| 维度 | 角色 | 定义位置 |
|------|------|---------|
| 平台角色 | `SUPER_ADMIN` · `SKILL_ADMIN` · `USER_ADMIN` · `AUDITOR` · `USER` | DB `role` 表 + `PlatformRoleDefaults` |
| 命名空间角色 | `OWNER` · `ADMIN` · `MEMBER` | `NamespaceRole.java` 枚举 |

`USER` 是隐式默认角色（`PlatformRoleDefaults.DEFAULT_USER_ROLE`），无 DB 记录，由代码层在用户无任何角色绑定时自动注入。

## 3. 平台角色权限矩阵

### 3.1 SUPER_ADMIN（超级管理员）

万能钥匙：所有管理页面可见，所有管理操作可执行。

- 技能隐藏/取消隐藏、硬删除、撤回（yank）
- 标签定义 CRUD，特权（PRIVILEGED）标签管理
- 搜索索引重建、AdminSearch
- 用户管理（含 SUPER_ADMIN 角色分配——此为唯一可执行入口）
- 用户资料审核、审计日志、举报处理、晋升审核
- Prometheus metrics 访问
- 看到隐藏（HIDDEN）技能，“我的技能”多出 HIDDEN 筛选
- `LabelPermissionChecker.canManageDefinitions()` 唯一通过者

### 3.2 SKILL_ADMIN（技能管理员）

技能治理专职角色。

- 治理中心、活动流可见
- 技能审核（批准/拒绝）、技能撤回（yank）
- 举报查看与驳回（“解决并隐藏”需 SUPER_ADMIN）
- 晋升审核、创建命名空间、命名空间治理、安全审计摘要

❌ 不能：隐藏/取消隐藏、硬删除、标签管理、用户管理、审计日志、搜索重建

### 3.3 USER_ADMIN（用户管理员）

- 用户列表、角色分配、启用/停用/批准、密码重置
- 用户资料审核（批准/拒绝）
- 全局审核中心访问

⚠️ 限制：`AdminUserAppService` 硬编码禁止 USER_ADMIN 将 SUPER_ADMIN 角色分配给他人。

### 3.4 AUDITOR（审计员）

只读角色。

- 查看审计日志
- Prometheus metrics 访问
- 治理活动流可见

### 3.5 USER（默认用户，隐式）

- 发布、浏览、下载技能
- 社交互动（收藏/评分/订阅）
- 举报技能、API Token 管理、个人资料管理

## 4. 命名空间角色权限矩阵

### 4.1 OWNER

继承 ADMIN 全部权限，并独占：

- 转让所有权（原 OWNER 自动降级为 ADMIN）
- 命名空间归档、恢复、删除
- 添加新的 OWNER

### 4.2 ADMIN

- 成员管理（添加/移除/变更角色，不含 OWNER 指派）
- 冻结 / 解冻技能
- 命名空间内技能审核
- 标签管理、编辑命名空间
- 技能生命周期管理（归档、删除版本、撤回审核、重新发布）
- 提交晋升申请

### 4.3 MEMBER

- 提交审核申请
- 查看命名空间技能

成员页面对 MEMBER、全局命名空间、非 ACTIVE 状态显示只读提示。

### 4.4 层级关系

`OWNER > ADMIN > MEMBER`，上层完整覆盖下层。

## 5. 权限实施层级

```
REQUEST
   │
   ├─ Layer 1  RouteSecurityPolicyRegistry.routePolicies()
   │           URL + Method → permitAll / authenticated / role-protected
   │
   ├─ Layer 2  @PreAuthorize on controller methods
   │           Method-level role check (e.g. "hasRole('SUPER_ADMIN')")
   │
   ├─ Layer 3  Service-layer programmatic check
   │           RbacService.hasRole() / hasPermission()
   │           (GovernanceWorkbench, Review, Promotion, Label, Visibility)
   │
   └─ Layer 4  Domain-layer permission checker
               NamespaceAccessPolicy, ReviewPermissionChecker,
               LabelPermissionChecker, VisibilityChecker
```

默认兜底：`anyRequest().authenticated()`，未列入 permitAll 的路径均需认证。

## 6. 前端 UI 可见性

### 6.1 用户菜单导航

| 菜单项 | 可见条件 |
|--------|---------|
| 治理中心 | SKILL_ADMIN / NAMESPACE_ADMIN / SUPER_ADMIN |
| 审核中心 | SKILL_ADMIN / USER_ADMIN / SUPER_ADMIN，或管理命名空间的 OWNER/ADMIN |
| 晋升 / 举报 | SKILL_ADMIN / SUPER_ADMIN |
| 用户管理 | USER_ADMIN / SUPER_ADMIN |
| 标签管理 | SUPER_ADMIN |
| 审计日志 | AUDITOR / SUPER_ADMIN |

### 6.2 路由级保护（RoleGuard）

| 路由 | 所需角色 |
|------|---------|
| `/dashboard/reports` | SKILL_ADMIN / SUPER_ADMIN |
| `/dashboard/promotions` | SKILL_ADMIN / SUPER_ADMIN |
| `/admin/users` | USER_ADMIN / SUPER_ADMIN |
| `/admin/audit-log` | AUDITOR / SUPER_ADMIN |
| `/admin/labels` | SUPER_ADMIN |

### 6.3 技能详情页关键操作

| 操作 | 条件 |
|------|------|
| 治理面板（隐藏/撤回） | SKILL_ADMIN / SUPER_ADMIN |
| 隐藏按钮 | SUPER_ADMIN |
| 硬删除 | 技能所有者 或 SUPER_ADMIN |
| 归档/版本删除/撤回审核 | `canManageLifecycle`（OWNER / ADMIN） |
| 特权标签 / 标签定义 | SUPER_ADMIN |
| 晋升到全局 | `canSubmitPromotion`（OWNER）+ 已发布版本 |

### 6.4 其他

- 创建命名空间按钮：SKILL_ADMIN / SUPER_ADMIN
- 命名空间成员管理：OWNER / ADMIN；转让所有权仅 OWNER
- “我的技能”筛选：SUPER_ADMIN 可见 HIDDEN；普通用户不可见
- 审核页 Tab：SKILL_ADMIN 显示“技能审核”，USER_ADMIN 显示“资料审核”

## 7. API Token 系统

### 7.1 定位

API Token 是用户的可编程身份凭证，用于 CLI 认证、CI/CD 自动化、第三方 API 调用。

- 绑定用户，继承用户的 RBAC 角色
- 格式：`sk_` + 32 字节随机 Base64（如 `sk_aB3dEfGh...`）
- 仅创建时显示一次原始值，服务端仅存 SHA-256 哈希与前缀（前 8 字符）
- 支持过期时间（7天/30天/90天/自定义/永不过期）与撤销
- 账号合并时自动迁移 Token 到主账号

### 7.2 作用域

| 作用域 | 覆盖操作 |
|--------|---------|
| `skill:read`（隐式） | 所有 GET 技能/命名空间/搜索/下载端点 |
| `skill:publish` | POST 发布、校验、上传（Web + CLI） |
| `skill:delete` | DELETE 技能（Web + CLI） |
| `token:manage` | `/api/v1/tokens/**` Token 管理自身 |

默认作用域：`["skill:read", "skill:publish"]`。Device Flow 也默认此组。

### 7.3 认证链路

```
Authorization: Bearer sk_xxx
   │
   ├─ ApiTokenAuthenticationFilter  → SHA-256(raw) 查哈希
   │                                  → 验证未撤销未过期
   │                                  → 校验 user.isActive()
   │                                  → 加载用户 RBAC 角色 + Token 作用域
   │                                  → 设置 Spring Security principal
   │                                  → touchLastUsed()
   │
   ├─ ApiTokenScopeFilter           → RouteSecurityPolicyRegistry.authorizeApiToken()
   │                                  → 校验路径所需 scope ⊆ Token scopes
   │
   └─ CSRF bypass                   → Bearer Token 请求跳过 CSRF
```

### 7.4 CLI / Device Auth Flow

OAuth Device Flow 用于 CLI 工具获取 Token：

1. CLI 请求 device code → 服务端在 Redis 生成 `{device_code, user_code}`
2. CLI 提示用户浏览器访问 URL 并输入 user_code
3. 用户在浏览器完成授权
4. CLI 持续轮询 token 端点，成功后服务端用 `rotateToken()` 创建/轮换名为 `"CLI Device Flow"` 的 Token
5. CLI 拿到原始 Token 写入本地配置

用法：`skillhub login --token sk_xxx --registry https://...`

### 7.5 前端管理

- 路径：`/dashboard/tokens`
- 创建：输入名称 + 过期策略，创建后一次性显示原始 Token 并附复制按钮
- 列表：分页展示，可撤销、可修改过期时间
- 同用户下同名 active token 由唯一索引 `uk_api_token_user_active_name` 保证

## 8. 关键文件索引

| 文件 | 作用 |
|------|------|
| `db/migration/V1__init_schema.sql` | DB 种子：角色、权限、绑定 |
| `auth/rbac/RbacService.java` | 中央 RBAC 服务：角色/权限查询 |
| `auth/rbac/PlatformRoleDefaults.java` | USER 默认角色注入 |
| `auth/policy/RouteSecurityPolicyRegistry.java` | 路由级授权策略目录 |
| `domain/namespace/NamespaceRole.java` | 命名空间角色枚举 |
| `domain/namespace/NamespaceAccessPolicy.java` | 命名空间生命周期权限规则 |
| `domain/review/ReviewPermissionChecker.java` | 审核/晋升权限检查 |
| `domain/label/LabelPermissionChecker.java` | 标签权限检查 |
| `domain/skill/VisibilityChecker.java` | 技能可见性检查 |
| `controller/admin/*.java` | 各管理端点 `@PreAuthorize` |
| `auth/token/ApiTokenService.java` | Token 生命周期 |
| `auth/token/ApiTokenAuthenticationFilter.java` | Bearer Token 认证过滤器 |
| `auth/token/ApiTokenScopeFilter.java` | Token 作用域校验过滤器 |
| `auth/device/DeviceAuthService.java` | OAuth Device Flow |
| `web/src/features/auth/use-auth.ts` | 前端 `hasRole()` hook |
| `web/src/shared/components/role-guard.tsx` | 前端路由守卫组件 |
| `web/src/shared/lib/governance-access.ts` | 前端治理入口判断 |
| `web/src/features/token/` | 前端 Token 管理 UI |
