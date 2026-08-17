# 租户企业 Logo 配置与移动端展示

## 目标

- 后台在“租户档案 -> 编辑租户”中为已存在租户上传、替换或清除企业 Logo。
- 租户小程序“我的身份”只显示当前登录租户配置的企业 Logo。
- 租户未配置 Logo、文件不可用或下载失败时保持空白，不回退德汇平台 Logo。
- STAFF 与 PUBLIC 的既有平台标识保持不变。

## 数据与文件模型

- Flyway `V61__add_tenant_company_logo.sql` 为 `tenant` 增加可空
  `logo_file_id` 和普通索引，不回填历史租户。
- 后台上传复用 `POST /api/files/upload`，固定提交：
  `fieldType=IMAGE`、`workspaceKey=tenants`。
- 上传草稿必须是当前后台操作者所有的
  `CUSTOM_FIELD_IMAGE + INTERNAL + tenants` 文件。
- 保存后文件原子绑定为
  `TENANT_LOGO + biz_id=tenant.id + workspace_key=tenants`。
- 替换或清除时旧文件标记为 `DISABLED`，不物理删除历史业务文件。

## 管理后台接口

`PUT /api/tenants/{tenantId}/logo`

请求：

```json
{
  "logoFileId": 123
}
```

清除：

```json
{
  "logoFileId": null
}
```

接口由既有 `tenant:update` 控制。租户主档仍走原有保存接口；Logo 使用独立
PUT，避免把编辑动作误映射到 `tenant:create`。新建租户需先保存主档，再配置
Logo。

租户档案 overview 返回：

- `logoFileId`
- `logoFileName`
- `logoUrl`，后台受保护地址 `/api/files/{fileId}`

## 小程序数据与权限

- `/api/mobile/me` 仅在当前身份为 TENANT 时通过 `tenantInfo` 返回
  `logoFileName` 和 `logoUrl`。
- 有效地址为 `/api/mobile/files/{fileId}`；未配置时两个字段均为空字符串。
- 小程序使用统一下载封装携带当前 token，下载到临时路径后交给 `<image>`，
  不把受保护 URL 直接绑定为图片地址，也不把 Logo 写入永久用户目录。
- 下载服务同时核对当前服务端租户身份、`file_upload.biz_id` 和
  `tenant.logo_file_id`。异租户、PUBLIC、STAFF 或已清除的旧文件均不可读取。
- 页面刷新、隐藏或身份切换会使旧下载响应失效；Logo 下载失败不影响身份卡
  其他真实信息。
- TENANT 身份卡中的企业 Logo 使用 `96rpx × 96rpx` 小圆角方形容器，圆角沿用
  设计系统最小规格 `--dhic-radius-xs`；未配置时同样保留方形空位。
  STAFF/PUBLIC 的平台标识继续使用原有圆形样式。

## 本地验证

- Flyway 已将开发库升级到 V61。
- 后台浏览器完成上传、保存、刷新鉴权预览和清除，相关请求均为 200，
  Console error/warn 为 0。
- 数据库确认绑定期间文件为
  `TENANT_LOGO/INTERNAL/tenants/biz_id=11`；清除后租户引用为空且旧文件不可下载。
- Mock 移动身份验证：本租户 `/mobile/me` 与 Logo 下载为 200，异租户不可读取，
  PUBLIC 不可读取；清除后 `/mobile/me.tenantInfo.logoUrl` 为空。
- 方形样式回归后小程序全量测试 `176/176` 通过，微信开发者工具 preview 成功。
- 隔离移动用户、绑定、Redis token 与测试上传文件已精确清理。
