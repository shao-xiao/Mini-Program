# 德汇创新中心微信小程序规划

本文档用于固化微信小程序建设方向，避免后续开发过程中偏离初衷。

## 一、总体判断

德汇创新中心移动端应以微信小程序为主，而不是以公众号为主。

公众号适合做品牌宣传、内容发布、招商文章、活动推送和客户触达；小程序更适合承载业务办理，包括账单查询、缴费、会议室预约、报修、访客、签到、招商线索等。因此本项目的移动端核心应建设为微信小程序，公众号后续可作为宣传和引流入口。

## 二、产品定位

现有网页端继续作为物业运营后台，面向内部管理人员和运营团队。

微信小程序作为移动服务端，服务三类用户：

- 内部员工
- 租户用户
- 关注德汇创新中心的外部客户或潜在客户

系统整体方向是：

```text
后台运营端 + 微信小程序服务端 + 微信身份体系 + 支付/通知能力
```

## 三、用户角色与核心场景

### 1. 内部员工

内部员工可使用与自身权限相匹配的移动端能力，包括：

- 查看权限范围内的运营信息
- 工单处理
- 巡检任务
- 访客核验
- 会议室预约与管理
- 打卡签到
- 公告查看
- 移动端简版 BI 或待办提醒

内部员工不应简单复制网页后台全部菜单，而应按移动办公场景重组功能。

### 2. 租户用户

租户用户面向租户公司联系人和租户员工，主要能力包括：

- 查看租金账单
- 查看物业费账单
- 查看水电煤/能耗账单
- 查看停车账单
- 查看会议室账单
- 在线缴费
- 查看缴费记录
- 会议室预约
- 报修工单
- 查看工单进度
- 访客预约
- 公告查看
- 合同/租赁信息查看

现有系统的租户是公司维度，小程序用户是个人维度，因此需要补充租户联系人体系。

### 3. 外部客户 / 关注用户

外部客户主要服务招商和园区展示，包括：

- 园区介绍
- 招商信息
- 可租房源
- 招商政策
- 公告活动
- 预约看房
- 留资咨询
- 联系我们

该类用户未绑定租户前，只能访问公开信息和招商服务。

## 四、核心原则

### 1. 不直接暴露后台接口

小程序不应直接调用现有后台运营接口，而应新增 `/mobile/**` 接口层。

原因：

- 移动端返回数据结构不同
- 租户用户必须严格按绑定租户隔离数据
- 外部客户只能访问公开信息
- 内部员工移动端权限应与后台角色映射，但不能简单开放全部后台能力

### 2. 先做 MVP，后接真实微信能力

第一阶段不应被微信支付、手机号授权、线上域名、备案、审核等外部依赖卡住。

建议先实现：

- 小程序项目骨架
- 移动端接口骨架
- 模拟微信用户或开发态登录
- 内部账号绑定
- 租户联系人绑定
- 公告、账单、会议室、报修、招商等核心闭环

之后再接：

- 真实微信登录
- 手机号授权
- 微信支付
- 支付回调
- 订阅消息
- 小程序审核上线

### 3. 租户数据必须隔离

租户用户只能查看自己绑定租户的数据，包括：

- 账单
- 合同摘要
- 会议室预约
- 报修工单
- 访客预约
- 缴费记录

不得允许租户通过前端参数查看其它租户数据。

### 4. 移动端以办理为主，不照搬后台

小程序页面应围绕高频任务设计：

- 我要缴费
- 我要报修
- 我要预约会议室
- 我要预约看房
- 我的账单
- 我的工单
- 我的预约
- 我的访客

不要把后台菜单机械搬到小程序。

## 五、建议一期 MVP 范围

一期目标：做出一个能演示、能跑通核心业务闭环的小程序。

### 小程序端页面

- 首页
- 我的
- 身份绑定
- 公告列表/详情
- 租户账单
- 缴费记录
- 会议室预约
- 报修提交
- 我的工单
- 招商中心
- 可租房源
- 预约看房
- 内部员工签到

### 后端能力

- 微信用户表
- 租户联系人表
- 移动端登录/绑定接口
- 移动端公告接口
- 移动端账单接口
- 移动端会议室接口
- 移动端会议预约接口
- 移动端报修接口
- 移动端招商接口
- 移动端签到接口

### 暂缓能力

以下能力不放在第一阶段强依赖中：

- 正式微信支付
- 真实手机号一键授权
- 法定节假日完整日历
- 发票申请
- 电子合同
- AI 客服
- 小程序正式审核上线

## 六、建议数据模型

### 1. 微信用户表 `wechat_user`

建议字段：

- id
- openId
- unionId
- phone
- nickname
- avatar
- userType: INTERNAL / TENANT / PUBLIC
- boundSysUserId
- boundTenantId
- status
- createdTime
- updatedTime

### 2. 租户联系人表 `tenant_contact`

建议字段：

- id
- tenantId
- name
- phone
- role
- isPrimary
- status
- createdTime
- updatedTime

