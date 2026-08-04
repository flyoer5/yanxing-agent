# 言行 Agent 第八阶段开发计划

## 目标
为对话增加联网搜索能力：用户问题触发搜索，结果注入模型上下文，让模型基于最新信息回答。

## 阶段
- [complete] 1. 项目骨架与 CI
- [complete] 2. 本地会话/消息数据层
- [complete] 3. OpenAI 兼容 API 与流式输出
- [complete] 4. Compose 聊天与模型配置界面
- [complete] 5. 单元测试、文档、推送验证
- [complete] 6. 多会话、分组与长期记忆
- [complete] 7. 语音、图片/文件输入输出
- [in_progress] 8. 联网搜索
- [pending] 9. 悬浮窗、无障碍与 Root 增强

## 本阶段验收标准
- [ ] 设置页可配置搜索 API Key（Tavily 或 Serper）
- [ ] 聊天输入框旁有"联网搜索"开关
- [ ] 开启后，发送消息自动搜索并注入结果
- [ ] 模型回答基于搜索结果（系统消息注入）
- [ ] 显示搜索结果来源/引用
- [ ] 搜索失败不影响普通对话
- [ ] GitHub Actions 编译、测试成功

## 技术方案

### 搜索提供方
- **Tavily Search API**：专为 LLM 设计，返回干净摘要
  - POST `https://api.tavily.com/search`
  - 参数：api_key, query, max_results, search_depth
  - 返回：results[] { title, url, content, score }
- **Serper API**：Google 搜索结果
  - POST `https://google.serper.dev/search`
  - 参数：q, num
  - 返回：organic[] { title, link, snippet }

### 架构
- 新增 `WebSearchClient` 接口 + `TavilySearchClient` 实现
- 搜索结果转成纯文本摘要，注入 system 消息
- 聊天页面显示"已联网搜索 N 条结果"并附引用列表

## 错误记录
| 错误 | 尝试 | 处理 |
|---|---:|---|
| - | - | - |
