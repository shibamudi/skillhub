# Label System

Date: 2026-06-30
Status: current code-aligned reference

本文档梳理 SkillHub 的标签（Label）系统，以当前代码实现为准。原始设计文档见 `2026-03-20-skill-label-system-design.md`。

## 1. 设计原则

- Label 挂在 skill 级别（与版本无关），提供分类和标记能力
- 两种标签类型：系统推荐（RECOMMENDED）与特权（PRIVILEGED），权限策略不同
- 支持多语言展示，display name 按请求 locale 解析，fallback 到 en → slug
- 标签变更自动触发搜索索引异步重建（批量 50）
- 独立于 `skill_tag`（版本分发通道），不复用表、Service、Controller、API 路径

## 2. 数据模型

### 2.1 表结构（V34 迁移）

```
label_definition (标签定义)
  ├── id            BIGSERIAL PK
  ├── slug          VARCHAR(64) UNIQUE NOT NULL    -- 英文标识，如 code-generation
  ├── type          VARCHAR(16) NOT NULL            -- RECOMMENDED | PRIVILEGED
  ├── visible_in_filter BOOLEAN NOT NULL DEFAULT true
  ├── sort_order    INTEGER NOT NULL DEFAULT 0
  ├── created_by    VARCHAR(128) FK user_account
  ├── created_at    TIMESTAMPTZ
  └── updated_at    TIMESTAMPTZ

label_translation (翻译，1:N， CASCADE 删除)
  ├── id            BIGSERIAL PK
  ├── label_id      BIGINT FK label_definition ON DELETE CASCADE
  ├── locale        VARCHAR(16) NOT NULL            -- 如 en、zh、ja
  ├── display_name  VARCHAR(128) NOT NULL
  ├── created_at    TIMESTAMPTZ
  └── updated_at    TIMESTAMPTZ
  UNIQUE(label_id, locale)

skill_label (Skill-Label 多对多连接表)
  ├── id            BIGSERIAL PK
  ├── skill_id      BIGINT FK skill ON DELETE CASCADE
  ├── label_id      BIGINT FK label_definition ON DELETE CASCADE
  ├── created_by    VARCHAR(128) FK user_account
  └── created_at    TIMESTAMPTZ
  UNIQUE(skill_id, label_id)
```

### 2.2 命名约束

- 表名前缀 `label_`，与 `skill_tag` 彻底隔离
- 实体命名：`LabelDefinition` / `SkillLabel` / `LabelTranslation`
- API 路径使用 `/labels`，不复用 `/tags`

### 2.3 可配置限制

| 配置项 | 默认值 | 作用 |
|--------|--------|------|
| `skillhub.label.max-definitions` | 100 | 标签定义总数上限 |
| `skillhub.label.max-per-skill` | 10 | 单个 skill 关联标签上限 |

## 3. 标签类型

系统仅支持两种标签类型（`LabelType` 枚举），暂无用户自定义标签：

### 3.1 RECOMMENDED（系统推荐标签）

- 用途：功能分类标签，如"代码生成"、"文档处理"、"数据分析"
- 出现在搜索页分类筛选板块（当 `visibleInFilter=true`）
- Skill 拥有者、Namespace ADMIN/OWNER、SUPER_ADMIN 均可赋予/移除
- 前端样式：**灰色 (slate)** 药丸徽章

### 3.2 PRIVILEGED（特权标签）

- 用途：平台运营/官方标记，如"官方推荐"、"官方认证"
- **仅 SUPER_ADMIN 可赋予/移除**，其他角色（含 Namespace ADMIN/OWNER）无权操作
- 前端样式：**琥珀色 (amber)** 药丸徽章，与 RECOMMENDED 视觉区分

### 3.3 未来扩展预留

设计文档中为"用户自定义标签"预留了扩展路径（新增 `USER_DEFINED` 类型 + 审核字段），但当前代码未实现。

## 4. 权限模型

### 4.1 标签定义管理（Admin CRUD）

| 操作 | 端点 | 所需角色 |
|------|------|---------|
| 列出所有标签 | `GET /api/v1/admin/labels` | SUPER_ADMIN |
| 创建标签 | `POST /api/v1/admin/labels` | SUPER_ADMIN |
| 更新标签 | `PUT /api/v1/admin/labels/{slug}` | SUPER_ADMIN |
| 删除标签 | `DELETE /api/v1/admin/labels/{slug}` | SUPER_ADMIN |
| 批量排序 | `PUT /api/v1/admin/labels/sort-order` | SUPER_ADMIN |

仅 SUPER_ADMIN 可管理标签定义。SKILL_ADMIN、USER_ADMIN、AUDITOR 均无权限。

权限实施路径：`@PreAuthorize("hasRole('SUPER_ADMIN')")` + `LabelPermissionChecker.canManageDefinitions()` 双重校验。

