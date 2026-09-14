# Spacelife #14 · CC0 授权/来源凭证

本文件用于给 Game Star Box（竞界）保存主界面背景音乐的来源、许可与文件完整性证据。它不是 OpenGameArt 或 Creative Commons 单独签发的“商业授权证书”，而是项目侧的可审计证据记录。

## 作品信息

- 作品名：`Spacelife #14`
- 作者 / 上传者：`yd`
- 类型：Music / background track with space mood
- OpenGameArt 发布日期：2013-09-19
- 来源页：https://opengameart.org/content/spacelife-14
- 原始文件：https://opengameart.org/sites/default/files/spacelifeNo14.ogg
- 原始文件名：`spacelifeNo14.ogg`
- 原始文件大小：`634526` bytes
- SHA-256：`64ba0b12ee09c106bca11531010534df282f3474a88fb1923d55b3de469898d4`
- 音频格式：Ogg Vorbis, stereo, 44.1 kHz

## 许可

OpenGameArt 的作品页面明确将该作品标注为 `CC0`。

CC0 1.0 Universal 官方说明：
https://creativecommons.org/publicdomain/zero/1.0/

CC0 法律文本：
https://creativecommons.org/publicdomain/zero/1.0/legalcode.en

Creative Commons 对 CC0 的说明是：权利人以法律允许的最大程度放弃其版权及相关权利，使作品尽可能进入公共领域；CC0 本身不要求署名。Game Star Box 仍保留本文件中的作者与来源记录，以便审计和溯源。

## 项目使用方式

- 用途：Game Star Box 主界面低音量循环背景音乐。
- APK 内文件名：`menu_bgm.ogg`
- 默认目标音量：约 10.5%。
- 启动动画期间不播放；启动动画完成后淡入。
- 用户可在“系统”页关闭/开启主界面音乐。
- 音乐不会作为独立素材包销售或重新授权。

## 完整性与供应链规则

CI 不信任远端文件名本身。每次构建会从上述 OpenGameArt 原始文件地址下载音频，并强制校验固定 SHA-256：

`64ba0b12ee09c106bca11531010534df282f3474a88fb1923d55b3de469898d4`

只要远端内容发生任何字节变化，构建立即失败，不会把变化后的文件静默打进 APK。

## 证据取得记录

2026-09-14，GitHub Actions 从 OpenGameArt 原始文件地址下载该文件并实测：

- size: `634526`
- SHA-256: `64ba0b12ee09c106bca11531010534df282f3474a88fb1923d55b3de469898d4`
- file: Ogg Vorbis audio, stereo, 44100 Hz

本记录与仓库提交历史共同作为 Game Star Box 使用该音频时的许可与文件身份凭证。
