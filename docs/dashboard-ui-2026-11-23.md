# 物业后台驾驶舱前端发布记录

## 账期联动与设备档案

源码提交 `c17474a`。在驾驶舱点选趋势图中的月份，下方显示该账期的应收、实收和最近八笔以内的账单；选择「全部账期」还原列表。设备台账操作列的「查看」打开本页设备档案，支持连续翻看及关闭后返回原按钮。触屏设备的查看入口为 44×44px。

| 项目 | 结果 |
| --- | --- |
| 构建包 | 2,230,494 字节，SHA-256 `f0fd3182d194f703897478b582c61d559af3a908f3dca5f332324971988a124e` |
| 入口 SHA-256 | `5987b03564f954496fdcf128728add289c9e0cdc5b1b12821442509182dd0e5f` |
| 本地验证 | 生产构建、50 项组件测试通过。隔离真实接口完成 1440px 与 390px 鼠标、键盘和触屏验证。只读账号可访问设备档案，查看、翻台和关闭均未额外请求业务接口。 |
| 公网验证 | 设备台账与驾驶舱入口均为 HTTP 200，线上 83 个文件均与验收构建逐字节一致。 |
| 上一版入口备份 | `/opt/dehui/backups/frontend-interactions-pre-f0fd3182/index.html` |

## 模块间距版本

相邻指标、图表、台账和待处理面板使用同一网格间距。白色面板之间露出浅灰底色，模块内部保留图例、分隔线和紧凑的表格行。浏览器在 1600px 和 1100px 宽度实测面板外沿间距均为 20px，390px 宽度实测为 12px。间距中点取色为 `#f1f2f4`。

| 项目 | 结果 |
| --- | --- |
| 构建包大小 | 2,231,025 字节 |
| 构建包 SHA-256 | `943ba546969765105693c92d5133dbb57336bd7e0ed2d7570fa804238bcce3c4` |
| 入口 SHA-256 | `ae3e81b798d00692ad71a2ab6201e88bbf0ce0e25f61c36b12b72969debdce70` |
| 本地验证 | 生产构建成功。14 项驾驶舱统计及界面检查通过。三个视口验证图例、刷新和展开，窄屏验证导航。 |
| 公网验证 | 驾驶舱及能耗路由均为 HTTP 200。下载并逐一比较本版 83 个静态文件，字节全部一致。 |

源码提交为 `e88960d`。旧入口已保存到 `/opt/dehui/backups/dashboard-gutters-pre-943ba546-2blY92/index.html`，其哈希对应前一版入口。ECS 校验 83 项文件并完成入口切换。

在 `dehui-property-admin` 目录内运行本版的构建和回归检查。

```sh
npm run build
node --test src/utils/dashboardRoomStatistics.test.js src/utils/financeDashboard.test.js tests/adminUiRefinement.test.mjs
```

## 前一版的信息层级与导航更新

2026-11-23 通过已登录的阿里云 Workbench 将前一版构建包上传到 ECS 的暂存目录，发布到 `/opt/dehui/frontend/admin/dist`。源码版本为 `6849415`，分支为 `codex/admin-ui-polish-20261123`。

### 发布内容

驾驶舱默认展示六项指标、三张图和最近八笔账单。桌面模块纵向间隔为 20px，左右分栏内距为 22px，手机模块纵向间隔为 12px。账单和合同均无待办时，首页用一行显示结果。窄屏主导航可以展开和关闭。共用表头采用灰底和更明显的下边线。

### 文件与验证

| 项目 | 验证结果 |
| --- | --- |
| 构建包大小 | 2,231,836 字节 |
| 构建包 SHA-256 | `b23828eb4af524f09d1ad1868389f018cbf1295220413f0cc5053a85b3163645` |
| 旧入口 SHA-256 | `6189e66a9c57366b4216e380893cead894876c0b434d0b151af5b19ed13aec4e` |
| 新入口 SHA-256 | `ef2bdcfc4ab68a3116e93f6222290bf60f1969e77e287c267a3441e6c11b81d3` |
| 公网检查 | 驾驶舱和能耗管理路由均返回 HTTP 200，入口与本地构建相同。另逐项下载并比对 83 个静态文件，83 项一致。 |
| 发布前测试 | 50 项组件检查与 30 项导航、统计、权限及界面契约检查通过。真实隔离开发环境验证了导航、图表、账单、空态，以及财务角色没有发起 AI 请求。 |

普通哈希资源先安装，原有资源继续供已打开的页面使用。固定路径 `icons.svg` 的新旧字节仅有换行符差异，发布前保存原文件。最后才原子替换 `index.html`。本次为静态前端发布。

### ECS 切换记录

旧入口和原始图标已校验并保存在 `/opt/dehui/backups/dashboard-spaced-pre-b23828eb-MqhMev`。服务器实际先从暂存目录执行 `sha256sum --quiet -c SHA256SUMS`，再用 `rsync --ignore-existing` 安装新资源。把新入口放到线上目录内的临时文件后执行 `mv -fT "$entry" "$live/index.html"`。临时文件与正式入口位于同一文件系统，访问者读取到的是一个完整版本。最后在正式目录重新校验整份清单。

## 自行复核

在 Mac 的项目根目录运行下面两行。第一行读取本地构建，第二行从公网重新下载 HTML，再计算 SHA-256。

```sh
shasum -a 256 dehui-property-admin/dist/index.html
curl -fsSL -H 'Cache-Control: no-cache' https://wuye.mingda.com.cn/dashboard | shasum -a 256
```

本地 dist 对应当前线上版本时，两行应输出文首最新版本的入口 SHA-256。已打开驾驶舱时，重新载入即可加载当前入口。

在 ECS 上可以单独核对 Nginx 正在提供的入口。

```sh
sha256sum /opt/dehui/frontend/admin/dist/index.html
```
