# 数据工作台关联字段契约

## 范围

本阶段只定义前端通用字段类型和只读展示契约，不新增数据库表、接口或设备关联数据。

## 字段配置

```js
{
  key: 'building',
  label: '关联楼宇',
  type: 'relation',
  relation: {
    entityType: 'building',
    idField: 'buildingId',
    labelField: 'buildingName',
    multiple: false
  }
}
```

`CellRenderer` 接受单个对象、对象数组或标量值，并统一转换为：

```js
{
  id: 12,
  label: 'A座',
  entityType: 'building'
}
```

## 后续接入边界

- 页面负责提供真实接口返回的关联值，不由 `CellRenderer` 发起请求。
- 查询候选项、建立或解除关联需要业务模块提供明确 API 后再接入。
- `relation` 当前不属于后端自定义字段可持久化类型，字段编辑器保持只读。
- 后续可以在字段配置中增加候选项加载器、详情路由和权限 code，但不得在公共组件中写死楼宇、房间、租户或工单接口。

## 2026-07-24 记录更新

- 已修复登录提交适配：dehui-property-admin/src/views/Login.vue 改为提交 JSON Body（username/password），并指定 Content-Type: application/json，避免后端 HttpMessageNotReadableException。
- 已新增演示数据脚本：dehui-property-management/scripts/seed_demo_building_tenants_energy.sql（1 个楼宇、3 层、3 间房、3 家租户、6 个表具、18 条近三月读数）用于楼宇/租户水电联动演示。
