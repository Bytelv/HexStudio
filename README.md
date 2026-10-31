# LvbyteStudio

字节君的博客工作台：**Android APK + Cloudflare Worker/D1 + Hexo/Stellar 站点改造**。

用手機管理 `blog.lvbyte.top` 的 Hexo 源码、发布说说、审批友链，并让网站直接读取 Cloudflare 上的数据。

---

## 1. 现在有什么

| 模块 | 位置 | 状态 |
|---|---|---|
| Cloudflare Worker API | `worker/` | **已部署**：<https://studio.4123010.xyz> |
| D1 数据库 | Cloudflare `lvbyte-studio-db` | 已建表，已迁移 34 条友链、11 条说说 |
| 朋友圈抓取 Cron | Worker `* * * * *` | 已运行，`/feeds` 已抓到真实文章 |
| Android APK | `android/` | Debug APK 已构建，单元测试通过 |
| 博客友链/说说/朋友圈 | `I:\MyBlogSource` | 已本地提交，**尚未 push**（push 即上线） |

线上接口：

```
GET  /health                 健康检查
GET  /friends                Stellar friends 标签兼容的友链 JSON
POST /friends/apply          友链申请（写通知表，APK 轮询提醒）
GET  /talks                  说说列表（公开）
GET  /feeds                  朋友圈文章（hexo-circle-of-friends 兼容结构）
POST /auth/github            GitHub Token → 90 天管理员会话
GET/POST/PATCH/DELETE /admin/*  管理端（好友、说说、通知、抓取）
```

Worker 的 `ADMIN_GITHUB_LOGIN` 为 `Bytelv`；只有这个 GitHub 账号能换到管理员会话。

---

## 2. 三个部分的职责

### Worker（`worker/`）

- **友链**：`/friends` 返回 Qexo v2 兼容结构，Stellar 的 `{% friends %}` 无需改渲染代码。
- **申请**：`/friends/apply` 做蜜罐、URL 校验、按 IP 限流，写入 `friend_applications` 和 `notifications`。
- **说说**：`/talks` 公开读取；APK 走 `/admin/talks` 发布，网站实时看到。
- **朋友圈**：Cron 每分钟处理一个友链源，自动发现 RSS/Atom → D1 → `/feeds`。
- **鉴权**：APK 用 GitHub Device Flow 拿 GitHub Token，再换 Worker 会话；会话只存哈希，有效期 90 天。
- **限速**：图床上传的 4 秒间隔由 APK 处理；Worker 侧友链申请按 IP 每天 5 条。

### 博客（`I:\MyBlogSource`）

- `source/friend/index.md`：友链 API 换成 Worker；申请表单改成自建表单。
- `source/friend/rss/index.md`：朋友圈 API 换成 `/feeds`。
- `source/talks/index.md`：新的 flomo 风格说说页。
- `source/timeline/index.html`：旧链接静态重定向到 `/talks/`。
- 菜单位置「说说」、页脚、robots 已同步。

### APK（`android/`）

- Kotlin + Jetpack Compose，minSdk 26 / targetSdk 35。
- GitHub OAuth Device Flow（也支持 PAT 备用），私有仓库可选。
- 文章/页面/配置浏览与 Markdown 编辑，front-matter 属性编辑。
- Stellar 标签插入面板、闭合校验、离线 HTML 预览（marked + DOMPurify）。
- 图床 API 上传（HTTP Basic），支持 Markdown 或 `{% image %}` 插入。
- 说说：flomo 卡片流、发布/编辑/删除、tgtalk JSON 导入（可选图片转存）。
- 友链：待审核、通过/拒绝、编辑、抓取状态、通知角标。
- WorkManager 每 15 分钟轮询通知；打开 App 立即同步。

---

## 3. 必须先做：创建 GitHub OAuth App

Device Flow 只需要 **Client ID**，不需要 Client Secret。

1. GitHub → Settings → Developer settings → **OAuth Apps** → New OAuth App。
2. 填写：
   - Application name：`Lvbyte Studio`
   - Homepage URL：`https://blog.lvbyte.top`
   - Authorization callback URL：`https://blog.lvbyte.top/`（Device Flow 不使用，但表单必填）
3. 创建后点 **Enable Device Flow**。
4. 复制 **Client ID**。

然后二选一：

- **不重新编译**：打开 APK 登录页 → 高级设置 → 填入 Client ID。它会存在本机 DataStore。
- **写进安装包**：把 Client ID 填到 `android/app/build.gradle.kts` 的 `GITHUB_CLIENT_ID`，重新构建。

> Client Secret 不要填进 App，也不要提交仓库；Device Flow 不需要它。

如果暂时不想建 OAuth App，可以在登录页选「使用 GitHub Token 登录」：  
Fine-grained token 至少需要 **Contents: Read and write**、**Metadata: Read**；Classic token 需要 `repo`。

---

## 4. 构建与安装 APK

前置：JDK 17、Android SDK Platform 35、Build-Tools 35。

