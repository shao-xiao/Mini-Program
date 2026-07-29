# 物业移动端访客预约审核闭环

## 目标

物业员工在小程序“访客预约管理”中完成待审核预约的通过或驳回，并在通过后上传门禁二维码、核验到访和登记离场。后台访客管理继续使用同一记录和同一状态机，不复制审核逻辑。

## 权限与身份

- 读取继续使用 `visitor.staff.view`。
- 通过、驳回、二维码绑定、到访和离场统一使用现有 `visitor.staff.verify`。
- 移动请求中的 `AuthPrincipal.userId` 是移动用户 ID，不能作为后台审核人。
- Controller 必须通过 `MobileApiSupport.requireStaffUserId` 取得当前绑定的 `sys_user.id`，由服务端写入审核、二维码和通行审计字段。
- 本功能不新增迁移，不修改或重建 `sys_user_role`、`sys_role_menu`、`sys_role_permission`。

## 移动端接口

- `GET /api/mobile/staff/visitors`：待审核记录优先，支持状态筛选和分页。
- `GET /api/mobile/staff/visitors/{id}`：返回审核人、审核时间、审核意见、二维码元数据和通行记录。
- `POST /api/mobile/staff/visitors/{id}/approve`：仅 `PENDING` 可执行，审核意见可选。
- `POST /api/mobile/staff/visitors/{id}/reject`：仅 `PENDING` 可执行，驳回原因必填。
- `POST /api/mobile/staff/visitors/{id}/qr-code`：仅 `REGISTERED` 可执行，接收当前员工上传的 `STAFF_DRAFT` 图片。
- `POST /api/mobile/staff/visitors/{id}/enter`：`REGISTERED -> ENTERED`。
- `POST /api/mobile/staff/visitors/{id}/leave`：`ENTERED -> LEFT`。

## 二维码文件边界

- 上传草稿为 `STAFF_DRAFT + INTERNAL + owner_sys_user_id`。
- 绑定时校验图片 MIME、文件所有人、未被使用和预约状态。
- 绑定后改为 `VISITOR + biz_id=appointmentId + workspace_key=visitors`。
- 小程序通过带登录态的 `/api/mobile/files/{id}` 下载到本地后展示；不把内部二维码改为公开文件。
- 页面以 `280rpx × 280rpx` 正方形显示，当前开发者工具 375px 视口实测为 `145px × 145px`。

## 本地自动验收

- 隔离预约 `UAT-MOBILE-APPROVE-20260729`：移动端通过、上传二维码、到访、离场成功，最终 `LEFT`。
- 隔离预约 `UAT-MOBILE-REJECT-20260729`：空原因被前端阻止，填写原因后成功驳回，最终 `REJECTED`。
- 重复通过已审核预约保持原结果，服务端状态机返回冲突而不覆盖审计字段。
- 数据库确认审核人与二维码上传人为绑定的 `sys_user.id=30`；二维码文件为 `VISITOR/INTERNAL`；通行记录包含 `ENTER,LEAVE`。
- 未登录读取二维码返回 401。
- 后台刷新后显示两条相同业务记录，详情可见审核时间、意见和“门禁二维码已上传”。
- 后台页面 Console error 为 0、失败请求为 0；访客列表和详情请求均为 200。
- Flyway 为 V60，无新增迁移。
- 后端全量测试、小程序全量测试、管理后台组件测试和生产构建必须通过。
