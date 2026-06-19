# AssignPass

离线作业提醒 App —— 截图识别作业信息，自动填充并提醒提交截止时间。

## 功能

- **截图识别**：上传作业截图，ML Kit OCR 自动识别作业名称、截止时间、平台等信息
- **手动录入**：支持手动输入作业信息
- **本地提醒**：基于 AlarmManager 的本地推送提醒，完全离线，无需服务器
- **作业管理**：创建、编辑、完成标记、删除，按截止时间排序
- **多平台支持**：mis课程平台、中国大学MOOC、雨课堂、学习通、智慧树等

## 截图识别能力

| 字段 | 识别方式 |
|------|---------|
| 作业名称 | 关键词匹配 + 启发式提取 |
| 截止时间 | 仅从含"截止/期限/ddl"等关键词的行提取日期，避免误识别 |
| 平台 | 自动检测已知平台名称 |
| 学科 | 匹配常见学科名列表，未命中则留空由用户手动选择 |
| 备注 | 作业内容等附加信息 |

### OCR 优化要点

- 利用 ML Kit TextBlock 的 **boundingBox 坐标** 过滤状态栏区域（顶部 6%）
- **编辑距离模糊匹配**（≤1），容忍 OCR 字符识别错误
- **年份校验**：自动修正 OCR 将 2026 误识别为 2027/2028 的情况
- 全程 Log 输出，便于调试

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **OCR**：Google ML Kit Text Recognition（中文）
- **数据库**：Room (SQLite)
- **提醒**：AlarmManager + BroadcastReceiver
- **架构**：MVVM（ViewModel + StateFlow）

## 截图

> 可在此处添加 App 截图

## 开始使用

1. Clone 仓库
```bash
git clone https://github.com/你的用户名/AssignPass.git
```

2. 用 Android Studio 打开项目

3. Sync Gradle，连接设备或模拟器，点击 Run

## 最低要求

- Android 8.0 (API 26) 及以上
- Android Studio Koala 及以上

## License

MIT License
