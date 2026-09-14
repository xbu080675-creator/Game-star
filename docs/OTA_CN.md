# Game Star Box OTA 更新架构

从 `0.4.5` 起，Game Star Box 沿用旧电竞助手验证过的 OTA 策略，并将其作为基础设施锁定。

## 真源与镜像边界

- GitHub `xbu080675-creator/Game-star` 是唯一代码、版本、构建与 APK 正式发布真源。
- 固定 Release/tag：`preview`。
- 客户端固定读取：`https://github.com/xbu080675-creator/Game-star/releases/download/preview/latest.json`。
- 镜像只允许转发同一个官方 GitHub Release 资源，不能决定版本、改写包名或切换到其他仓库。

默认更新传输池：

- GitHub 直连
- `https://gh.llkk.cc/`
- `https://cors.isteed.cc/`
- `https://gh.xmly.dev/`
- `https://gh.ddlc.top/`
- `https://ghfast.top/`
- `https://ghproxy.net/`

## 客户端流程

1. 优先直连 GitHub 获取 `latest.json`；直连失败时才使用镜像获取同一个官方 URL。
2. 新版本存在时，对 GitHub 直连和全部镜像对真实 APK 发起小段 HTTP Range 并发测速。
3. 依据当前网络实际吞吐排序下载通道。
4. 下载中断保留 `.part` 文件，切换通道后通过 HTTP Range 继续下载。
5. APK 完成后必须依次通过：SHA-256、包名 `com.xbu.esportscenter`、manifest `versionCode`、固定 DEV 证书 SHA-256。
6. 全部校验通过后才拉起 Android 系统安装器。
7. 镜像请求只服务白名单 GitHub Release URL，不创建 VPN，不修改系统代理。

## 发布顺序

CI 发布必须遵循：固定签名构建 -> 校验证书 -> 上传新 APK -> 最后覆盖 `latest.json`。

禁止先发布 manifest 再上传 APK，避免客户端看到一个尚不可下载的新版本。
