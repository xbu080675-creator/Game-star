# Game Star Box 签名策略

## Preview / Development

从 `0.4.5` 起，所有 preview APK 使用固定 DEV 签名，以保证后续测试版本可以原位覆盖升级。

DEV 证书 SHA-256：

`1d1c44e7bfabeb5c4bab863408f07aef1fa806c4337827830f4b72fb2920bf35`

GitHub Actions 每次构建后都会使用 `apksigner` 校验证书指纹，不匹配即构建失败。

此 DEV 私钥随公开仓库分发，只用于测试版升级兼容，不构成生产安全边界。

## 首次迁移

`0.4.4` 及更早的测试 APK 使用过不固定的 runner/debug 签名。因此第一次安装 `0.4.5` 固定签名版时通常需要先卸载旧测试版一次。之后只要 applicationId 保持 `com.xbu.esportscenter` 且 versionCode 递增，即可由 APP 内 OTA 原位升级。

## Stable / Production

首个公开稳定版必须切换到单独的私有 production keystore，通过受保护 CI Secret 注入。不得继续使用公开 DEV key，也不得将 production 私钥提交到仓库。
