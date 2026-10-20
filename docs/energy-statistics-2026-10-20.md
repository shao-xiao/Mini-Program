# 能耗统计数据与展示优化（2026-10-20）

## 根因

EnergyController.stats 原本仅返回类型合计与异常记录。
trend、roomRanking、buildingRanking 均为 List.of()，因此前端显示空图，与现有抄表汇总不符。

## 接口与统计口径

继续使用 GET /api/energy/stats，不改原路径、鉴权或筛选参数。
新增三个真实只读聚合复用 readingQuery 的 where 与参数：

- trend：按 er.period_month 汇总电、水、燃气用量、结算金额、总记录数以及各类型记录数。
- roomRanking：按房间和 meterType 汇总 usageAmount / settlementAmount / recordCount。
- buildingRanking：按楼宇和 meterType 使用同样口径汇总。

类型结构额外返回 recordCount。没有删除或重命名旧字段。
每个查询都保留 er.deleted=0、m.deleted=0 和当前主体/类型/账期/楼层/楼宇/房间/
账单状态/异常条件。分组与排序片段来自服务端固定字符串，用户值全部绑定参数。
未关联房间或楼宇保留为明确分组；前端数量仅统计已关联实体。
原 averageUsage 字段为兼容保留，图表不依赖跨单位的平均用量。

## 展示

- 保持现有红黑后台风格，收紧筛选、六项指标、图表间距，去掉原栅格留白。
- 用量趋势、房间与楼宇排行按同一类型比较，明确标注 kWh / m³。
- 趋势取筛选月份或最近真实账期作为终点展示六个账期；无该类型抄表记录的月份为 null，真实零用量仍为 0。
- 结构图使用人民币金额占比，避免混加 kWh 与 m³；零金额类型不生成扇区。
- 增加当前数据范围、记录数、有效账期数量、更新时间及准确的空状态。
- 保留筛选、重置、异常处理及其原权限判断，未修改抄表、计费或账单写操作。

## 验证

后端 Maven compile 通过。EnergyStatisticsAggregationTest、EnergyControllerTest、
EnergyChargeBasisBoundaryTest、EnergyMeterBindingControllerTest、
RbacUpgradePreservationPolicyTest、BusinessDataUpgradePreservationPolicyTest 共 50 项通过。
前端 energyStatistics.test.js 4 项通过，npm run build、git diff --check 通过。

生产只读核对与浏览器结果：

- tenant-9 / 上海亮界未来科技：2026-09 共 10 条电抄表，1479.29 kWh，1937.88 元。
  房间 802 和德汇创新中心的排行均为 1479.29 kWh；费用结构电费 100%。
- 当前主体切换水类型：趋势与排行明确提示没有该类型记录，合计与费用结构仍遵守当前筛选。
- tenant-3 / 上海德汇集团：22 条抄表，电 74425.43 kWh、水 84 m³，结算 97096.96 元；
  类型切换正常。历史未关联空间记录保持原样，单独展示且不计入实际房间数。
- 返回 tenant-9 点击刷新，图表、排行与合计正常。Console error/warn 为 0。
- 1830x1344 桌面截图与 1024px 无页面横向溢出检查通过，已恢复默认视口。
- 工作区 energy-statistics-proof.png 为验收截图，未加入 Git。

## 上线与回滚

备份目录：/opt/dehui/backups/energy-statistics-20261020-142734。

修改前原 EnergyController 源码在生产依赖下编译后的 javap -p -c 输出与线上 class 完全一致。
仅替换 app.jar 内 BOOT-INF/classes/com/dehui/property/modules/energy/controller/EnergyController.class，
逐条比较确认其他 ZIP 项字节不变，ZIP 完整性通过；重启 dehui-backend，8080 正常。
先前照片权限修复保留，未变更 migrations 或数据库记录。

前端新增 AssetEnergyView-stats-20261020-v2.js / .css，入口 importmap 将旧能耗模块映射至新资源，
并引用新 CSS。既有其他资产、财务及驾驶舱资源未覆盖，SHA256 对比仅 index.html 变化。
使用现有生产依赖；主入口辅助导出 h 对应生产 f、request i 保持不变，权限方法继续使用
生产 permission-CNqji9Z7.js 的 a 导出；其他公共 chunk 只映射对应文件名。

回滚：恢复备份目录的 app.jar 与 index.html，再重启 dehui-backend。
旧前端资源均保留。避免旧入口缓存的验收链接：
/assets/energy?tab=statistics&subject=tenant-9&ui=energy-stats-20261020-v2。
