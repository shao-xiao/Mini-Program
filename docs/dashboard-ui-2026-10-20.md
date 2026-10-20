# 驾驶舱 UI 优化（2026-10-20）

## 展示调整

保持现有红黑配色、导航与数据接口。六个核心指标集中为一个紧凑指标带，区分本月/累计/风险。
四个图表以 7:5 / 5:7 完整栅格排列，趋势图保持金额轴；来源结构显示金额和占比；
逾期、临期、正常使用红、黄、灰色；缺少房源统计时显示缺省状态，不绘制零值等分饼图。
表格采用紧凑行高、金额右对齐和中文状态；新增更新时间及分模块失败提示。
DashboardPanel 复用面板标题、统计标签和内容布局。

数据继续来自 /bills、/contracts、/ai/daily-report；不新增接口，不调整后端授权或数据库。
未收款指标的说明改为各账期累计，与原金额口径一致；财务汇总算法未更改。
房源统计当前接口返回零值，因此显示暂无统计，不补造房间数量。

## 验证

- npm --prefix dehui-property-admin run build：通过。
- 前端目录运行 node --test src/config/access.test.js src/config/permissionRules.test.js src/utils/requestLifecycle.test.js src/utils/pagination.test.js：22 项通过。
- 线上 ADMIN +1 会话：本月应收 24.6 万、未收款 363.1 万、逾期 14 笔，与改版前一致。
- 1734x1100 桌面、1024x900、618x900 窄屏目视验收；1024/618 的 document.scrollWidth 等于 viewport，无页面横向溢出。
- 刷新按钮更新成功，控制台 error/warn 为 0。测试后已恢复默认浏览器视口。
- 桌面效果图保存在工作区 dashboard-ui-proof.png（未加入 Git）。

## 增量上线

生产其他前端模块与当前仓库全量构建不保证版本一致，因此未整包覆盖。
新 Dashboard bundle 仅依赖生产已存在的 element-plus、echarts、pagination 和主入口共享导出。
构建基线与更新后的公共 vendor chunk 完全一致；将 Dashboard 的 import 路径映射至生产对应
chunk，主入口 SFC 辅助函数的导出别名 h 映射为生产 f，request 导出 i 保持原样。

发布文件：

- assets/DashboardView-ui20261020-1403.js
- assets/DashboardView-ui20261020-1403.css
- index.html：增加驾驶舱模块 importmap 与版本化 CSS 引用。

原 DashboardView-C1TIguLw.js / DashboardView-BguqYAjs.css 和其他现有资源均保留原字节。
只替换 Dashboard 的模块解析，其他业务页面继续使用原构建及依赖，不重启后端。
旧入口 HTML 被浏览器缓存时，可通过 /dashboard?ui=20261020 绕过旧缓存。

备份目录：/opt/dehui/backups/dashboard-ui-20261020-140157。
包含 index.html、原 Dashboard JS/CSS、before-sha256.json 与发布清单。
回滚时恢复备份 index.html；旧 Dashboard 资源已保留原样，新版本资源可保留以供回溯。
后续正式全量构建无需此增量映射，正常部署新 dist 即可。
