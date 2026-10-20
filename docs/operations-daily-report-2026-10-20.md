# 运营日报补齐（2026-10-20）

## 原因与范围

原 DailyReportController 仅返回 id=0、固定零值、英文测试摘要以及空风险和历史数组；原前端以 !report.id 隐藏所有模块，生产有业务数据仍只显示空态。

本次只修改前端，将日报改为当前账号可见业务的实时汇总，不依赖占位日报接口。保留红黑品牌、导航和现有权限，不接入大模型、不将规则提醒标为模型推断，不伪造日报 ID 或历史快照。

## 内容与功能

- 六项指标：实时房间出租率、累计待收、逾期账单、待跟进工单、未关闭异常巡检、未来 30 天到期合同。
- 今日摘要、优先处理清单，提醒依据真实状态和到期日期生成；已授权业务提供列表跳转。
- 收缴租赁、工单巡检、设备能耗概览；能耗明确显示最近有记录的账期。
- 近 7 天新增工单趋势，按 createdTime 的日期计数，未来记录不计入。
- 逾期最早五笔账单、优先工单六条、近期合同六条，复用 WorkspaceDataGrid。
- 刷新与 UTF-8 TXT 日报下载，下载包含指标、摘要、行动提醒与数据覆盖状态。

## 数据及口径

统一走 request.js，加载器按各业务 permission code 判断请求资格；所有模块独立失败，未授权与错误状态明确显示 —，不误作零。房源复用服务端总数，不从第一页推断。

使用既有 /rooms、/bills/summary、/bills、/bills/payments、/contracts、/workorders、/inspections/summary、/equipments、/energy/stats。收款、合同、工单使用共享分页工具；最多 10000 条，超限显示来源失败，不呈现截断的统计。最新请求序号避免旧响应覆盖，离开页面后不更新状态。

- 收款仅统计 PAID 流水，以 paidAt 区分今日/本月，排除未来日期及取消记录；不将账期账单 paidAmount 误当当天收款。
- 合同仅 ACTIVE 状态参与到期提醒，包含今天至未来第 30 天，排除过期、取消与缺失日期。
- 工单 CREATED/SUBMITTED/ASSIGNED/PROCESSING/COMPLETED 属于待跟进，已完成仍待关闭；CLOSED/CANCELLED 不计入。未定义 SLA 字段时不编造超时指标。
- 异常巡检待处理为 result=ABNORMAL 且 status=OPEN；今日巡检按 inspectionDate 筛选，不计算缺乏计划分母的完成率。
- 设备故障为 FAULT；能耗用月度聚合的最近真实账期，各能耗类型独立单位。

合同搜索 /contracts/search 在线遇到历史记录引发的 NullPointerException，日报改用已被驾驶舱使用的 /contracts 台账，并兼容 contractNumber 为展示合同号；该搜索接口本身未在本次改动。

## 验证与发布

- Node：operationsReport、dashboardRoomStatistics、workspaceExport 共 13 项通过；覆盖未授权不请求、部分源失败隔离、真实分页总数、工单状态、支付日期、合同边界与台账兼容。
- npm --prefix dehui-property-admin run build、git diff --check 通过。
- 线上全部 9 项来源已更新：房源39/已租11/出租率28.2%，待收3631299.81元、逾期14笔/3458645.61元，未关闭异常巡检13条，工单已完成待关闭1单，生效合同10份，设备205台/故障0台，与只读数据库核对一致。
- 实际刷新、巡检处理入口和返回通过；1596、1024、618px 页面无横向溢出；浏览器 error/warn 为0，下载按钮执行无异常（未核验浏览器实际落盘文件）。验收结束恢复视口并关闭临时标签页。
- 后端、数据库、权限配置未修改。新增版本化 DailyReport-live-20261020-v2.js/.css、无业务依赖的房源统计辅助资源，通过 index.html importmap/CSS 增量引用；原88个已有文件 SHA256 不变。
- 备份 /opt/dehui/backups/operations-daily-report-20261020-150251，恢复 index.html 即可回滚。
- 验收入口 /ai/daily-report?ui=operations-live-20261020-v2。
