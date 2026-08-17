# 德汇创新中心微信小程序

德汇创新中心微信小程序是 DHIC 物业运营平台的公开展示与租户自助服务端，面向园区访客、招商客户和已匹配的租户联系人。物业内部员工继续使用后台管理系统，小程序第一期不提供内部工作台。

## 项目定位

后台管理系统继续放在 `dehui-property-vscode` 仓库中，小程序仓库只负责微信小程序前端。

后端移动端接口统一规划为：

```text
/api/mobile/**
```

小程序不得直接调用后台运营端敏感接口，应通过移动端接口完成身份隔离、租户数据隔离和移动端数据裁剪。

## 一期 MVP

一期产品边界为“园区公开展示 + 租户自助服务”：

- 公开用户：微信静默会话、公告、招商房源、预约看房、租户登录
- 租户用户：账单与发票、合同、报修、会议预约、访客预约、账号管理
- 登录方式：微信静默会话 + 微信手机号匹配有效租户联系人
- 内部员工：不展示签到、工单处理、审批或核验入口
- 当前阶段不实现微信支付，账单只展示线下缴费说明

## 目录结构

```text
config/              环境配置
utils/               请求封装与工具函数
pages/               小程序页面
  index/             首页
  mine/              我的
  me/                登录与账号管理
  announcements/     公告
  bills/             账单
  contracts/         合同
  meeting/           会议室预约
  workorders/        租户报修
  visitors/          访客预约
  investment/        招商中心
  agreements/        用户服务协议
```

## 开发约定

- 所有网络请求必须走 `utils/request.js`
- API 基础地址从 `config/env.js` 读取
- 移动端接口统一使用 `/mobile/**`
- 不在小程序中写死真实 token
- 不在小程序中越权请求后台运营接口
- 租户数据必须由后端按当前登录身份对应的租户隔离
- 客户端不得提交或决定 `tenantId`、`userId`、角色或权限
- 页面能力统一通过 `utils/capabilities.js` 判断；后端 `capabilities` 字段优先，缺失时才按 `userType` 最小回退

## 关联规划

完整规划见后台仓库：

```text
dehui-property-vscode/MINIPROGRAM_PLAN.md
```

后续涉及小程序方向调整时，应先更新该规划文档。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
