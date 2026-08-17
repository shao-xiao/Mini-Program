# DHIC 招商公告移动端闭环（2026-07-26）

## 1. 结论

本轮固定四条数据链路：

1. `investment_display`（招商展示）驱动小程序招商中心上半部分。
2. `investment_room_listing.room_id -> building_room.id`（招商房源）驱动房源列表与详情。
3. `investment_lead.mobile_user_id + listing_id`（招商线索）记录当前微信用户的预约看房。
4. `notice`（公告管理）驱动小程序首页轮播、公告列表和详情。

招商展示、招商房源、招商线索、园区公告不互相代替。历史 `investment_display.section_key = 'notice'` 记录不删除，V46 只将已发布记录停用。

## 2. 差异矩阵

| 业务能力 | 当前数据表 | 管理接口 | 后台页面 | 小程序页面 | 本轮主要修复 | 兼容风险 |
| --- | --- | --- | --- | --- | --- | --- |
| 招商展示 | `investment_display` | `/investment/contents` | 招商管理/招商展示 | 招商中心顶部与内容区 | 移除 notice 区块；增加公开范围、有效期、单区块唯一发布、统一图片上传 | 保留旧 overview/content 路由别名 |
| 招商房源 | `investment_room_listing`、`investment_room_image`、`building_room` | `/investment/listings` | 招商管理/招商房源 | 房源列表、详情 | 房源只保存 `room_id` 与营销字段；面积快照来自 `building_room.area`；租赁/合同/房间状态共同决定公开和预约资格 | 保留 `/mobile/investment/rooms` 查询别名 |
| 招商线索 | `investment_lead`、`investment_follow_record`、`investment_lead_status_history` | `/investment/leads` | 招商线索 | 预约表单、我的预约 | 从技术会话取得 `mobile_user_id` 和已验证手机号；保存房源快照；限制重复提交；状态与跟进留痕；转化不创建租户/合同 | 保留 `/mobile/investment/leads` 兼容路由，但不再匿名 |
| 园区公告 | `notice` | `/announcements` | 公告管理 | 首页轮播、公告列表、详情 | 增加摘要、封面、轮播排序、有效期、可见范围；公开查询统一过滤 | 旧公告类型迁移为四种标准类型 |

## 3. 数据规则

### 招商展示

- 区块：`hero`、`introduction`、`highlight`、`policy`、`contact`、`location`。
- 同一区块最多一条 `PUBLISHED` 记录；新发布会停用同区块旧记录。
- 小程序只读取已发布、公开、当前有效、未删除记录，顺序由 `sort_order` 返回。

### 招商房源

- 楼宇、楼层、房号、面积、房型和出租状态实时来自真实 `building_room` 关系。
- `display_area_snapshot` 仅记录发布/预约时展示面积，不能替代 `room_id`。
- 房间停用、非空置、有未结束有效租赁或有效合同占用时，不进入公开列表且不能预约。
- 发布还要求房源图片、营销发布状态、有效期和预约开关满足条件。

### 招商线索

- 小程序请求不接受可信 `mobileUserId`、`openid`、`tenantId`、`sysUserId`、`roomId` 或手机号。
- 服务端从 token principal 取得 `mobile_user_id`，从 `mobile_user.phone` 取得已验证手机号。
- `listingId` 必须是当前已发布且可预约的房源。
- 保存标题、楼宇、楼层、房号、面积快照。
- 状态：`NEW -> FOLLOWING/VIEWING_SCHEDULED/VIEWED/CONVERTED/INVALID`；`CONVERTED`、`INVALID` 为终态。
- 跟进记录和状态历史单独保存；同用户同房源 60 秒内重复提交返回 429。

### 园区公告

- 类型：`NOTICE`、`ACTIVITY`、`PROPERTY`、`REMINDER`。
- 首页轮播仅包含已发布、公开、当前有效、未删除且 `show_on_home = 1` 的公告。
- 轮播按 `carousel_sort`、发布时间、ID 排序；列表按发布时间和 ID 排序。

