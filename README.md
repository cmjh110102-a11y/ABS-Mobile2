# ABS Mobile — Android prototype

這是 ABS Mobile 的 Android 原生測試原型，使用 Java、AndroidX CameraX 與職業棒球轉播風格介面。

## 目前功能

- Android 後鏡頭即時預覽（CameraX）
- 橫向全螢幕介面
- 好球帶框線疊加
- 校正模式下可拖曳好球帶位置
- 手動 BALL / STRIKE 操作
- B / S / O 基本球數顯示與重置

## 重要限制

這個版本是可安裝、可操作的 UI／攝影機原型，**尚未包含 AI 棒球偵測模型、逐影格球體追蹤、3D 攝影機校正或自動好壞球推論**。畫面上的 BALL / STRIKE 目前由操作按鈕手動輸入，不能當成正式裁判判決。

## GitHub Actions 雲端編譯 APK

1. 將本資料夾內的所有內容上傳到 GitHub repository 根目錄。根目錄應直接看到 `app/`、`build.gradle`、`settings.gradle` 與 `.github/`。
2. 開啟 repository 的 **Actions**。
3. 選擇 **Build ABS Mobile APK**，按 **Run workflow**，選擇 `main`，再按綠色按鈕。
4. 等待工作流程出現綠色勾勾。
5. 進入該次執行紀錄，在 **Artifacts** 下載 `ABS-Mobile-debug-apk`。
6. 解壓縮 artifact，取得 `app-debug.apk`，傳到 Android 手機安裝。

工作流程使用 GitHub Ubuntu runner 內建的 Android SDK，不再執行先前失敗的 `android-actions/setup-android@v3` 步驟。Gradle 8.9、JDK 17 與 Android Gradle Plugin 8.7.3 相互配套。

## 本機編譯

使用 Android Studio 開啟此資料夾，安裝 Android SDK Platform 35，等待 Gradle Sync 完成，再執行 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。

輸出位置：`app/build/outputs/apk/debug/app-debug.apk`。
