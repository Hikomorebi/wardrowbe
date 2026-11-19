# 个人衣橱部署与维护

正式域名为 `https://cd.hikomorebi.site`，仅使用邮箱密码登录。2026-11-19 已完成备案并启用正式 HTTPS 访问，网站备案号为 `蜀ICP备2026072570号-1`。电脑直接访问域名，[SSH 私有访问](private-access.md) 保留作维护备用。衣物成色为优秀、良好、一般、较差；存放位置由本人维护；搭配手动保存。首页提供衣物、我的搭配、类别管理、位置管理、设置。[原生安卓客户端](android-app.md) 位于 `android/`，登录页服务器地址使用同一 HTTPS 域名，与网页共用账号和数据。

## 首次安装

在服务器终端执行：

```bash
cd /home/hkb/Projects/wardrowbe
sudo bash deploy/install-docker.sh hkb
```

重新登录终端以取得 docker 组权限。服务器当前通过本机 `127.0.0.1:7897` 代理访问镜像仓库；若直接拉取失败，执行 `sudo bash deploy/configure-docker-proxy.sh`。代理必须持续可用，Docker 服务不会自动继承终端代理变量。

确保域名解析指向服务器，云安全组和主机防火墙允许 TCP 80/443。保留原有 SSH 端口。应用的私有入口仅映射到 `127.0.0.1:18080`，经 SSH 转发使用；数据库和 Redis 不映射宿主机端口。

```bash
bash deploy/deploy.sh
docker compose --env-file deploy/.env.server -f deploy/compose.server.yml exec backend python -m app.manage create-user
```

全新数据库创建账号时交互输入邮箱、显示名称和密码。密码长度为12～128个字符。首次账号建议显示名称 `hekunbin`、邮箱 `hekunbin@163.com`。账号默认中文、上海时区，跳过天气和家庭引导；不提供公开注册。

`deploy/prepare.py` 仅在不存在时创建权限为0600的 `deploy/.env.server`，内含随机数据库密码、签名密钥、域名及镜像版本。该文件不提交Git。不要在已有数据库上重新生成数据库密码。

容器保留现有图片处理Worker，内部AI默认关闭。前后端从本仓库构建，镜像标签使用 `APP_VERSION`，构建按顺序执行。Node.js24和Python3.11由镜像提供，不依赖服务器的Python版本。

## 日常检查

2026-11-19 首页入口修复：登录检查、首次引导与个人衣橱跳转按顺序选择一个目标，避免未登录时多个跳转互相中断而只剩页脚。已从正式 HTTPS 域名在 Chromium、Firefox 中验证：首页、`/dashboard`、个人衣橱地址未登录时进入登录页，已登录时进入衣橱。

```bash
docker compose --env-file deploy/.env.server -f deploy/compose.server.yml ps
docker compose --env-file deploy/.env.server -f deploy/compose.server.yml logs --tail 100 backend frontend caddy
curl -fsS https://cd.hikomorebi.site/api/v1/health/ready
```

Caddy负责申请及自动续期证书。首次签发需要域名解析正确、80/443公网连通。数据库、Redis、照片和证书各自使用持久化卷。容器自动重启，日志按10MB、3份轮转。

## 密码重置

用户可在设置中修改密码；忘记密码时执行：

```bash
docker compose --env-file deploy/.env.server -f deploy/compose.server.yml exec backend python -m app.manage reset-password
```

重置后所有旧本地登录凭据失效，需要重新登录。正常登录有效期为7天。

## 备份与恢复

```bash
bash deploy/backup.sh
sudo bash deploy/install-backup-timer.sh hkb
```

定时器每天北京时间03:30执行备份。备份短暂停止后端及Worker写入，依次导出数据库、照片和配置，完成后恢复服务。脚本失败也会恢复原运行的服务，保留最近7个完整快照。备份目录权限受保护，数据库密码和签名密钥包含在快照中。

备份仅保存在当前服务器，无法覆盖整台服务器丢失的情况。可另行将完整快照复制到个人电脑或异地存储。

可先在隔离的临时数据库中验证备份，不改动正式环境：