### 4.2 公开查询（无需登录）

| 操作 | 端点 | 认证 |
|------|------|------|
| 获取可见筛选标签列表 | `GET /api/v1/labels` | 无需登录 |
| 获取可见筛选标签列表 | `GET /api/web/labels` | 无需登录 |
| 查看某 skill 的标签 | `GET /api/v1/skills/{ns}/{slug}/labels` | 无需登录（受 skill 可见性约束） |
| 按标签搜索 skill | `GET /api/web/skills?label=xxx` | 无需登录 |

### 4.3 赋予/移除 Skill 标签（需登录 + 域级权限）

| 操作 | 端点 | 认证 |
|------|------|------|
| 赋予标签 | `PUT /api/v1/skills/{ns}/{slug}/labels/{labelSlug}` | 需登录 + 权限校验 |
| 移除标签 | `DELETE /api/v1/skills/{ns}/{slug}/labels/{labelSlug}` | 需登录 + 权限校验 |

同一路径在 `/api/web/...` 下也有暴露。

### 4.4 权限矩阵

#### 标签定义 CRUD

| 角色 | 创建/修改/删除标签定义 |
|------|:---:|
| SUPER_ADMIN | ✅ |
| SKILL_ADMIN | ❌ |
| USER_ADMIN | ❌ |
| AUDITOR | ❌ |
| Namespace OWNER / ADMIN / MEMBER | ❌ |

#### 给 Skill 赋予/移除标签

| 角色 | RECOMMENDED 标签 | PRIVILEGED 标签 |
|------|:---:|:---:|
| SUPER_ADMIN | ✅ | ✅ |
| Skill 创建者（ownerId 匹配） | ✅ | ❌ |
| Namespace OWNER | ✅ | ❌ |
| Namespace ADMIN | ✅ | ❌ |
| Namespace MEMBER（非作者） | ❌ | ❌ |
| 匿名用户 | ❌ | ❌ |

权限实施核心：`LabelPermissionChecker.canManageSkillLabel()`。

SUPER_ADMIN 绕过 namespace membership 直接执行。

#### 跨空间权限边界

- Namespace 管理员只能管理其所管理空间内 skill 的标签
- Namespace OWNER/ADMIN 对非本空间 skill 的标签无任何操作权

### 4.5 前端 UI 权限逻辑

| 场景 | 条件 | 行为 |
|------|------|------|
| 标签管理面板可见性 | `canManage` 为 true | 显示贴/撕标签面板；否则整个面板不渲染 |
| 可用标签列表数据源 | SUPER_ADMIN | 从 admin API 获取所有标签定义（含 PRIVILEGED） |
| 可用标签列表数据源 | 非 SUPER_ADMIN 管理员 | 从公开 API 获取 `visibleInFilter=true` 的标签 |
| REMOVE 按钮显示 | `isSuperAdmin` 或标签类型非 PRIVILEGED | 显示"移除"按钮；否则显示"受限"提示文字 |

## 5. API 详解

### 5.1 管理后台 API（`/api/v1/admin/labels`）

#### 创建标签定义

`POST /api/v1/admin/labels`

```json
{
  "slug": "code-generation",
  "type": "RECOMMENDED",
  "visibleInFilter": true,
  "sortOrder": 10,
  "translations": [
    { "locale": "en", "displayName": "Code Generation" },
    { "locale": "zh", "displayName": "代码生成" }
  ]
}
```

#### 更新标签定义

`PUT /api/v1/admin/labels/{slug}`

Body 不包含 slug 字段（slug 不可修改）。`translations` 采用全量替换策略。

```json
{
  "type": "PRIVILEGED",
  "visibleInFilter": true,
  "sortOrder": 5,
  "translations": [
    { "locale": "en", "displayName": "Official" },
    { "locale": "zh", "displayName": "官方推荐" }
  ]
}
```

#### 删除标签定义

`DELETE /api/v1/admin/labels/{slug}`

硬删除，级联删除所有 translations 和 skill_label 关联。

#### 批量更新排序

`PUT /api/v1/admin/labels/sort-order`

```json
{
  "items": [
    { "slug": "code-generation", "sortOrder": 1 },
    { "slug": "official", "sortOrder": 2 }
  ]
}
```

### 5.2 Skill 标签 API

#### 获取 skill 的标签列表

`GET /api/v1/skills/{namespace}/{slug}/labels`
`GET /api/web/skills/{namespace}/{slug}/labels`

返回 `[ { slug, type, displayName } ]`，按 type → slug 排序。`displayName` 按请求 locale 解析。

SUPER_ADMIN bypass skill 可见性检查直接返回；其他用户受 `VisibilityChecker.canAccess()` 约束。

#### 赋予 / 移除标签

