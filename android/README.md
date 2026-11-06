# 星语 AI 塔罗 Android 测试版

这是 V0.1 网页原型的 Android WebView 封装，原生 Android 外壳与内置 HTTPS WebViewAssetLoader 承载 78 张牌、界面与本地记录。

**状态说明**：此 APK 为侧载安装的 debug 测试版，不是正式签名发布版。DeepSeek 在线推理依赖尚未部署的安全后端；此包可离线体验本地塔罗抽牌和示例解读，不能声称 AI 已连通。

## Build
Run the GitHub Actions workflow `Build Starweave Tarot APK` on the feature branch. Download the artifact and unzip the debug APK.

## Security
Do not embed API keys in Android assets, app source, or GitHub Actions logs. Leaked credentials must be revoked before production use. To connect a production API, deploy an authenticated backend with rate limits and use a configurable HTTPS URL.

Android minSdk 24, targetSdk 34, compileSdk 35.