```powershell
cd android
# local.properties 写 sdk.dir=C:\Users\lvbyte\AppData\Local\Android\Sdk
$env:JAVA_HOME="$HOME\.jdks\jdk-17.0.20.1+1"
.\gradlew :app:assembleDebug
```

产物：

```
android/app/build/outputs/apk/debug/app-debug.apk
```

单元测试：

```powershell
cd android
.\gradlew :app:testDebugUnitTest
```

首次使用：

1. 登录 GitHub，按提示在 GitHub 输入设备码。
2. 选择 `Bytelv/MyBlogSource`（私有库会显示锁图标）。
3. 设置 → 图床：填 `https://image.lvbyte.top/upload`、用户名、密码（凭证见博客仓库的 `AGENTS.md`，**不要提交到任何仓库**）。
4. 设置 → Cloudflare 服务：默认 `https://studio.4123010.xyz`，可测试连接。
5. 设置 → 通知：允许系统通知权限。

---

## 5. Worker 部署 / 维护

```powershell
cd worker
npm install
npx wrangler login
# schema 已经应用过；重建时执行：
npx wrangler d1 execute lvbyte-studio-db --remote --file=./schema.sql
npx wrangler deploy
```

`wrangler.toml` 已包含：

- D1 绑定 `DB`
- 路由 `studio.4123010.xyz/*`
- Cron `* * * * *`
- 变量 `SITE_URL`、`SITE_NAME`、`ADMIN_GITHUB_LOGIN`

可选 secret：

```powershell
npx wrangler secret put IP_SALT
```

用于友链申请限流的 IP 哈希加盐；不设置也有默认值。

### 数据备份

`worker/seed/friends.json` 是 2026-10-31 从 Qexo 导出的 34 条友链；`worker/seed/talks.json` 是从 tgtalk 整理出的 11 条说说。它们只是迁移快照，线上数据以 D1 为准。

### 朋友圈抓取

- Cron 每分钟取一个 `next_fetch_at` 到期的友链源。
- 先抓站点 HTML 发现 `<link rel="alternate" type="application/rss+xml">`，再试常见路径。
- 支持 RSS 2.0 / Atom；失败会记录 `last_error` 并退避。
- 某个站点一直失败时，在 APK 友链编辑里手动填 RSS 地址。
- 免费 Worker 计划下这个频率足够 30~40 个友链，约半小时轮完一轮。
- 2026-10-31 运行现状：34 个友链里 20 个已自动发现订阅，已抓约 320 篇文章；其余 14 个站点返回 403/404/526，需要在 APK 的友链编辑里手动填订阅地址。

---

## 6. 安全与凭证

- GitHub Token、Worker 会话、图床密码只存 **Android EncryptedSharedPreferences**（Keystore 加密）。
- Worker 会话只存 SHA-256 哈希；GitHub Token 不落 Worker 数据库。
- Worker 管理接口必须带 `Authorization: Bearer <session>`。
- 友链申请做蜜罐 + URL 校验 + IP 限流。
- 不要在仓库里提交：图床密码、OAuth Client Secret、GitHub Token、Worker 会话。
- 图床的 Telegram 存储约 20 条消息/分钟，APK 批量上传按 3.5 秒间隔；PNG 透明区域会被压成白底（已知限制）。

---

## 7. 已知限制 / 下一步

1. **模拟器运行被 BIOS 虚拟化挡住**  
   本机 `VirtualizationFirmwareEnabled=False`，AVD `LvbyteATD` 已创建，但 emulator 在软件加速下启动即崩溃。  
   需要在 BIOS 开启 AMD SVM（AMD-V / Virtualization），或在手机上用 USB 调试安装 debug APK。构建和 JVM 单元测试均已通过。
2. **Stellar 预览是近似渲染**  
   APK 内置 marked + DOMPurify + 自写标签渲染器，覆盖 note/folding/tabs/grid/timeline/image/frame/button/link/copy/inline 等常用标签；最终效果以 Hexo 构建为准。
3. **通知是 15 分钟轮询**  
   WorkManager 最小周期 15 分钟；如果需要即时推送，后续可接 Telegram Bot 或 FCM。
4. **朋友圈失败项**  
   部分站点有反爬/无 RSS，APK 的「抓取」页能看到 `last_error`，可手动填订阅地址。
5. **博客尚未发布**  
   `I:\MyBlogSource` 的改动已本地提交但未 push；push 后 Cloudflare Pages 会自动构建上线。旧的 `hexo-circle-of-friends` GitHub Action 已不再被站点引用，可以在其仓库里停用。
6. **tgtalk 导入图片默认不转存**  
   导入对话框默认保留 tgtalk 代理图片地址；勾选「把图片转存到图床」会按限速逐张上传，比较慢但更稳。

---

## 8. 第三方资源

- [marked](https://github.com/markedjs/marked) — MIT，Markdown 渲染
- [DOMPurify](https://github.com/cure53/DOMPurify) — Apache-2.0 / MPL-2.0，HTML 清洗
- 许可证文本在 `android/app/src/main/assets/preview/` 与 `THIRD_PARTY_NOTICES.md`。