- `PUT /api/v1/skills/{namespace}/{slug}/labels/{labelSlug}` — 赋予
- `DELETE /api/v1/skills/{namespace}/{slug}/labels/{labelSlug}` — 移除

幂等性：赋予已存在的标签不报错，直接返回。移除不存在的标签返回 400。

### 5.3 公开查询 API

#### 可见标签列表

`GET /api/v1/labels` / `GET /api/web/labels`

返回 `visibleInFilter=true` 的标签，按 `sortOrder` 升序排列。返回 `[ { slug, type, displayName } ]`。

### 5.4 Slug 校验规则

- 模式：`^[a-z0-9]([a-z0-9-]*[a-z0-9])?$`
- 长度：1-64 字符
- 规范化：trim → lowercase → 拒绝双连号
- 唯一性：不区分大小写

### 5.5 翻译规则

- 每个标签至少需要一条翻译
- locale 规范化：trim → `_` 替换为 `-` → lowercase
- 不允许同一标签下出现重复 locale
- display name 不允许空白

## 6. 前端使用

### 6.1 搜索页筛选

搜索页从公开 API 获取 `visibleInFilter=true` 的标签，渲染为按钮列表。点击某个标签高亮并追加 `?label=<slug>` 到 URL，支持分享。单选互斥（点击另一个切换，再次点击取消）。

搜索请求透传 `label` 参数到后端 `GET /api/web/skills?label=xxx`，后端通过 JOIN `skill_label` + `label_definition` 按 slug 过滤，而非全文搜索。

### 6.2 Skill 详情页

**标题下方**：所有标签以彩色药丸徽章展示（只读，所有人可见）。PRIVILEGED = 琥珀色边框+背景，RECOMMENDED = 灰色。

**侧边栏管理面板**（`skill-label-panel.tsx`）：
- `canManage` 为 false 时不渲染
- SUPER_ADMIN 从 admin API 获取所有标签定义作为候选列表
- 其他管理员从公开 API 获取可见标签作为候选列表
- 已关联标签显示 REMOVE 按钮（PRIVILEGED 标签对非 SUPER_ADMIN 显示"受限"提示）

### 6.3 Skill 卡片（搜索结果）

当前搜索结果中的 Skill 卡片**不显示**标签，仅显示名称、摘要、命名空间、版本、下载量、收藏数、评分。

### 6.4 管理后台标签页

路由 `/admin/labels`，受 SUPER_ADMIN RoleGuard 保护。

功能：
- 列表展示所有标签定义（含类型、可见性、排序、翻译数）
- 创建/编辑对话框（slug + type + visibleInFilter + 动态翻译条目）
- 删除需二次确认
- 排序支持上移/下移按钮
- 页面顶部展示统计摘要（总数、可见数、特权数）

## 7. 搜索集成

### 7.1 搜索文档写入

搜索文档重建时，将 skill 关联的所有标签的所有语言翻译文本追加到 `keywords` 字段，与现有 keywords 来源组合。标签删除 / 翻译修改 / skill 移除标签后，旧文本通过重建被清理，不残留。

### 7.2 重建触发时机

| 事件 | 影响范围 | 处理方式 |
|------|---------|---------|
| Skill 赋予/移除标签 | 单个 skill | AFTER_COMMIT 同步重建 |
| 标签翻译被修改 | 所有关联该标签的 skill | AFTER_COMMIT 异步批量重建 |
| 标签定义被删除 | 所有关联该标签的 skill | 删除前快照 skill_id，AFTER_COMMIT 异步批量重建 |
| 标签排序更新 | 无 | 不触发搜索重建 |

### 7.3 批量重建策略

- Spring `@Async` 执行，AFTER_COMMIT 阶段触发，避免事务回滚时产生脏任务
- 批量大小 50，批次间无间隔
- 单个 skill 重建失败 try/catch + log，不影响后续
- 超大批量由人工触发 `rebuildAll` 兜底
- 搜索重建入口在 `skillhub-app` 层（`LabelSearchSyncService`），不放在 `skillhub-search` 模块，保持模块边界

### 7.4 分类筛选

搜索页的分类筛选不走全文搜索，而是通过 SQL JOIN 按 slug 过滤：

```sql
AND d.skill_id IN (
    SELECT sl.skill_id FROM skill_label sl
    JOIN label_definition ld ON ld.id = sl.label_id
    WHERE ld.slug IN (:labelSlugs)
)
```

当前为 OR 语义（匹配任一 label 即命中）。前端一期为单选。

## 8. 审计日志

以下动作记录到 `audit_log`：