```bash
bash deploy/verify-backup.sh /absolute/path/to/backup
```

恢复会覆盖现有数据库和图片，应先保存当前快照：

```bash
bash deploy/backup.sh
bash deploy/restore.sh /absolute/path/to/backup --replace-current-data
```

恢复过程中失败会保持写入服务停止，便于排查。全新服务器恢复时，先把快照内 `env.server` 复制为 `deploy/.env.server` 并设为0600，恢复对应部署配置与镜像，然后执行恢复脚本。现有服务器需确保快照与当前部署的数据库账号密码一致。

## 升级与回退

1. 先执行备份并记录当前 `APP_VERSION`。
2. 更新源码，把 `.env.server` 的 `APP_VERSION` 改为新版本标签，保留已有密钥和密码。
3. 执行 `bash deploy/deploy.sh`，验证登录、图片、衣物编辑、位置及搭配。
4. 兼容数据库的应用回退：恢复旧 `APP_VERSION` 后执行Compose `up -d --no-build`；数据库结构不兼容时，使用旧部署配置、镜像及发布前完整快照恢复。

不要清理仍用于回退的旧镜像，也不要执行带 `-v` 的Compose停止命令。新增迁移保持单一head；本地账号存在时，禁止向不支持本地认证的上游版本直接降级。

## 开发验证

后端：`ruff check .`、`ruff format --check .`、`pytest`、`python scripts/check_migration_heads.py`。后端测试必须设置独立的 `TEST_DATABASE_URL` 和测试Redis，不能使用生产数据库。

前端：`npm ci`、`npm run typecheck`、`npm run lint`、`npm test`、`npm run i18n:check`、`npm run vocab:check`、`npm run build`。前端升级到Next.js15的修复版本，并更新登录及图片依赖；构建执行类型检查。

## API约定（供后续安卓客户端使用）

- `POST /api/v1/auth/login`：JSON提交 `email`、`password`，返回用户信息、`access_token`、`token_type` 和 `expires_in`；后续请求使用 `Authorization: Bearer <access_token>`。
- `POST /api/v1/items`：multipart提交 `image`、`category_id` 及可选 `name`、`condition`、`storage_location_id`、`notes`；多个季节使用重复的 `season` 表单字段。
- `PATCH /api/v1/items/{id}`：JSON更新字段，`category_id: null`、`condition: null`、`storage_location_id: null` 清空对应值，`season: []` 清空季节。
- `condition` 取值为 `excellent`、`good`、`fair`、`poor`，与原有系统处理字段 `status` 分开。季节使用 `spring`、`summer`、`fall`、`winter` 四个值。个人模式下，旧客户端传入的 `all-season` 会展开为四个季节。
- `GET /api/v1/items` 支持 `category_id`、`condition`、`storage_location_id`、`season` 及搜索和分页参数。衣物响应包含 `category_id` 和 `category_name`，搭配中的衣物也返回对应类别。
- `/api/v1/categories` 提供列表、新增；`/api/v1/categories/{id}` 提供改名、删除，返回 `id`、`name`。引用中的类别删除返回409。
- `/api/v1/locations` 提供列表、新增；`/api/v1/locations/{id}` 提供改名、删除，返回 `id`、`name`。引用中的位置删除返回409。
- 手动搭配复用 `/api/v1/outfits/studio`、`/api/v1/outfits/{id}`；`GET /api/v1/outfits?item_id=...&source=manual&is_lookbook=true` 反查包含该衣物的手动搭配。引用中的衣物永久删除返回409，可先解除搭配引用或归档。
- 账号只由服务器命令创建，登录有效期7天；401时客户端重新登录。本次未引入刷新令牌或安卓客户端工程。

## 本次上线验证（2026-11-15）