### 3. 支付流水表 `payment_record`

建议字段：

- id
- billId
- billSource: RENT / PARKING / MEETING / ENERGY / OTHER
- amount
- payChannel: WECHAT
- transactionId
- prepayId
- status
- paidTime
- callbackPayload
- createdTime
- updatedTime

### 4. 签到表 `staff_checkin`

建议字段：

- id
- sysUserId
- checkinTime
- checkinType: ON_DUTY / OFF_DUTY
- location
- longitude
- latitude
- remark
- status

### 5. 招商线索表 `investment_lead`

建议字段：

- id
- name
- phone
- companyName
- desiredArea
- intendedUse
- preferredVisitTime
- source
- status
- remark

## 七、建议接口规划

小程序接口统一使用 `/mobile/**` 前缀。

### 登录与绑定

```text
POST /mobile/auth/wechat-login
POST /mobile/auth/bind-internal
POST /mobile/auth/bind-tenant
GET  /mobile/auth/me
```

### 公告

```text
GET /mobile/announcements
GET /mobile/announcements/{id}
```

### 账单

```text
GET  /mobile/bills
GET  /mobile/bills/{id}
POST /mobile/bills/{id}/pay-preview
```

### 支付

```text
POST /mobile/payments/wechat/prepay
POST /mobile/payments/wechat/callback
GET  /mobile/payments/records
```

第一阶段可先做模拟支付或仅生成待支付记录。

### 会议室

```text
GET  /mobile/meeting-rooms
GET  /mobile/meeting-rooms/availability
POST /mobile/meeting-bookings
GET  /mobile/meeting-bookings/my
PATCH /mobile/meeting-bookings/{id}/cancel
```

### 报修工单

```text
POST /mobile/workorders
GET  /mobile/workorders/my
GET  /mobile/workorders/{id}
POST /mobile/workorders/{id}/comments
```

### 访客

```text
POST /mobile/visitors
GET  /mobile/visitors/my
```

### 招商

```text
GET  /mobile/investment/overview
GET  /mobile/investment/rooms
POST /mobile/investment/leads
```

### 内部签到

```text
POST /mobile/checkins
GET  /mobile/checkins/my
```

## 八、小程序页面建议

### 首页

- 公告摘要
- 待办事项
- 待缴账单
- 我的预约
- 快捷入口

### 我的

- 微信头像/昵称
- 当前身份
- 绑定租户或内部账号
- 我的账单
- 我的工单
- 我的预约
- 缴费记录

### 租户服务

- 账单中心
- 在线缴费
- 会议室预约
- 报修服务
- 访客预约
- 公告通知

### 内部办公

- 打卡签到
- 工单处理
- 巡检任务
- 访客核验
- 会议室管理

### 招商中心

- 园区介绍
- 可租房源
- 招商政策
- 预约看房
- 联系我们

## 九、建议开发节奏

在 Codex 协作模式下，若外部微信配置暂不阻塞，建议按以下节奏推进。

### 第一阶段：1-2 天

- 新建 `dehui-property-miniprogram`
- 后端新增移动端模块骨架
- 新增微信用户/租户联系人模型
- 新增移动端登录和绑定接口
- 新增公告、账单、会议室、工单、招商 API 骨架

### 第二阶段：2-3 天

- 完成小程序基础页面
- 首页
- 我的
- 身份绑定
- 公告
- 租户账单
- 会议室预约
- 报修提交
- 招商展示
- 内部签到

### 第三阶段：1-2 天

- 打通核心业务闭环
- 租户身份绑定
- 查看租户账单
- 发起会议预约
- 提交报修工单
- 内部员工签到
- 招商线索提交

### 第四阶段：后续增强

- 微信支付
- 支付回调
- 订阅消息
- 发票申请
- 巡检扫码
- 移动端 BI
- AI 移动助手

## 十、外部依赖清单

这些不是纯代码问题，需要提前准备：

- 微信小程序 AppID
- 微信小程序 AppSecret
- 开发者权限
- 服务器域名
- HTTPS 证书
- ICP 备案
- 微信支付商户号
- 支付证书/APIv3 Key
- 小程序类目
- 隐私协议
- 用户服务协议
- 审核材料

## 十一、方向边界

短期不要做：

- 公众号优先
- 一次性复制后台所有页面
- 未做租户隔离就开放账单接口
- 未做支付流水就直接改账单已支付
- 小程序内做复杂后台配置
- 没有绑定关系就展示敏感租户数据

短期应该做：

- 小程序业务闭环
- 移动端身份绑定
- 租户数据隔离
- 账单查询
- 会议室预约
- 报修
- 招商展示
- 内部签到

## 十二、最终目标

微信小程序不是后台系统的附属页面，而是德汇创新中心面向员工、租户和潜在客户的移动服务入口。

最终它应形成：

```text
租户服务入口 + 内部移动办公入口 + 招商获客入口
```

并与现有后台系统共同组成：

```text
AI + BI + 移动服务 的企业级物业运营平台
```
