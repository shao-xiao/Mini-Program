# 物业后台驾驶舱前端发布记录

2026-11-23 通过已登录的阿里云 Workbench 将构建包上传到 ECS 的暂存目录，发布到 `/opt/dehui/frontend/admin/dist`。源码版本为 `6849415`，分支为 `codex/admin-ui-polish-20261123`。

## 发布内容

驾驶舱默认展示六项指标、三张图和最近八笔账单。桌面模块纵向间隔为 20px，左右分栏内距为 22px，手机模块纵向间隔为 12px。账单和合同均无待办时，首页用一行显示结果。窄屏主导航可以展开和关闭。共用表头采用灰底和更明显的下边线。

## 文件与验证

| 项目 | 验证结果 |
| --- | --- |
| 构建包大小 | 2,231,836 字节 |
| 构建包 SHA-256 | `b23828eb4af524f09d1ad1868389f018cbf1295220413f0cc5053a85b3163645` |
| 旧入口 SHA-256 | `6189e66a9c57366b4216e380893cead894876c0b434d0b151af5b19ed13aec4e` |
| 新入口 SHA-256 | `ef2bdcfc4ab68a3116e93f6222290bf60f1969e77e287c267a3441e6c11b81d3` |
| 公网检查 | 驾驶舱和能耗管理路由均返回 HTTP 200，入口与本地构建相同。另逐项下载并比对 83 个静态文件，83 项一致。 |
| 发布前测试 | 50 项组件检查与 30 项导航、统计、权限及界面契约检查通过。真实隔离开发环境验证了导航、图表、账单、空态，以及财务角色没有发起 AI 请求。 |

普通哈希资源先安装，原有资源继续供已打开的页面使用。固定路径 `icons.svg` 的新旧字节仅有换行符差异，发布前保存原文件。最后才原子替换 `index.html`。本次为静态前端发布。

## ECS 切换记录

旧入口和原始图标已校验并保存在 `/opt/dehui/backups/dashboard-spaced-pre-b23828eb-MqhMev`。服务器实际先从暂存目录执行 `sha256sum --quiet -c SHA256SUMS`，再用 `rsync --ignore-existing` 安装新资源。把新入口放到线上目录内的临时文件后执行 `mv -fT "$entry" "$live/index.html"`。临时文件与正式入口位于同一文件系统，访问者读取到的是一个完整版本。最后在正式目录重新校验整份清单。

## 自行复核

在 Mac 的项目根目录运行下面两行。第一行读取本地构建，第二行从公网重新下载 HTML，再计算 SHA-256。

```sh
shasum -a 256 dehui-property-admin/dist/index.html
curl -fsSL -H 'Cache-Control: no-cache' https://wuye.mingda.com.cn/dashboard | shasum -a 256
```

二者应与表格中的新入口 SHA-256 一致。右侧页面已打开时，通过浏览器重新载入页面，以便加载新版前端。

在 ECS 上可以单独核对 Nginx 正在提供的入口。

```sh
sha256sum /opt/dehui/frontend/admin/dist/index.html
```
