# 个体租户月度能耗视图（2026-10-20）

## 范围

仅 embedded 且 subjectTenantId 为有效租户 ID、subjectScope 非 property 时启用精简视图。
物业公共区域和独立统计页继续使用综合分析。

租户视图只保留月份、能耗类型两项筛选，新增每月用量与费用表，按账期倒序。
表格使用既有 WorkspaceDataGrid，列为账期、有记录的能耗类型及其单位、结算费用和记录数。
只有电记录时仅展示电指标与电列；水电都有记录时分列展示，缺少类型记录的月份显示缺省值。

隐藏房间排行、楼宇排行、费用饼图和空异常模块，保留用量趋势。
切换租户会清除隐藏的空间、账单状态、异常筛选，月份与能耗类型保持可见可控。
异常存在时仍保留原处理入口与权限。未修改 API、后端、数据库或授权配置。

## 验证

- npm --prefix dehui-property-admin run build：通过。
- node --test dehui-property-admin/src/utils/energyStatistics.test.js：4 项通过。
- git diff --check：通过。
- tenant-9：每月明细 2026-09 电 1479.29 kWh、结算 1937.88 元、10 条，排行等模块已隐藏。
- tenant-3：2026-09 电 29249.27 kWh、水 45 m³、结算 38586.10 元；
  2026-08 电 45176.16 kWh、水 39 m³、结算 58510.86 元，各 11 条。
- 月份筛选 2026-08 正确收敛为当月一行；清除月份恢复两行。
- 浏览器无 error/warn，原始数据、租户筛选和权限保持原样。

## 上线

只新增能耗页资源 AssetEnergyView-monthly-20261020-v1.js/.css，并更新 index.html 中能耗页
importmap 与 CSS 引用；其他已有资源 SHA256 保持不变，未重启后端。
备份目录 /opt/dehui/backups/energy-tenant-monthly-20261020-143747，回滚恢复其中 index.html 即可。
验收入口 /assets/energy?tab=statistics&subject=tenant-3&ui=energy-monthly-20261020。