## 4. 移动端接口

| 方法 | 接口 | 访问边界 |
| --- | --- | --- |
| GET | `/mobile/investment/showcase` | PUBLIC/TENANT/STAFF 公开读取 |
| GET | `/mobile/investment/listings` | PUBLIC/TENANT/STAFF 公开读取 |
| GET | `/mobile/investment/listings/{id}` | PUBLIC/TENANT/STAFF 公开读取 |
| POST | `/mobile/investment/appointments` | 有效微信技术会话、已验证手机号、PUBLIC/TENANT；STAFF 不开放预约 |
| GET | `/mobile/investment/appointments/mine` | 仅当前 `mobile_user` |
| GET | `/mobile/investment/appointments/{id}` | 仅当前 `mobile_user` |
| GET | `/mobile/announcements` | 公开有效公告 |
| GET | `/mobile/announcements/{id}` | 公开有效公告详情 |
| GET | `/mobile/announcements/carousel` | 首页有效轮播公告 |

## 5. Migration

- 版本：`V46__close_investment_announcement_mobile_loop.sql`
- 开发库：2026-07-26 已由 Flyway 从 V44 顺序应用 V45、V46，当前 schema 为 V46。
- 未连接或操作生产数据库。
- Migration 只追加字段、索引、状态历史表和兼容数据更新；没有 `DELETE`、`TRUNCATE`、`DROP TABLE`。

## 6. 验证记录

- 后端 `mvn test`：396 项，0 失败，0 错误，11 项既有 MySQL 环境门控测试跳过。
- 后端 `mvn -DskipTests compile`：通过。
- 后端 `mvn -DskipTests package`：通过；本地 8080 使用新 JAR 重启成功。
- 管理后台 `pnpm run build`：通过。
- 管理后台 `pnpm run test:components`：8 个文件、29 项测试全部通过。
- 小程序 `node --test tests/*.test.js`：84 项全部通过。
- 本地接口实测：showcase、listings、announcements、carousel 均返回 200。
- 浏览器实测：招商展示、招商房源、真实房间选择器、招商线索、公告管理页面均可进入；字段和操作符合闭环模型，控制台未发现持续错误。
- 微信开发者工具/真机手机号授权未在本轮自动化环境执行；不得据此宣称真机预约已验收。

## 7. 最终验收收尾

- V46 已在现有 `dehui_property_dev` 和临时全新 MySQL 8.4 数据库验证；两条路径均由 Flyway 执行至 46 且 history success=1，临时库随后删除。
- 浏览器创建并发布六类招商展示；停用/恢复区块会实时影响公开接口，且每个 section 仅一条已发布记录。
- 房源通过后台选择 `building_room.id=1`，楼宇、楼层、房号、面积、类型、出租状态由资产关系读取；房间 RENTED、DISABLED 或有效合同占用时公开列表和详情均不可用，恢复后重新可见。
- 公告已验证发布、轮播、列表、详情、停用、取消轮播、过期和软删除；招商展示和房源未进入公告接口。
- 图片已验证单图、多图、追加、缩略图、大图预览、编辑回显、单张删除和 URL 访问；伪图片上传返回 400 且不产生记录。
- 权限已用临时只读/无权账号验证菜单、路由、按钮和后端 403；测试账号与关联记录已清理。
- 最小修复：房源图片由逐房源查询改为批量查询；图片上传增加扩展名与 MIME 类型一致性校验。
- 最终回归：后端 398 项、0 失败、0 错误、11 项环境门控跳过，compile/package 通过；管理后台 29/29、build 通过；小程序 84/84。
- 微信开发者工具已经打开项目，但本地后端没有 AppID/AppSecret，工具服务端口也未启用，因此真实 `wx.login -> getPhoneNumber`、两个真实微信用户隔离和由真实预约驱动的后台线索状态操作仍未完成。未使用文本手机号或直接写库冒充验收。
