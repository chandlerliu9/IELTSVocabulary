# 雅思单词库

一款原生 Android 离线背单词应用，内置雅思词库和本地发音资源，并支持使用 Excel 管理自定义词库。

## 功能

- 显示单词并自动播放发音，点击学习区域后显示详细信息
- 支持上一词、下一词、标记掌握和全局收藏
- 已掌握单词自动移出后续学习队列
- 内置默认雅思词库，支持导入多个 `.xlsx` 词库
- 不同词库分别保存学习进度，自定义词库可删除
- 收藏跨所有词库汇总
- 可导出当前词库学习状态和全局收藏 Excel
- 核心学习功能离线可用；本地缺少发音时可联网获取

## 环境

- Android Studio
- Android SDK 35
- JDK 17 或 Android Studio 内置 JDK
- 最低 Android 6.0（API 23）

## 构建

使用 Android Studio 打开项目并同步 Gradle，或者执行：

```powershell
$env:JAVA_HOME='E:\Developer Tools\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

发布用 AAB：

```powershell
.\gradlew.bat bundleRelease
```

## Excel 词库

必需列为 `term`。应用还会读取现有词库中的音标、词性、释义、`example_en` 等支持字段。`example_zh` 不在学习页显示。

## 数据与隐私

学习进度、收藏和导入词库保存在设备本地。只有缺少本地发音时才会使用网络。详细说明见 [隐私政策](release/privacy-policy-zh.md)。

## 声明

本项目不是 IELTS 官方应用，亦未获得 IELTS 相关机构的赞助或认可。IELTS 是其权利人的商标。公开分发词库、释义、音频和图标前，请自行确认相应内容的授权状态。

第三方材料及权利人联系、移除流程详见 [内容与权利声明](NOTICE.md)。
