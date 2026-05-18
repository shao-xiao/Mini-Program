# 德汇创新中心物业管理系统

本仓库是德汇创新中心物业管理系统的统一代码仓库，包含后台管理前端、Spring Boot 后端、微信小程序前端三部分。

GitHub 仓库地址：

```text
https://github.com/shao-xiao/Mini-Program
```

当前主分支：

```text
main
```

## 1. dehui_ 工作区路径标准

以下路径均以 `dehui_` 文件夹为根目录，GitHub 文档不再写本机用户目录前缀。

```text
dehui-property-vscode
```

本地后台管理前端：

```text
dehui-property-vscode\dehui-property-admin
```

本地 Spring Boot 后端：

```text
dehui-property-vscode\dehui-property-management
```

本地微信小程序：

```text
dehui-property-vscode\dehui-property-miniprogram
```

原始独立微信小程序工作目录：

```text
dehui-property-miniprogram
```

推荐服务器部署路径：

```text
/opt/projects/Mini-Program
```

服务器后台管理前端路径：

```text
/opt/projects/Mini-Program/dehui-property-admin
```

服务器后端路径：

```text
/opt/projects/Mini-Program/dehui-property-management
```

服务器微信小程序代码路径：

```text
/opt/projects/Mini-Program/dehui-property-miniprogram
```

说明：本地命令默认先进入 `dehui_` 文件夹，再进入对应项目目录，避免在错误目录执行 `mvn spring-boot:run` 或 `npm run build`。

## 2. 项目结构

```text
dehui-property-vscode
├── dehui-property-admin
│   ├── src
│   │   ├── views
│   │   ├── router
│   │   ├── utils
│   │   └── styles
│   ├── package.json
│   └── vite.config.js
├── dehui-property-management
│   ├── src\main\java\com\dehui\property
│   │   ├── common
│   │   ├── config
│   │   └── modules
│   ├── src\main\resources
│   └── pom.xml
├── dehui-property-miniprogram
│   ├── app.js
│   ├── app.json
│   ├── config
│   ├── pages
│   ├── utils
│   └── project.config.json
├── MINIPROGRAM_PLAN.md
└── README.md
```

## 3. 三端职责

后台管理前端：

```text
dehui-property-vscode\dehui-property-admin
```

用途：

- 管理员、运营、财务、员工在浏览器中使用。
- 管理楼宇、楼层、房间、租户、合同、账单、收费规则、停车、能耗、工单、访客、会议室、招商线索。
- 查看小程序提交的数据，例如工单、访客预约、会议预约、招商线索。
- 财务人员审核账单、查看财务数据。

Spring Boot 后端：

```text
dehui-property-vscode\dehui-property-management
```

用途：

- 提供后台管理接口。
- 提供微信小程序移动端接口。
- 负责登录、权限、业务校验、账单生成、审核、数据持久化。
- 统一接口前缀为：

```text
/api
```

微信小程序：

```text
dehui-property-vscode\dehui-property-miniprogram
```

用途：

- 给租户、内部员工、访客在微信中使用。
- 支持公告、账单、会议预约、访客预约、工单报修、招商咨询、员工签到。
- 只请求后端移动端接口：

```text
/api/mobile/**
```

## 4. 公网联调配置

微信小程序合法域名已按以下域名配置：

```text
https://wuye.mingda.com.cn
```

后端健康检查接口：

```text
https://wuye.mingda.com.cn/api/ping
```

