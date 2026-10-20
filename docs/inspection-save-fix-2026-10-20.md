# 巡检编辑保存报错修复（2026-10-20）

## 根因

在巡检127编辑页保存备注，返回400“Invalid attachment file id”。后端同步原照片时对 inspection_attachment.deleted 调用 parseLong；生产 MySQL 驱动把 TINYINT(1) 返回为 Boolean，false/true 无法解析成长整型，整个事务回滚。文件ID本身116有效，原附件关系84未损坏。

前端另有回填遗漏：syncDialogFromInspection 只复制固定业务字段，schema表单引用的 images、id、createdAt 未进入 form，导致原照片、巡检编号与创建时间在编辑页为空。

## 修复

- 后端只为删除标记分支兼容 Boolean，数字/数字字符串沿用原解析；文件ID仍严格校验，不接受非法ID。原活动附件只更新排序，原删除附件按既有逻辑恢复，不重复新增。
- 前端新增 inspectionEditValues，将完整详情交给schema表单，images缺省时从attachments回填，保留自定义字段及只读元数据；显式空照片数组仍表示用户主动清空。
- resetForm清理完整旧记录键，避免下次新增/编辑继承前一条巡检的照片或系统元数据。
- 未修改授权、关闭逻辑、业务字段规则、照片文件或批量数据。

## 验证

- InspectionControllerTest 10项，InspectionFileScopeTest 6项、RbacUpgradePreservationPolicyTest 4项、BusinessDataUpgradePreservationPolicyTest 6项，共26项通过。测试覆盖false/true/0/1/数字字符串删除标记、复用或恢复原照片、不重复插入和非法文件ID仍拒绝。
- 前端inspectionEditValues.test.js 2项通过，覆盖备注编辑保留fileId116、巡检编号和创建时间、自定义字段，以及显式空照片与新表单隔离。
- Maven test隐含完整compile通过；管理后台生产build与git diff --check通过。
- 真实浏览器保存用户原弹窗中待提交的备注“B1食堂燃气阀已关闭，已解决”，显示“巡检记录更新成功”；重新打开显示编号127、原创建时间、原照片和已保存备注，无浏览器error/warn。
- 只读数据库核验：id127备注已更新，inspection_status仍OPEN；attachment id84/file_id116/deleted0保留，无重复关系。后端8080已恢复，未登录接口返回401，systemd active。

## 发布与回滚

备份：/opt/dehui/backups/inspection-save-20261020-152525，包含app.jar、index.html、原前端SHA256清单和变更class清单。

生产基线与当前源码在同一JDK21、-g -parameters选项下反汇编完全一致。仅替换app.jar中InspectionController.class，其他jar条目逐项验证不变；重启dehui-backend。前端从生产代码基线构建隔离的巡检模块，增加InspectionList-save-fix-20261020-v1.js/.css并修改index.html importmap，其他93个前端文件SHA256不变，保留原照片权限、驾驶舱、能耗及日报改动。

回滚恢复备份app.jar并重启后端，恢复index.html。浏览器验收入口 /inspections?ui=inspection-save-20261020。
