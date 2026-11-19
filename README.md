# GitHub加速

[github.akams.cn](https://github.akams.cn/) 的安卓客户端。原站是电脑端布局，手机上输入框和节点列表很难用；这个 App 用原生 Kotlin + Jetpack Compose 重做了移动端界面，文件仍然走原站的公益加速节点。

## 功能

- **一键生成加速链接**：粘贴 GitHub Release / Raw / Archive / Gist 链接，选节点，生成 `https://节点/https://github.com/...` 地址。
- **节点选择与测速**：内置官网最新的 80+ 节点，支持延迟测试、单节点下载测速、搜索、排序。
- **Releases 浏览**：输入 `owner/repo`（或仓库链接），列出 Release 与全部附件，直接复制加速链接或用系统下载器下载到 `Download/GitHub/`。
- **Git 克隆命令**：输入仓库地址时自动生成 `git clone` 加速命令。
- **分享即用**：在浏览器里把 GitHub 链接「分享」到本应用，自动填入输入框。
- **节点在线更新**：从官网抓取最新节点列表；失败时继续用内置列表，也可以手动添加节点。
- **自动测速选优**：启动时自动测试节点延迟，测完自动切到最快的节点；下次启动先恢复上次使用的节点。
- **应用内检查更新**：启动静默检查 GitHub Releases，发现新版本可一键更新，更新包在 gh-proxy.org 与当前节点之间自动选快的下载。
- **深色模式 / 可选 GitHub Token**：Token 只存在本机，用于把 Releases 接口额度从 60 次/小时提升到 5000 次/小时。

## 下载

到 [Releases](https://github.com/hzxs163/gighub-android/releases) 下载 APK，安装即可（Android 7.0+）。

> GitHub Releases 会剥掉资产文件名中的非 ASCII 字符，因此 APK 的实际文件名是 `GitHub-v<版本>.apk`，Release 页面通过 label 显示为 **GitHub加速-v<版本>**。下载后的文件也可自行改名。

## 构建

```bash
./gradlew assembleRelease
```

Release APK 输出在 `app/build/outputs/apk/release/app-release.apk`。

仓库里带了自签名 keystore（`keystore/gighub.jks`，密码见 `keystore.properties`），方便 GitHub Actions 直接产出可安装包，也保证后续版本能覆盖安装。请不要用这个密钥签任何其他应用。

推送到 `main` 或打 `v*` tag 会触发 Actions：构建、发布到 Releases，并把资产 label 设为中文名。版本号由 CI 自动递增（`0.1.<run number>`），versionCode 同步增长，无需手动改；要换大版本时改 `gradle.properties` 里的 `appVersion`。

## 说明

- 节点列表与加速能力来自 [github.akams.cn](https://github.akams.cn/)，本项目只是客户端，与站点维护者无关联。
- 公益服务请勿滥用；大文件下载请尊重 GitHub 与节点提供者的带宽。
- 加速链接由第三方节点中转，敏感仓库请谨慎使用。