预期返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "status": "ok",
    "time": "2026-05-19T..."
  }
}
```

小程序生产接口地址配置文件：

```text
dehui-property-vscode\dehui-property-miniprogram\config\env.js
```

生产地址：

```text
https://wuye.mingda.com.cn/api
```

## 5. 本地运行

运行后端：

```powershell
cd dehui-property-vscode\dehui-property-management
mvn spring-boot:run
```

后端本地地址：

```text
http://localhost:8080/api
```

运行后台管理前端：

```powershell
cd dehui-property-vscode\dehui-property-admin
npm install
npm run dev
```

后台管理前端本地地址：

```text
http://localhost:5173
```

打开微信小程序：

```text
dehui-property-vscode\dehui-property-miniprogram
```

使用微信开发者工具导入该目录。

## 6. 服务器部署

服务器建议先拉取仓库到固定绝对路径：

```bash
cd /opt/projects
git clone https://github.com/shao-xiao/Mini-Program.git
```

后续更新代码：

```bash
cd /opt/projects/Mini-Program
git pull origin main
```

编译后端：

```bash
cd /opt/projects/Mini-Program/dehui-property-management
mvn clean package -DskipTests
```

前台运行后端：

```bash
cd /opt/projects/Mini-Program/dehui-property-management
mvn spring-boot:run
```

或运行 jar：

```bash
cd /opt/projects/Mini-Program/dehui-property-management
java -jar target/*.jar
```

构建后台管理前端：

```bash
cd /opt/projects/Mini-Program/dehui-property-admin
npm install
npm run build
```

后台前端构建产物目录：

```text
/opt/projects/Mini-Program/dehui-property-admin/dist
```

## 7. 本次已推送的重要改动

最新提交：

```text
96e0c05 Enhance billing audit and miniprogram integration
```

### 7.1 后端健康检查

文件：

```text
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\common\HealthController.java
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\config\WebConfig.java
```

作用：

- 新增 `GET /api/ping`。
- 用于检查公网域名、Nginx、后端服务是否连通。
- 放行 `/ping`，无需登录 token。

### 7.2 账单审核与来源追踪

主要文件：

```text
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\bill
```

作用：

- 账单支持审核通过、审核驳回、审核备注。
- 账单支持按租户、状态、审核状态、账单类型筛选。
- 账单增加标题、审核状态、审核人、审核时间、来源类型、来源 ID、备注、逾期标识。
- 手动账单可以不绑定合同，适合物业费、停车费、维修费、会议费、调整费用等场景。
- 自动生成账单时记录来源，便于追溯来自合同、收费规则、能源、停车、会议室或工单。

新增审核请求对象：

```text
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\bill\dto\BillAuditRequest.java
```

### 7.3 权限调整

文件：

```text
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\config\AuthInterceptor.java
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\system\service\SystemUserService.java
```

作用：

- 新增 `bill:audit` 权限判断。
- 财务角色可以执行账单审核。
- 放行 `/api/ping` 和开发联调接口 `/api/mobile/dev/fixtures`。

### 7.4 小程序员工签到

后端文件：

```text
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\mobile\controller\MobileCheckinController.java
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\mobile\service\MobileCheckinService.java
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\mobile\entity\StaffCheckin.java
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\mobile\repository\StaffCheckinRepository.java
```

小程序文件：

```text
dehui-property-vscode\dehui-property-miniprogram\pages\checkin
```

作用：

- 内部员工可以在小程序里上班签到、下班签退。
- 支持填写地点、经度、纬度、备注。
- 支持调用微信定位能力。
- 支持查看当前员工自己的签到记录。
- 签到数据写入 `staff_checkin` 表。

### 7.5 小程序联调设置

文件：

```text
dehui-property-vscode\dehui-property-miniprogram\config\env.js
dehui-property-vscode\dehui-property-miniprogram\utils\request.js
dehui-property-vscode\dehui-property-miniprogram\pages\me
```

作用：

- 小程序“我的”页面可以查看当前接口地址。
- 支持保存接口地址，便于切换本机、局域网、生产环境。
- 支持检查后端 `/ping`。
- 支持读取开发联调数据 `/mobile/dev/fixtures`。
- 请求封装改为每次动态读取 API 地址，不再写死单一 `baseURL`。

### 7.6 移动端兼容性调整

文件：

```text
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\mobile\controller
dehui-property-vscode\dehui-property-miniprogram\pages\meeting
dehui-property-vscode\dehui-property-miniprogram\pages\visitors
dehui-property-vscode\dehui-property-miniprogram\pages\workorders
```

作用：

- 会议取消、访客取消、工单取消、工单评价支持 `POST`。
- 小程序端对应操作从 `PATCH` 改成 `POST`。
- 降低微信小程序端请求方法兼容问题。

### 7.7 访客车牌号

文件：

```text
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\visitor\entity\VisitorRecord.java
dehui-property-vscode\dehui-property-management\src\main\java\com\dehui\property\modules\mobile\service\MobileVisitorService.java
dehui-property-vscode\dehui-property-admin\src\views\operation\VisitorList.vue
```

作用：

- 访客记录新增 `carPlateNo` 字段。
- 小程序提交车牌号时单独保存。
- 后台访客列表单独展示车牌号。
- 不再把车牌号拼接到备注里，便于后续查询和管理。

### 7.8 后台管理页面细节

文件：

```text
dehui-property-vscode\dehui-property-admin\src\views\tenant\BillList.vue
dehui-property-vscode\dehui-property-admin\src\views\finance\FinanceDashboard.vue
dehui-property-vscode\dehui-property-admin\src\views\investment\InvestmentLeadList.vue
dehui-property-vscode\dehui-property-admin\src\views\meeting\MeetingBookingList.vue
dehui-property-vscode\dehui-property-admin\src\views\operation\WorkOrderList.vue
```

作用：

- 账单列表支持审核状态、来源类型、逾期状态和审核操作。
- 财务看板适配账单审核与应收数据。
- 招商线索显示来源，小程序提交的线索有明确标识。
- 会议预约显示申请人类型：内部员工、租户、外部客户。
- 工单来源文案改为“小程序提交”。

## 8. 接口约定

统一响应格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

后台管理接口：

```text
/api/**
```

小程序移动端接口：

```text
/api/mobile/**
```

健康检查接口：

```text
/api/ping
```

开发联调接口：

```text
/api/mobile/dev/fixtures
```

该接口仅用于 `dev` 环境辅助联调。

## 9. 验证记录

已在本机验证后台管理前端构建：

```powershell
cd dehui-property-vscode\dehui-property-admin
npm run build
```

结果：

```text
build success
```

说明：

- Vite 提示部分包体较大，这是构建体积警告，不是构建失败。
- 当前本机没有 `mvn` 命令，后端需在服务器或已安装 Maven 的机器上验证。

服务器后端验证命令：

```bash
cd /opt/projects/Mini-Program/dehui-property-management
mvn clean package -DskipTests
curl https://wuye.mingda.com.cn/api/ping
```

## 10. Git 操作标准

本地从 `dehui_` 文件夹进入仓库根目录：

```powershell
cd dehui-property-vscode
```

查看状态：

```powershell
git status --short
```

提交：

```powershell
git add dehui-property-admin dehui-property-management dehui-property-miniprogram README.md
git commit -m "Update project documentation"
```

推送到 GitHub：

```powershell
git push mini-program main
```

服务器更新：

```bash
cd /opt/projects/Mini-Program
git pull origin main
```

## 11. 不应提交的文件

以下内容不应提交到 GitHub：

```text
dehui-property-vscode\dehui-property-admin\node_modules
dehui-property-vscode\dehui-property-admin\dist
dehui-property-vscode\dehui-property-management\target
dehui-property-vscode\dehui-property-management\data
dehui-property-vscode\dehui-property-management\uploads
dehui-property-vscode\dehui-property-miniprogram\project.private.config.json
```

原因：

- `node_modules` 是依赖目录，可通过 `npm install` 生成。
- `dist` 是前端构建产物，可通过 `npm run build` 生成。
- `target` 是后端构建产物，可通过 Maven 生成。
- `data` 是本地数据库文件。
- `uploads` 是运行期上传文件。
- `project.private.config.json` 是微信开发者工具本机私有配置。
