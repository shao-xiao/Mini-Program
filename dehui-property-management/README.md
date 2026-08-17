# 德汇创新中心物业管理系统后端

## 架构定位

后端采用 Java 21 + Spring Boot 3 模块化单体架构，作为小程序和后台管理端唯一业务规则入口。

```text
HTTPS / Nginx
  -> Spring Boot 3 API
  -> MySQL 正式业务数据库
  -> Redis 登录状态、验证码、权限缓存、首页统计缓存、限流、临时锁
```

## 技术栈

- Java 21
- Spring Boot 3.2.5
- Spring Data JPA
- Flyway
- MySQL 8
- Redis
- Maven

## 目录结构

```text
src/main/java/com/dehui/property
  common
  config
  security
  modules
    system
    mobile
    building
    tenant
    contract
    bill
    meeting
    parking
    workorder
    visitor
    investment
    checkin
    file
    log
    notice
```

## 配置

- `application.yml`：公共配置
- `application-dev.yml`：开发环境 MySQL/Redis 配置
- `application-prod.yml`：生产环境变量配置
- `db/migration/V1__baseline_schema.sql`：生产级 MySQL 基线表结构

生产环境不要把数据库密码、Redis 密码、微信 AppSecret 写入前端或仓库代码；使用服务器环境变量注入。

## 验证

```powershell
mvn test
```

如果本机没有全局 Maven：

```powershell
& "$env:USERPROFILE/.cache/codex/apache-maven-3.9.9/bin/mvn.cmd" test
```

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
