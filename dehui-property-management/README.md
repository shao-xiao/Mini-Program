# 德汇创新中心物业管理系统

## 项目简介

德汇创新中心物业管理系统后端服务，采用 Java 21 + Spring Boot 3 + Maven 构建。

## 技术栈

- Java 21
- Spring Boot 3.2.5
- Spring Data JPA
- H2 Database (开发环境)
- Lombok
- Maven

## 项目模块

| 模块 | 说明 |
|------|------|
| user | 用户与权限管理 |
| building | 楼宇/楼层/房间/租赁面积管理 |
| tenant | 租户管理 |
| contract | 合同管理 |
| workorder | 工单管理 |
| equipment | 设备巡检 |
| energy | 能耗记录 |
| aiassistant | AI物业助手接口预留 |

## 开发环境

- H2 内存数据库（dev 环境）
- H2 Console: http://localhost:8080/api/h2-console
- 默认 JDBC URL: `jdbc:h2:mem:property_dev`

## 快速开始

### 编译项目

```bash
mvn compile
```

### 运行项目

```bash
mvn spring-boot:run
```

### API 访问

- 基础路径: http://localhost:8080/api
- H2 Console: http://localhost:8080/api/h2-console

## 主要 API

### 用户管理
- `GET /api/user/list` - 用户列表
- `POST /api/user/save` - 保存用户

### 楼宇管理
- `GET /api/building/list` - 楼宇列表
- `POST /api/building/save` - 保存楼宇

### 租户管理
- `GET /api/tenant/list` - 租户列表
- `POST /api/tenant/save` - 保存租户

### 合同管理
- `GET /api/contract/list` - 合同列表
- `POST /api/contract/save` - 保存合同

### 工单管理
- `GET /api/workorder/list` - 工单列表
- `POST /api/workorder/save` - 保存工单

### 设备管理
- `GET /api/equipment/list` - 设备列表
- `POST /api/equipment/save` - 保存设备

### 能耗管理
- `GET /api/energy/list` - 能耗记录列表
- `POST /api/energy/save` - 保存能耗记录

### AI助手
- `POST /api/ai-assistant/chat` - AI对话接口

## 下一步建议

1. 接入真实数据库（MySQL/PostgreSQL）
2. 添加 Spring Security 权限控制
3. 配置 Redis 缓存
4. 添加 Swagger API 文档
5. 编写单元测试
6. 配置 Docker 部署

## 配置文件

- `application.yml` - 主配置
- `application-dev.yml` - 开发环境配置
