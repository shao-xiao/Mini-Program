# 资产管理模块重组清单

## 目标结构

```text
资产管理
├── 楼宇空间管理  /assets/spaces
├── 设备台账      /assets/equipment
├── 能耗管理      /assets/energy
├── 停车位管理    /assets/parking
└── 会议管理      /assets/meetings
```

## 路由与页面合并

| 旧菜单或路由 | 新页面 | 处理方式 | 旧路由兼容 |
| --- | --- | --- | --- |
| `/buildings` | 楼宇空间管理 | 楼宇栏 | 重定向到 `/assets/spaces` |
| `/floors` | 楼宇空间管理 | 楼层栏 | 重定向到 `/assets/spaces` |
| `/rooms` | 楼宇空间管理 | 房间 DataWorkspace | 重定向到 `/assets/spaces` |
| `/equipment` | 设备台账 | 复用现有页面 | 重定向到 `/assets/equipment` |
| `/energy/meters` | 能耗管理 | 表具页签 | 重定向到 `/assets/energy?tab=meters` |
| `/energy/records` | 能耗管理 | 抄表记录页签 | 重定向到 `/assets/energy?tab=records` |
| `/energy/stats` | 能耗管理 | 用量统计页签 | 重定向到 `/assets/energy?tab=statistics` |
| `/parking/spaces` | 停车位管理 | 车位台账 | 重定向到 `/assets/parking` |
| `/parking/bills` | 财务账单 | 仅保留历史查询 | 重定向到 `/bills?billType=PARKING` |
| `/meetings/rooms` | 会议管理 | 会议室卡片 | 重定向到 `/assets/meetings?tab=rooms` |
| `/meetings/bookings` | 会议管理 | 预约 DataWorkspace | 重定向到 `/assets/meetings?tab=bookings` |

## 职责边界

- 组合页面负责选中上下文、URL 状态、局部加载状态和跨区域刷新。
- 现有业务页面继续负责 DataWorkspace、WorkspaceDataGrid、视图、自定义字段和业务弹窗。
- 真实 CRUD、导入导出和业务 mutation 继续调用现有 API。
- 资产页面不调用能耗、停车或会议的账单生成接口。
- 财务账单 `/bills` 是统一出账入口；历史业务账单数据和后端兼容接口不删除。
- 菜单迁移只调整 `sys_menu` 与 `sys_role_menu`，不改变 `sys_role_permission` 和 permission code。

## 权限组合

| 页面 | 查看权限 |
| --- | --- |
| 楼宇空间管理 | `building:view`、`floor:view`、`room:view` 任一 |
| 设备台账 | `asset:view` |
| 能耗管理 | `energy:meter:view`、`energy:view` 任一 |
| 停车位管理 | `parking:view` |
| 会议管理 | `meeting-room:view`、`meeting-booking:view` 任一 |

页面内部仍按原 permission code 分别控制每个区域和操作。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
