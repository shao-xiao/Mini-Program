# 小屏表格浮动横向滚动条（2026-10-20）

## 需求与实现

小屏电脑查看宽表时，原生横向滚动条位于表格底部，必须先向下滚动才能拖动。为共享 WorkspaceDataGrid 增加 FloatingTableScrollbar，能耗表具、抄表等已有表格自动获得此能力，不修改业务列、固定列或操作按钮。

表格横向溢出且其底部在屏幕下方时，在可视区底部12px显示轻量滚动条。表格未进入视口、没有横向溢出、被隐藏，或原生底部滚动条已进入视口时自动隐藏。可拖动滑块、点击左右箭头，或聚焦滚动条后使用左右/Home/End键。原表格、表头与浮动滚动条双向同步。

浮动条按表格实际可见左右边界定位，不覆盖左侧导航；宽屏显示操作提示，窄屏隐藏提示保留操作空间。仅进行滚动及位置写入，不触碰业务数据或配置。

使用 requestAnimationFrame 合并窗口和祖先滚动事件，ResizeObserver 监测表格尺寸、列宽与表体变化；切换数据及列时重新绑定，卸载时断开观察并清理监听与待执行帧。

## 验证

- FloatingTableScrollbar、WorkspaceDataGrid、WorkspaceAccessibility 组件检查共14项通过，覆盖显示/隐藏条件、拖动事件双向同步、箭头/键盘、卸载清理及既有表格行为。
- 全部13个组件测试文件、48项检查通过（包含20/100/200行与50自定义字段性能回归）；完整生产构建、隔离生产基线共享组件构建和git diff --check通过。默认并行测试首次触及Node进程2GB堆限制；使用NODE_OPTIONS=--max-old-space-size=4096、--maxWorkers=1后全部通过，未更改测试断言或项目运行配置。
- 线上1024×768，tenant-3表具11行：原滚动条尚在屏幕下方时显示浮动条；点击右箭头后表体/表头/滑块均493px，实际拖动后均1041px，End后均1236px，右侧列正常可见，固定操作列保留。
- 纵向滚到原生滚动条进入视口后浮动条隐藏（表格底部660px，视口768px）；无页面横向溢出，无浏览器error/warn。验收后恢复默认视口并关闭临时标签页。

## 发布

仅增量发布共享组件 WorkspaceDataGrid-floating-scroll-20261020-v1.js/.css，index.html importmap 将原WorkspaceDataGrid-BN2nLRmy.js指向新版。共享组件44个公开导出别名保持不变，运行时继续复用生产Vue、Element Plus、权限及请求模块，原95个前端文件SHA256保持不变。

未修改后端、数据库、授权或用户的列/视图配置。备份 /opt/dehui/backups/floating-table-scrollbar-20261020-154203，恢复index.html即可回滚。

验收入口 /assets/energy?tab=meters&subject=tenant-3&ui=floating-scroll-20261020。
