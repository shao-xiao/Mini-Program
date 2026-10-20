# 租户表具逐期读数趋势（2026-10-20）

在个体租户月度用量分析下新增“表具读数趋势”，保留原有用量趋势。复用 GET /energy/readings 与 request.js，按当前租户、能耗类型及月份条件分页读取，沿用服务端权限；失败独立提示，不影响汇总统计。读取上限沿用共享分页工具的 10000 条限制，超限提示缩小月份范围。

按 meterId 区分物理表具，同名表具不会合并，累计读数不相加、不乘倍率。图表按实际账期升序绘制上次读数/本期读数两条线，支持缩放和表具搜索切换；明细复用 WorkspaceDataGrid，按账期倒序显示抄表日期、两次原始读数和倍率。真实零值保留，缺失值留空。类型、月份和主体切换同步过滤，最新请求守卫避免旧主体响应覆盖。

验证：
- energyStatistics.test.js 与 workspaceExport.test.js 共 8 项通过；生产构建和 git diff --check 通过。
- tenant-3 / 9F空调外：2026-08 为 33262.02 → 53733.84，2026-09 为 53733.84 → 62842.05；线上趋势及明细一致。
- 切换水类型，3FS 显示 2026-08 为 83 → 91，2026-09 为 91 → 100，单位 m³。
- 筛选 2026-09 后两张表与读数图均仅保留当月；切换 tenant-9 后仅显示该租户表具读数。浏览器 error/warn 为 0。

仅增量发布 AssetEnergyView-reading-trend-20261020-v1.js/.css 与 index.html 引用。其他已有 86 个文件 SHA256 未变；未修改后端、数据库或 RBAC。备份 /opt/dehui/backups/energy-reading-trend-20261020-144824，恢复其中 index.html 即可回滚。

验收入口 /assets/energy?tab=statistics&subject=tenant-3&ui=energy-reading-trend-20261020。