- 服务器本机HTTPS与证书验证通过；公网访问目前受到腾讯云备案拦截（见下文诊断），不能以本机结果代替外部验收。首个账号已初始化，初始凭据位于服务器用户私有文件 `/home/hkb/.local/share/wardrowbe/initial-login.json`，权限0600。登录后可在设置中修改密码。
- 后端2432项测试全量通过；前端全量测试及新增类别、修复缓存键后的定向回归通过（累计859个不同用例）。Ruff、TypeScript、多语言及共享数据检查通过，ESLint无错误。
- 生产前端依赖审计结果为0个已知漏洞；前后端生产镜像构建成功。
- 真实浏览器在服务器侧完成账号登录、照片上传、成色/位置/季节保存与编辑、筛选、手动搭配保存与反查、删除保护和清理操作；浏览器未记录脚本错误。首页不会再触发天气查询。
- 带图片的备份已恢复到隔离PostgreSQL15数据库，全部3个关联图片文件验证通过；正式环境验收账号及数据已清理，保留真实账号。
- 已完成干净的正式备份及隔离恢复验证。定时器 `wardrowbe-backup.timer` 已启用，每天北京时间03:30运行；当前快照保存在 `deploy/backups/`，镜像记录保存在 `deploy/releases/`，均不提交Git。


## 类别与季节

在网页的“类别管理”中新增、改名和删除类别。类别名称直接存入数据库，使用简体中文；初次迁移和创建新账号时会复制一份默认类别，后续不会覆盖自定义名称或补回已删除类别。初始类别见 `backend/app/data/default_categories.json`，修改该文件仅影响之后创建的账号，已有账号请通过网页管理。

衣物可选择或清除类别，列表可按类别筛选；类别改名会同步显示在衣物和已保存的搭配中。正在被衣物使用的类别（含已归档衣物）不能删除，需要先调整或清除这些衣物的类别。界面不再提供子类别字段。

适合季节仅提供春、夏、秋、冬，可多选。升级会将旧“四季”展开为四个季节全选，同时保留衣物的其他字段与搭配关联。

衣物卡片只提供一个“推荐搭配”入口，按本人已保存的手动组合查找配套衣物；结果中当前衣物单独展示，各组合展示其余配套衣物并可打开衣物详情。创建、编辑和删除组合统一在“我的搭配”中进行。卡片的成色、位置和季节分组展示，季节使用带图标的彩色标签，推荐、归档/恢复与删除操作使用明确的按钮样式。


## 公网连接被重置的排查记录（2026-11-15）

当前云服务器地域为 `ap-chengdu`。从服务器本机直接访问域名可返回 HTTP 200，证书验证成功，但经外部代理访问同一域名时，HTTPS 在握手阶段提前断开。HTTP 请求实际返回以下响应，来自腾讯云的备案拦截链路：

```text
HTTP/1.1 302 OK
Location: https://dnspod.qcloud.com/static/webblock.html?d=cd.hikomorebi.site
```

腾讯云拦截页查询接口对 `hikomorebi.site` 和 `cd.hikomorebi.site` 均返回 `GovStatus: false`、`LandedStatus: false`；对应提示为“您的网站未完成备案”。应用容器、数据库、HTTPS 证书均正常，修改应用代码或重新签发证书无法解除这项拦截。

继续使用当前服务器对外提供域名访问，需要通过腾讯云完成备案；也可选择迁移到可用的境外服务器，或将个人衣橱改为仅通过私有网络访问。备案提交涉及本人的实名资料，由服务器和域名持有人在腾讯云控制台办理。说明见[腾讯云备案概述](https://cloud.tencent.com/document/api/243/18907)。

后续发布必须从用户电脑或独立外部网络验证登录页和 API，不能将服务器访问自身公网 IP 的结果作为公网可用的依据。故障排查时，可对比 HTTP 是否被跳转到备案页，以及 HTTPS 是否在到达应用前断开。

已选择保留成都服务器，备案暂缓。目前通过 [SSH 私有访问](private-access.md) 使用衣橱，公开入口通过 `SITE_CLOSED_FOR_FILING=true` 暂停，数据和每日备份保留。备案资料草稿、官方办理入口和通过后的恢复命令见 [ICP备案办理](icp-filing.md)。实际备案号保存在私有配置 `ICP_RECORD_NUMBER`，网页底部仅在该项填写后展示真实号码及工信部链接。
