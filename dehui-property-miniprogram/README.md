# 德汇创新中心微信小程序

本目录是微信小程序前端代码，属于统一仓库的一部分。

仓库根目录：

```text
dehui-property-vscode
```

小程序目录：

```text
dehui-property-vscode\dehui-property-miniprogram
```

GitHub 仓库：

```text
https://github.com/shao-xiao/Mini-Program
```

## 1. 打开方式

使用微信开发者工具导入以下项目目录：

```text
dehui-property-vscode\dehui-property-miniprogram
```

小程序 AppID 配置在：

```text
dehui-property-vscode\dehui-property-miniprogram\project.config.json
```

本机私有配置不提交：

```text
dehui-property-vscode\dehui-property-miniprogram\project.private.config.json
```

## 2. 接口地址

接口配置文件：

```text
dehui-property-vscode\dehui-property-miniprogram\config\env.js
```

生产接口地址：

```text
https://wuye.mingda.com.cn/api
```

健康检查接口：

```text
https://wuye.mingda.com.cn/api/ping
```

小程序所有请求必须通过：

```text
dehui-property-vscode\dehui-property-miniprogram\utils\request.js
```

## 3. 页面目录

```text
dehui-property-vscode\dehui-property-miniprogram\pages\home
dehui-property-vscode\dehui-property-miniprogram\pages\me
dehui-property-vscode\dehui-property-miniprogram\pages\announcements
dehui-property-vscode\dehui-property-miniprogram\pages\bills
dehui-property-vscode\dehui-property-miniprogram\pages\meeting
dehui-property-vscode\dehui-property-miniprogram\pages\visitors
dehui-property-vscode\dehui-property-miniprogram\pages\workorders
dehui-property-vscode\dehui-property-miniprogram\pages\investment
dehui-property-vscode\dehui-property-miniprogram\pages\checkin
```

页面用途：

- `home`：小程序首页入口。
- `me`：登录、身份绑定、接口联调、后端检查。
- `announcements`：公告列表和公告详情。
- `bills`：租户账单查询。
- `meeting`：会议室预约、取消预约。
- `visitors`：访客预约、取消预约、车牌号提交。
- `workorders`：报修工单、取消工单、评价工单、图片上传。
- `investment`：招商信息和招商线索提交。
- `checkin`：内部员工上班签到、下班签退、签到记录。

## 4. 本次小程序改动

### 4.1 接口联调

文件：

```text
dehui-property-vscode\dehui-property-miniprogram\config\env.js
dehui-property-vscode\dehui-property-miniprogram\utils\request.js
dehui-property-vscode\dehui-property-miniprogram\pages\me
```

作用：

- 支持生产地址 `https://wuye.mingda.com.cn/api`。
- 支持在“我的”页面保存接口地址。
- 支持检查 `/ping` 是否可用。
- 支持读取 `/mobile/dev/fixtures` 测试数据。
- 支持静默请求，避免联调接口失败时频繁弹窗。

### 4.2 员工签到

文件：

```text
dehui-property-vscode\dehui-property-miniprogram\pages\checkin
dehui-property-vscode\dehui-property-miniprogram\app.json
```

作用：

- 新增内部员工签到页面。
- 支持上班签到和下班签退。
- 支持获取微信定位。
- 支持手动填写地点、经纬度、备注。
- 支持展示我的签到记录。
- `app.json` 增加定位权限说明。

### 4.3 账单页面

文件：

```text
dehui-property-vscode\dehui-property-miniprogram\pages\bills
```

作用：

- 展示账单标题。
- 展示账单来源。
- 展示账单备注。
- 适配后端账单审核和来源追踪字段。

### 4.4 预约和工单操作

文件：

```text
dehui-property-vscode\dehui-property-miniprogram\pages\meeting
dehui-property-vscode\dehui-property-miniprogram\pages\visitors
dehui-property-vscode\dehui-property-miniprogram\pages\workorders
```

作用：

- 取消会议预约改为 `POST`。
- 取消访客预约改为 `POST`。
- 取消工单改为 `POST`。
- 工单评价改为 `POST`。
- 与后端兼容性调整保持一致。

### 4.5 招商线索

文件：

```text
dehui-property-vscode\dehui-property-miniprogram\pages\investment\index.js
```

作用：

- 招商线索提交成功后显示线索 ID。
- 方便后台核对小程序提交记录。

## 5. 后端接口约定

小程序移动端接口统一前缀：

```text
/api/mobile
```

常用接口：

```text
GET  /api/ping
GET  /api/mobile/dev/fixtures
GET  /api/mobile/checkins
POST /api/mobile/checkins
POST /api/mobile/meetings/bookings/{id}/cancel
POST /api/mobile/visitors/{id}/cancel
POST /api/mobile/workorders/{id}/cancel
POST /api/mobile/workorders/{id}/evaluation
```

## 6. 微信后台配置

微信公众平台服务器域名：

```text
request 合法域名：https://wuye.mingda.com.cn
```

如果后续启用上传图片，需要额外配置：

```text
uploadFile 合法域名：https://wuye.mingda.com.cn
```

如果后续启用下载文件，需要额外配置：

```text
downloadFile 合法域名：https://wuye.mingda.com.cn
```

## 7. 不提交文件

不要提交：

```text
dehui-property-vscode\dehui-property-miniprogram\project.private.config.json
dehui-property-vscode\dehui-property-miniprogram\node_modules
dehui-property-vscode\dehui-property-miniprogram\miniprogram_npm
dehui-property-vscode\dehui-property-miniprogram\dist
```

原因：

- `project.private.config.json` 是微信开发者工具本机配置。
- `node_modules`、`miniprogram_npm`、`dist` 是依赖或构建产物。
