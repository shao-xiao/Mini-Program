# 驾驶舱房源统计修复（2026-10-20）

## 原因

AI 日报控制器 DailyReportController 的 metrics 返回空占位房源指标。
此前驾驶舱直接依赖该数据，导致台账有房源但房间出租率及房源状态均无统计。

## 统计口径与接口

- 总房源：GET /api/rooms?page=1&pageSize=1 的 total。
- 已出租：GET /api/rooms?page=1&pageSize=1&status=RENTED 的 total。
- 未出租：总房源减已出租（所有非 RENTED 状态）。
- 出租率：已出租 / 总房源，百分比保留一位小数；台账为零时为 0%。

接口遵守原 room:view 权限，后端自动排除 deleted=1 的房源。
使用分页 total，因此超过 100 间也不会截断。不新增接口，不修改后端、台账或角色配置。
统计保存在独立 roomStats，KPI 与环形图及数量共用同一份结果；AI 日报与房源加载互不覆盖。
任一房源请求失败则不展示部分结果或伪造零值；403 显示无权限，网络失败显示刷新提示。

## 验证

- 只读数据库核对：未删除房源 39 间，RENTED=11，VACANT=28。
- 浏览器 ADMIN +1 会话：顶部房间出租率 28.2%，图表中心 28.2%，已租 11 / 未租 28 / 总数 39。
- 点击刷新，两处仍一致，更新时间更新；Console error/warn 为 0。
- node --test dehui-property-admin/src/utils/dashboardRoomStatistics.test.js：5 项通过。
- npm --prefix dehui-property-admin run build 和 git diff --check：通过。
- 验收截图 dashboard-room-statistics-proof.png 留在工作区，未加入 Git。测试后恢复默认视口。

## 上线和回滚

延续驾驶舱专用增量发布。新增资源 DashboardView-rooms-20261020-v2.js / .css，
index.html 中驾驶舱 importmap 和 CSS 引用切换到新资源，其余原资源 SHA256 不变。
主入口辅助函数/依赖映射沿用前次已验证的对应关系。未重启后端。

备份：/opt/dehui/backups/dashboard-room-stats-20261020-141335，包含 index.html 与发布前资源校验表。
回滚时恢复此备份中的 index.html；原版驾驶舱资源保留。
绕过旧入口 HTML 缓存的验收入口：/dashboard?ui=20261020-rooms2。
