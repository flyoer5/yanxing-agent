# Yanxing Agent - CI 编译指南

## 方式一：GitHub Actions (推荐)

### 1. 推送代码到 GitHub
```bash
git add .
git commit -m "feat: 添加操作日志功能"
git push origin main
```

然后在 GitHub 页面点击 **Actions** → **Manual Build** → **Run workflow**

### 2. 等待构建完成
- 构建成功后，APK 会作为 Artifact 上传
- 点击下载链接即可获取 `app-debug.apk`

### 3. 安装 APK
将 APK 传到 Android 设备并安装

---

## 方式二：本地编译

### 环境要求
- JDK 17+
- Android SDK (API 35, build-tools 35.0.0)
- Gradle 8.9+

### 步骤
```bash
cd /var/minis/workspace/yanxing-agent

# 设置环境变量
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# 下载必要的 SDK 组件
sdkmanager "platforms;android-35" "build-tools;35.0.0"

# 生成 Gradle Wrapper
gradle wrapper --gradle-version 8.9

# 构建 Debug APK
./gradlew assembleDebug --no-daemon

# APK 位置：app/build/outputs/apk/debug/app-debug.apk
```

---

## 新功能测试清单

✅ 操作日志功能已完成，包括：
- [x] ActionLogEntity 数据实体
- [x] action_logs 数据库表
- [x] 日志 CRUD Repository 方法
- [x] 日志页面 UI (底部 Tab 3)
- [x] 动作执行时自动记录日志
- [x] 清空全部/指定应用日志功能

🧪 测试步骤：
1. 打开 App → 切换到"聊天"Tab
2. 切换到"替我行动"模式 (在设置中开启)
3. 说："帮我打开设置"
4. 确认后观察动作列表
5. 切换到第三个 Tab **"日志"**
6. 查看完整记录：时间、应用名、动作类型、结果状态、详情、错误信息
