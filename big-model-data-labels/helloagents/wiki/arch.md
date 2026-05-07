# 架构设计

## 总体架构

```mermaid
flowchart TD
    U[用户/标注人员] --> FE[前端 Vue3]
    FE --> API[后端 REST API]
    FE --> WS[WebSocket 进度推送]

    API --> TASK[任务编排/异步执行]
    TASK --> RULE[规则预处理/结构化提取器/证据SSOT]
    RULE --> INTENT[number_intent 意图执行（规则闭环）]
    TASK --> PROMPT[提示词构建]
    PROMPT --> LLM[DeepSeek 70B]
    LLM --> ENH[二次强化/校验（可选）]

    API --> DB[(MySQL)]
    TASK --> DB
    WS --> FE
```

## 技术栈
- **后端:** Java 8 / Spring Boot 2.7.x / JPA / OkHttp / WebSocket
- **前端:** Vue 3 / Vite / TS / Element Plus / Pinia
- **数据:** MySQL 8
- **大模型:** DeepSeek 70B（OpenAI 兼容接口）

## 核心流程（目标形态：规则建模 → LLM 分析 → 校验）

```mermaid
sequenceDiagram
    participant R as 原始数据行
    participant P as 规则预处理(号码建模)
    participant M as 结构化JSON(证据模型)
    participant L as DeepSeek70B
    participant V as 一致性校验/强化(可选)
    participant O as 结果输出

    R->>P: 归一化+候选抽取+分类校验
    P->>M: 输出证据JSON(脱敏/可审计)
    M->>L: 构建提示词(仅允许引用证据)
    L->>V: 输出结论+推理(引用证据ID)
    V->>O: 通过/打回needs_review/修正置信度
```

## 重大架构决策

| adr_id | title | date | status | affected_modules | details |
|--------|-------|------|--------|------------------|---------|
| ADR-001 | 号码类任务以规则证据为SSOT，LLM只做分析与解释 | 2026-01-19 | ✅已采纳 | backend | ../history/2026-01/202601192135_num_extraction_99/how.md |
| ADR-003 | 号码类标签引入 number_intent（显式意图驱动，规则优先） | 2026-01-20 | ✅已采纳 | backend/frontend | ../history/2026-01/202601200006_phone_bank_number_intent_99/how.md |
