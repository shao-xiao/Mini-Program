# 巡检照片权限修复（2026-10-20）

## 根因与修复

`GET /api/files/{id}` 委托 MobileStaffFileService.requireDownload 校验。
历史后台上传照片保存到 inspection_attachment 后未转换文件的 CUSTOM_FIELD_IMAGE 类型。
因此文件服务只允许 owner_sys_user_id 读取，忽略了已保存巡检的查看权限。

现在仅对 SYSTEM 身份、inspections 工作区、CUSTOM_FIELD_IMAGE / CUSTOM_FIELD_ATTACHMENT
文件检查有效 inspection_attachment 与未删除 inspection_record 的关联。
关联存在时要求 inspection:view；否则保留上传者草稿访问规则。
不修改 API、登录格式、AuthInterceptor、数据库、授权配置或物理照片。

## 验证

- mvn -q -DskipTests compile：通过。
- mvn -q -Dtest=InspectionFileScopeTest,WorkOrderFileScopeTest,MobileTenantLogoFileAccessTest,RbacUpgradePreservationPolicyTest,BusinessDataUpgradePreservationPolicyTest test：27 项通过。
- npm ci --ignore-scripts 与 npm run build：通过，前端构建产物未部署。
- 工程部负责人现有线上会话：巡检 127 / INSP-9EB950863C6A 的照片缩略图与大图预览均显示正常。

## 部署与回滚

生产依赖与当前运行 class 用于验证和编译；仓库基线原始源码编译后的 javap -p -c
结果与生产原始 class 完全一致。
仅替换 /opt/dehui/backend/app.jar 中
BOOT-INF/classes/com/dehui/property/modules/mobile/service/MobileStaffFileService.class。
逐条比较 ZIP 解压字节，确认其他条目不变，ZIP 完整性通过。
重启 dehui-backend，8080 服务正常响应（无认证请求返回 401）。

备份目录：/opt/dehui/backups/inspection-photo-permission-20261020-134546。
回滚时将其中 app.jar 复制回 /opt/dehui/backend/app.jar，再 systemctl restart dehui-backend。
服务器 /tmp/inspection-fix 保存本次编译与原始字节码对比材料。
服务器旧源码目录未覆盖，以防旧版本被误当作线上源码重建。
