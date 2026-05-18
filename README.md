# DeHui 德汇创新中心物业管理系统

## 1. 项目简介

DHIC 德汇创新中心物业管理系统，是一套面向写字楼、园区、创新中心等物业场景的综合运营管理平台。

系统围绕德汇创新中心的实际运营需求，覆盖资产管理、租赁管理、合同管理、财务收费、停车管理、能耗管理、运营管理、AI日报和系统权限管理等核心业务模块，目标是建设一套可持续扩展、可实际运行、可逐步智能化升级的物业数字化平台。

当前版本定位为：

```text
v1.0 可运行版
```

已完成前后端基础闭环，具备演示、测试和继续迭代开发的基础。

---

## 2. 项目目录结构

```text
/opt/projects
├── dehui-property-admin
├── dehui-property-management
└── README.md
```

---

## 3. 技术架构

### 前端

- Vue3
- Vite
- Element Plus
- Vue Router
- axios

目录：

```text
/opt/projects/dehui-property-admin
```

### 后端

- Spring Boot
- Spring Data JPA
- H2 Database
- Maven
- Java 21

目录：

```text
/opt/projects/dehui-property-management
```

---

## 4. 已完成功能模块

### 资产管理

- 楼宇管理
- 楼层管理
- 房间管理
- 设备台账

### 租赁管理

- 租户管理
- 租户入驻
- 合同台账

### 财务管理

- 账单管理
- 收费规则
- 财务统计

### 停车管理

- 车位管理
- 车位绑定
- 停车账单

### 能耗管理

- 能耗抄表
- 能耗统计

### 运营管理

- 工单管理
- 巡检管理
- 访客管理
- 公告管理

### AI分析

- AI运营日报

### 系统管理

- 用户管理
- 角色管理
- RBAC基础权限

---

## 5. 权限系统

当前已实现：

- 登录认证
- Token机制
- 用户角色管理
- 前端菜单权限控制
- 路由访问拦截

当前角色：

| 角色 | 说明 |
|---|---|
| ADMIN | 系统管理员 |
| MANAGER | 运营经理 |
| STAFF | 普通员工 |
| SECURITY | 安保人员 |
| CLEANER | 保洁人员 |
| FINANCE | 财务人员 |

---

## 6. UI设计

系统采用 DHIC 品牌化风格：

- 红 + 黑 + 灰
- 深色侧边菜单
- DHIC Logo
- 企业级后台风格

主题文件：

```text
src/styles/dhic-theme.css
```

---

## 7. 前端开发规范

### 请求规范

所有请求必须通过：

```text
src/utils/request.js
```

统一接口前缀：

```text
/api
```

### 日期规范

统一：

```text
value-format="YYYY-MM-DD"
```

### 开发原则

- 不允许 mock 数据
- 前端必须适配真实后端接口
- Continue 只能修改前端
- 后端修改必须人工确认

---

## 8. 后端接口规范

统一返回结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

认证方式：

```text
Authorization: token
```

当前不使用 Bearer 前缀。

---

## 9. 前端运行

```bash
cd /opt/projects/dehui-property-admin

npm install

npm run dev
```

默认地址：

```text
http://localhost:5173
```

---

## 10. 后端运行

```bash
cd /opt/projects/dehui-property-management

mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

---

## 11. 当前版本状态

当前版本：

```text
v1.0 可运行版
```

当前已完成：

- 前后端基础闭环
- 主要业务模块
- RBAC基础权限
- DHIC品牌UI
- 停车与能耗模块

---

## 12. 后续规划

### 第二阶段

- 登录返回真实角色
- 动态菜单
- 按钮级权限控制
- Dashboard升级

### 第三阶段

- H2 → MySQL
- Docker部署
- Nginx
- CI/CD

### AI增强

- AI财务分析
- AI能耗分析
- AI工单分析

---

## 13. Git管理

推荐使用统一仓库：

```text
/opt/projects
├── dehui-property-admin
├── dehui-property-management
└── README.md
```

GitHub：

```text
https://github.com/jwgit2467/dehui-property-vscode
```

---

## 14. 项目定位

DHIC 系统长期目标：

```text
园区 / 写字楼综合物业运营平台
```

核心方向：

- 数字化
- 智能化
- AI化

---

## 15. 项目说明

当前系统已经具备：

- 可运行
- 可演示
- 可继续迭代开发

适合作为：

- 企业内部物业平台
- 园区管理平台
- 智慧物业基础系统

---

## 16. 技术栈

### 前端

```text
Vue3 + Vite + Element Plus
```

### 后端

```text
Spring Boot + JPA + H2
```

### 当前版本

```text
v1.0
```