| Action | targetType | 触发时机 | detail |
|--------|-----------|---------|--------|
| `LABEL_CREATE` | LABEL | 创建标签定义 | `{ "slug": "..." }` |
| `LABEL_UPDATE` | LABEL | 更新标签定义 | `{ "slug": "..." }` |
| `LABEL_DELETE` | LABEL | 删除标签定义 | `{ "slug": "..." }` |
| `LABEL_SORT_ORDER_UPDATE` | LABEL | 批量更新排序 | `{ "count": N }` |
| `SKILL_LABEL_ATTACH` | SKILL | 给 skill 赋予标签 | `{ "labelSlug": "..." }` |
| `SKILL_LABEL_DETACH` | SKILL | 从 skill 移除标签 | `{ "labelSlug": "..." }` |

## 9. 国际化

### 9.1 DisplayName 解析链

`LabelLocalizationService` 按请求 locale（`LocaleContextHolder`）解析显示名称：

完整 locale 标签 → 语言代码 → `"en"` → slug 本身

示例：请求 `zh-CN`，查找顺序：`zh-cn` → `zh` → `en` → `"code-generation"`

前端实现相同逻辑（`skill-label-panel.tsx` 中的 `resolveDisplayName`）。

### 9.2 搜索文档国际化

所有语言的翻译文本都写入搜索文档 keywords，使多语言搜索均可命中。

## 10. Promotion 后的标签生命周期

当前 promotion 会在目标全局空间创建新的 target skill，而非移动 source skill。

- Promotion 不自动复制 source skill 的标签到 target skill
- Source skill 和 target skill 各自维护独立的 `skill_label`
- Source 空间的 Namespace 管理员不会因 source skill 的管理权限而获得 target skill 的标签管理权

## 11. 关键文件索引

### 后端

| 文件 | 作用 |
|------|------|
| `db/migration/V34__skill_label_system.sql` | DDL：3 张表的建表语句 |
| `domain/label/LabelDefinition.java` | JPA 实体：标签定义 |
| `domain/label/SkillLabel.java` | JPA 实体：skill-label 关联 |
| `domain/label/LabelTranslation.java` | JPA 实体：标签翻译 |
| `domain/label/LabelType.java` | 枚举：RECOMMENDED / PRIVILEGED |
| `domain/label/LabelPermissionChecker.java` | 权限检查核心组件 |
| `domain/label/LabelSlugValidator.java` | Slug 规范化和校验 |
| `domain/label/LabelDefinitionService.java` | 标签定义 CRUD 域服务 |
| `domain/label/SkillLabelService.java` | Skill 标签赋予/移除域服务 |
| `domain/label/LabelDefinitionRepository.java` | 标签定义仓储接口 |
| `domain/label/SkillLabelRepository.java` | Skill-label 关联仓储接口 |
| `domain/label/LabelTranslationRepository.java` | 翻译仓储接口 |
| `infra/jpa/LabelDefinitionJpaRepository.java` | JPA 实现：LabelDefinitionRepository |
| `infra/jpa/SkillLabelJpaRepository.java` | JPA 实现：SkillLabelRepository |
| `infra/jpa/LabelTranslationJpaRepository.java` | JPA 实现：LabelTranslationRepository |
| `controller/admin/AdminLabelController.java` | 管理后台 CRUD（SUPER_ADMIN） |
| `controller/portal/LabelController.java` | 公开标签列表（permitAll） |
| `controller/portal/SkillLabelController.java` | Skill 标签关联 API |
| `service/LabelAdminAppService.java` | Admin 编排服务（含审计 + 搜索同步） |
| `service/SkillLabelAppService.java` | Skill 标签编排服务（含审计 + 搜索同步） |
| `service/PublicLabelAppService.java` | 公开查询编排服务 |
| `service/LabelLocalizationService.java` | DisplayName locale 解析 |
| `service/LabelSearchSyncService.java` | 搜索索引异步重建 |
| `service/LabelSearchSyncListener.java` | AFTER_COMMIT 事件监听器 |
| `search/postgres/PostgresFullTextQueryService.java` | 按 label slug 过滤搜索 SQL |
| `search/postgres/PostgresSearchRebuildService.java` | 将标签翻译写入搜索文档 keywords |

### 前端

| 文件 | 作用 |
|------|------|
| `api/client.ts` | labelApi 对象（所有标签 API 调用） |
| `api/types.ts` | LabelItem / LabelDefinition / LabelTranslation 类型 |
| `api/generated/schema.d.ts` | OpenAPI 生成的标签类型 |
| `shared/hooks/use-label-queries.ts` | TanStack Query hooks |
| `shared/hooks/query-keys.ts` | 标签 query key 工厂（含 i18n locale） |
| `features/admin/use-admin-labels.ts` | Admin 标签 mutations |
| `features/skill/skill-label-panel.tsx` | Skill 详情页标签管理面板 |
| `pages/skill-detail.tsx` | Skill 详情页（标题下方标签徽章） |
| `pages/search.tsx` | 搜索页（标签筛选按钮） |
| `pages/admin/labels.tsx` | 管理后台标签管理页 |
| `app/router.tsx` | admin/labels 路由守卫 |
