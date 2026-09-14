# Game Star Box Development Changelog

## 0.4.5 / versionCode 18

- OTA 基础设施接入：GitHub preview Release 为唯一正式版本真源。
- 内置 GitHub 直连 + 六个请求级镜像通道，APK 下载前并发 Range 测速并动态排序。
- 下载支持 `.part` 断点续传与跨通道恢复。
- 安装前执行 SHA-256、包名、versionCode、固定签名证书四重校验。
- 新增 APP 内更新浮层、自动后台检查以及 WebView Native Bridge 更新接口。
- Preview 构建切换到固定 DEV 签名；CI 强制验证证书 SHA-256。
- 发布顺序固定为 APK 先上传、`latest.json` 最后覆盖。
- 启动视觉资产保持冻结，不在本版本修改。
