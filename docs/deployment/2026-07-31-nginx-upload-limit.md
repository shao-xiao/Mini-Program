# 2026-07-31 生产环境图片上传 413 修复记录

## 现象与根因

- 会议室图片 `SGY_0100.JPG` 为有效 JPEG，大小 `13,983,523` 字节，分辨率 `8256 × 5504`。
- 前端允许 JPG，Spring Boot 单文件上限为 `20 MiB`，但生产 Nginx 未配置
  `client_max_body_size`，使用默认约 `1 MiB` 限制。
- Nginx 错误日志确认请求在到达 Spring Boot 前被拒绝：
  `client intended to send too large body: 13983922 bytes`，HTTP 状态为 `413`。

## 生产修复

- 服务器：`8.133.160.149`
- 配置：`/etc/nginx/conf.d/dehui.conf`
- HTTPS `server` 块增加：`client_max_body_size 25m;`
- 备份：`/etc/nginx/conf.d/dehui.conf.bak-20260731091448`
- `nginx -t` 通过并完成平滑 reload。
- Spring Boot 仍保留单文件 `20 MiB` 业务上限，Nginx 的 `25 MiB` 仅用于容纳
  multipart 请求开销，不允许无限大小上传。

## 真实验收

- 使用原始 `SGY_0100.JPG` 调用生产
  `POST /api/files/upload`，参数为 `fieldType=IMAGE`、
  `workspaceKey=meeting-rooms`。
- HTTPS 返回 `200`，API 返回 `200`，文件大小与本地一致。
- 数据库 `file_upload`、服务器物理文件及 SHA-256
  `1c0cc66ced45e87a5dc8d44c4e5d4c81aec4c3e061a3e646df119d236bb5585e`
  均已核对。
- 验收产生的未绑定文件记录和物理文件已按精确 ID、路径清理，残留均为 `0`。
- Nginx、后端、MySQL、Redis 均为 `active + enabled`，站点 HTTPS 返回 `200`。
