# 微信小程序隐私保护指引配置清单

更新时间：2026-08-05

本文用于微信公众平台后台配置：
`小程序设置 -> 基本设置 -> 服务内容声明 -> 用户隐私保护指引`

## 代码侧已使用的隐私相关能力

| 接口/能力 | 代码位置 | 使用场景 | 后台声明口径 |
| --- | --- | --- | --- |
| `open-type="getPhoneNumber"` | `pages/me/index.wxml`、`pages/meeting/index.wxml`、`pages/investment/index.wxml`、`pages/investment/detail/index.wxml` | 手机号快捷登录，以及提交会议/看房预约前的手机号身份核验 | 手机号 |
| `wx.openPrivacyContract` | `dehui-property-miniprogram/pages/me/index.js` | 打开微信后台配置的《用户隐私保护指引》 | 用户隐私保护指引入口 |
| `wx.chooseImage` | `dehui-property-miniprogram/pages/workorders/index.js` | 工单报修选择或拍摄现场照片 | 选中的照片或视频信息 |
| `wx.uploadFile` | `dehui-property-miniprogram/utils/request.js` | 提交已选择的工单现场照片到后端 | 归入工单照片/用户生成内容，不单独声明文件上传能力 |
| `wx.downloadFile` + `wx.openDocument` | `dehui-property-miniprogram/utils/request.js`、`pages/bills/index.js`、`pages/contracts/index.js` | 用户主动查看自己的发票、电子合同 | 只下载并打开业务文件，不是用户文件采集能力 |

## 当前未使用的隐私接口

当前代码未实际调用以下能力，不建议在微信后台额外声明，避免审核认为声明与实际功能不一致：

- `getUserProfile`
- `chooseMedia`
- `chooseMessageFile`
- `getLocation`
- `chooseLocation`
- `openLocation`
- `startLocationUpdate`
- `camera` 组件
- `recorder`
- 视频上传
- 音频上传

说明：工单位置、会议室位置、房源位置等均为业务字段展示或用户手动填写，不是微信设备定位接口采集。

## 微信后台建议填写内容

### 1. 手机号

使用目的：
用于小程序手机号快捷登录、租户联系人或物业员工身份匹配、预约联系与账号安全校验。

使用场景：
用户在账号管理页点击“手机号快捷登录”，或在会议、看房预约提交前验证手机号时使用。

### 2. 姓名/联系人信息

使用目的：
用于工单报修、访客预约、看房预约、会议预约等物业服务办理和联系确认。

使用场景：
用户提交工单、访客预约、看房预约、会议预约时使用。

### 3. 选中的照片或视频信息

使用目的：
用于工单报修时上传现场照片，帮助物业人员判断故障、维修或服务事项。

使用场景：
用户在“我的工单/工单报修”页面选择或拍摄现场照片并提交工单时使用。

说明：当前代码实际使用 `wx.chooseImage` 选择或拍摄照片，没有 `chooseMedia`、视频上传或音频上传。微信后台如以“选中的照片或视频信息”作为统一信息类型，可按该项声明。

### 4. 用户生成内容

使用目的：
用于用户资料、工单文本描述、预约备注和现场图片等用户主动提交内容的物业服务办理、记录留存与服务跟进。

使用场景：
用户提交个人资料、工单、访客预约、会议预约、看房预约或填写备注时使用。相关用户生成内容采用人工审核把关。

## 小程序前端协议入口

账号管理页已增加底部协议勾选：

- 未勾选时，不渲染真实 `getPhoneNumber` 授权按钮，只提示用户先阅读并同意协议。
- 勾选后，显示“手机号快捷登录”按钮。
- 《德汇创新中心用户服务协议》打开本地页面：`/pages/agreements/service/index`。
- 《用户隐私保护指引》调用 `wx.openPrivacyContract` 打开微信后台审核通过的隐私保护指引。

## 后端生产环境要求

手机号快捷登录依赖服务端调用微信接口，需要在生产环境配置真实值：

```text
WECHAT_MINIPROGRAM_APP_ID=小程序 AppID
WECHAT_MINIPROGRAM_APP_SECRET=小程序 AppSecret
```

后端配置映射位置：
```text
dehui-property-management/src/main/resources/application.yml
```

接口路径：
```text
POST /api/mobile/auth/wechat-phone-login
```

请求字段：
```json
{
  "phoneCode": "微信 getPhoneNumber 返回的 code"
}
```

返回沿用现有移动端登录态结构：

```json
{
  "token": "...",
  "identity": "PUBLIC",
  "profile": {
    "mobileUserId": 1,
    "phone": "13800000000",
    "userType": "PUBLIC"
  }
}
```

手机号快捷登录只创建或复用移动用户，并由服务端按已验证手机号匹配真实业务身份：没有匹配时保持普通用户，唯一匹配时自动进入该身份，多项匹配时进入身份选择。客户端不能自行绑定或伪造租户、员工身份。

## 登录前置页审核文案

- 调用 `getPhoneNumber` 的前置页和按钮统一使用“手机号快捷登录”“验证手机号”等中性产品文案，不展示“微信”“官方接口”等平台背书式文字，也不使用平台官方 Logo。
- `open-type="getPhoneNumber"`、动态 `code`、`/mobile/auth/wechat-phone-login` 和服务端手机号解析保持不变；本项只约束用户可见文案，不改变真实授权能力。
- 用户服务协议、隐私保护指引和支付说明中的平台事实表述继续保留，避免法律说明与实际能力不一致。

## 审核前测试步骤

1. 微信公众平台后台按本清单填写并提交《用户隐私保护指引》，等待审核通过。
2. 微信开发者工具清除缓存和授权数据。
3. 打开小程序 `pages/me/index`。
4. 未勾选协议时点击“手机号快捷登录”，应提示先阅读并同意协议，不应弹出手机号授权。
5. 点击《用户隐私保护指引》，应打开微信后台配置的隐私保护指引。
6. 勾选协议后点击“手机号快捷登录”，应弹出原生手机号授权。
7. 同意授权后，应请求 `/api/mobile/auth/wechat-phone-login`，并按服务端匹配结果保持普通用户、进入唯一身份或进入身份选择。
8. 打开“我的工单”选择图片，应先出现隐私授权说明，再进入选图。
9. 打开“我的账单”下载发票、“电子合同”查看合同，应通过统一下载封装完成，不出现定位、录音、视频或文件选择授权。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
