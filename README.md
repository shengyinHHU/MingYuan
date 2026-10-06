# 数据库与安全边界

## 项目和数据库位置

协作仓库：`shengyinHHU/MingYuan`；本次上传分支：`DataMarketplacebyydh`；资料商城版本：`1.0`。

以下绝对路径是 我 的 Mac 本地位置，其他协作者克隆后应使用自己的目录，不需要照搬这些路径。

| 内容 | 位置 |
| --- | --- |
| 本机开发项目根目录 | `/Users/philo/Documents/HHU/小程序/MingyuanMiniAPP/MingyuanMiniAPP/` |
| 微信小程序源码 | 根目录下 `WeChatMiniApp/`，用微信开发者工具导入此目录 |
| 若依后端源码 | 根目录下 `RuoYi-Vue-master/`，入口模块为 `ruoyi-admin` |
| 若依网页前端源码 | 根目录下 `RuoYi-Vue-master/ruoyi-ui/` |
| 数据库脚本目录 | `/Users/philo/Documents/HHU/小程序/MingyuanMiniAPP/MingyuanMiniAPP/database/`；仓库相对路径为 `database/` |
| 本机 MySQL 连接 | `127.0.0.1:3306`，数据库名 `mingyuaneduminiapp` |
| 本机 MySQL 数据文件目录 | `/opt/homebrew/var/mysql/`，由 MySQL 管理，不要直接复制或修改数据文件 |
| 后端数据库连接配置 | `RuoYi-Vue-master/ruoyi-admin/src/main/resources/application-druid.yml` |

## 数据库使用边界

- `database/schema.sql`：当前 48 张表的结构，不包含业务数据。仅用于新建的空数据库。
- `database/seed.sql`：基础菜单、角色、字典、系统配置及虚构演示账号。只在空库导入表结构后执行，不得覆盖现有数据库。演示账号为 `admin`、`teacher_demo`，密码均为 `123456`，仅供本机测试。
- `database/migrations/20261004_material_shop.sql`：已有旧版数据库的资料商城迁移脚本。执行前备份，先核对结构和历史订单；涉及关闭历史未支付订单时，须人工审核并按脚本提示批准。新建库导入当前表结构后，不再执行该迁移。
- `database/Dump20260924.sql`：仅四张历史表的结构，用于迁移自动化测试，不包含原始行数据，也不是业务数据库备份。

资料商城复用 `sys_user`、`sys_role`、`sys_user_role` 和现有权限体系，不另建商城用户表或角色表；扩展 `edu_material`、`edu_material_order`，增加商品图片、订单明细、支付记录、发货记录、订单日志、收货地址及迁移记录表。

本次新增的数据库脚本不包含真实学生、教师、收货地址、电话、订单或支付数据。协作仓库原有的表格、旧 SQL、压缩包和历史提交未在本次上传中全面审计，不能因为有脱敏初始化脚本就认为整个仓库历史均已脱敏。

## 安全边界

- `TOKEN_SECRET` 必须通过环境变量设置；数据库账号、密码使用 `MYSQL_USER`、`MYSQL_PASSWORD`，正式微信配置使用 `WECHAT_APPID`、`WECHAT_SECRET`。不要把真实密钥或密码提交到仓库。
- 默认关闭模拟微信登录。仅使用 `--spring.profiles.active=druid,local` 在本机演示时，启用模拟角色登录和模拟支付；不允许在生产环境启用 `local`。开发工具 AppID 为占位测试号，需要替换成有开发权限的 AppID。
- 当前支付只模拟流程，不会实际扣款；未实现真实微信支付的生产接入。演示密码必须在对外部署前更改，不能直接上线使用演示数据库。
- 本机后端默认 `8080`、Redis 默认 `127.0.0.1:6379`；开发工具中的 `127.0.0.1` 仅指本机，真机调试需要可达地址。对外部署还需 HTTPS、合法域名、独立最小权限数据库账号及正式支付实现。
- 数据库自动化测试会重建专用库 `codex_material_shop_test_20261004`，不得在该库保存业务数据。本次上传不提交依赖、构建产物、上传文件或内部开发 `.md` 文档。
