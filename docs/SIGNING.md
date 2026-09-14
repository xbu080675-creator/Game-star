# Game Star Box 签名策略

## Preview / Development

从 `0.4.5` 起，所有 preview APK 必须使用同一套固定 DEV 签名，以保证后续测试版本可以原位覆盖升级。

当前固定 DEV 证书 SHA-256：

`d9bc5f96ed5fa9a4e11f3888c8c3c261333f4301ec9a266c614fb5a2ad180ecd`

该指纹是公开校验信息，不是私钥。DEV 私钥本体不提交到仓库，由 GitHub Actions Secrets 注入。CI 每次构建后使用 `apksigner` 计算实际证书 SHA-256，并与受保护 Secret 中的固定指纹比对；不一致则发布失败。

需要配置的 Repository Actions Secrets：

- `GSB_DEV_KEYSTORE_B64`：固定 DEV keystore 的 Base64 文本
- `GSB_DEV_STORE_PASSWORD`：keystore 密码
- `GSB_DEV_KEY_PASSWORD`：key 密码
- `GSB_DEV_KEY_ALIAS`：key alias
- `GSB_EXPECTED_CERT_SHA256`：该证书的 SHA-256 指纹

客户端安装更新前还会把下载 APK 的签名证书与当前已安装 Game Star Box 的证书做一致性校验。因此镜像、manifest 或下载链路即使被替换，也不能让不同签名的 APK 通过安装前检查。

## 首次迁移

`0.4.4` 及更早的测试 APK 使用过不固定的 runner/debug 签名。因此第一次安装 `0.4.5` 固定签名版时通常需要先卸载旧测试版一次。之后只要 applicationId 保持 `com.xbu.esportscenter`、versionCode 递增并继续使用同一固定 DEV 签名，即可由 APP 内 OTA 原位升级。

## Stable / Production

首个公开稳定版使用另一套独立 production keystore，并继续只通过受保护 CI Secret 注入。Preview DEV key 与 production key 永久分离，任何私钥都不得提交到公开仓库。
