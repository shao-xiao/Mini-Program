# RBAC 升级数据保留规则

## 目标

系统升级不得覆盖管理员已经配置的角色权限，也不得清空员工已经绑定的角色。

以下三张关系表属于管理员业务配置，不是可重复生成的种子数据：

- `sys_user_role`
- `sys_role_menu`
- `sys_role_permission`

## 升级规则

从 Flyway V61 开始，迁移必须遵守：

1. 禁止对三张关系表执行 `DELETE`、`TRUNCATE`、`UPDATE`、`REPLACE` 或 `DROP TABLE`。
2. 禁止通过默认角色模板重建或覆盖现有关系。
3. 新模块需要兼容已有角色时，只允许使用 `INSERT IGNORE` 增量增加关系，并以 `NOT EXISTS` 检查该角色与目标菜单/权限是否存在任何历史关系；已存在、已停用或已删除的关系保持原状。
4. 模块下线时停用菜单或权限目录项，不修改角色已经保存的关系记录。
5. 用户主动在后台保存角色或权限不属于系统升级；该操作继续由事务接口完成，并以用户本次提交为准。
6. 生产升级前仍需备份数据库，并保留 Flyway 执行记录。

## 自动保护

`RbacUpgradePreservationPolicyTest` 会扫描 V61 及后续迁移：

- 发现对三张关系表的破坏性语句时测试失败；
- 普通 `INSERT` 或 `ON DUPLICATE KEY UPDATE` 可能覆盖历史状态，因此同样失败；
- 只有同时使用 `INSERT IGNORE` 和 `NOT EXISTS` 历史关系检查的兼容性增量授权可以通过。

`spring.sql.init.mode=never` 明确禁止应用启动时执行 `data.sql` 或 `schema.sql`，数据库结构升级仅由 Flyway 管理。

本地升级前后数据保留验证使用：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File dehui-property-management\scripts\verify_rbac_upgrade_preservation.ps1
```

脚本会创建唯一隔离员工、角色、菜单和权限关系，对三张关系表计算行数与内容摘要，重新打包并启动后端后再次计算并比较，最后只清理带有专用测试标识的数据。
