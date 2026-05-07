# 前端 UI/UX 设计规范文档

> **项目名称**：智能数据标注平台 V3.0
> **设计风格**：深蓝主题 + 渐变卡片 + 圆角设计
> **技术栈**：Vue 3 + Element Plus + TypeScript

---

## 📐 一、现有设计风格分析

### 1.1 配色方案

```css
/* 主题色（深蓝色系） */
--primary-color: #1e3a5f;         /* 主色 */
--primary-light: #2c5282;         /* 浅主色 */
--primary-dark: #0f2744;          /* 深主色 */
--accent-color: #3182ce;          /* 强调色（亮蓝） */
--accent-light: #63b3ed;          /* 浅强调色 */

/* 语义色 */
--success-color: #38a169;         /* 成功（绿色） */
--warning-color: #d69e2e;         /* 警告（黄色） */
--danger-color: #e53e3e;          /* 危险（红色） */

/* 背景色 */
--bg-gradient-start: #f0f4f8;     /* 渐变起始 */
--bg-gradient-end: #e2e8f0;       /* 渐变结束 */
--sidebar-bg: linear-gradient(180deg, #1e3a5f 0%, #2c5282 100%);
--header-bg: linear-gradient(90deg, #1e3a5f 0%, #2c5282 50%, #1e3a5f 100%);
```

### 1.2 视觉特点

| 特性 | 描述 | 示例 |
|------|------|------|
| **背景** | 渐变浅灰蓝色背景 | `linear-gradient(135deg, #f0f4f8 0%, #e2e8f0 100%)` |
| **卡片** | 白色渐变背景 + 圆角 + 阴影 | `border-radius: 12px` + `box-shadow` |
| **按钮** | 渐变色 + 圆角 + 悬停效果 | `linear-gradient(135deg, #3182ce 0%, #2c5282 100%)` |
| **输入框** | 圆角 + 聚焦时蓝色边框 | `border-radius: 8px` + 蓝色 `box-shadow` |
| **标签** | 渐变色背景 + 深色文字 | 圆角 `6px` + 无边框 |
| **侧边栏** | 深蓝色渐变背景 | 深蓝 + 白色半透明文字 |
| **表格** | 灰色表头 + 悬停高亮 | 表头渐变背景 |
| **对话框** | 渐变标题栏 + 圆角 | 标题栏深蓝渐变 |

### 1.3 交互模式

| 交互类型 | 实现方式 | 适用场景 |
|----------|----------|----------|
| **列表展示** | `el-table` 表格 | 数据列表、标签列表、模板列表 |
| **卡片展示** | `el-card` + 自定义样式 | 数据集列表、统计卡片 |
| **创建/编辑** | `el-dialog` 对话框 | 表单输入、配置编辑 |
| **详情查看** | `el-drawer` 抽屉 | 版本历史、详细配置 |
| **筛选搜索** | 顶部筛选栏 | 关键字搜索、状态筛选 |
| **分页** | `el-pagination` | 数据列表分页 |

---

## 🎨 二、新增功能页面设计

### 2.1 提示词模板管理页面 (`PromptTemplatesView.vue`)

#### 页面布局

```
┌─────────────────────────────────────────────────────────┐
│  提示词模板管理                    [+ 新建模板] [搜索框]  │
├─────────────────────────────────────────────────────────┤
│  [分类筛选] 全部 | 分类标签 | 信息提取 | 自定义           │
├─────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────┐  │
│  │ 模板卡片列表（Grid 布局，3列）                    │  │
│  │                                                   │  │
│  │  ┌──────────────┐  ┌──────────────┐             │  │
│  │  │ 📝 默认分类  │  │ 📝 默认提取  │             │  │
│  │  │ 模板         │  │ 模板         │             │  │
│  │  │              │  │              │             │  │
│  │  │ 分类: 标签   │  │ 分类: 提取   │             │  │
│  │  │ 版本: v3     │  │ 版本: v1     │             │  │
│  │  │ 使用: 156次  │  │ 使用: 89次   │             │  │
│  │  │              │  │              │             │  │
│  │  │ [编辑][测试] │  │ [编辑][测试] │             │  │
│  │  └──────────────┘  └──────────────┘             │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  < 分页 >                                               │
└─────────────────────────────────────────────────────────┘
```

#### 卡片设计细节

```vue
<el-card class="template-card">
  <!-- 卡片头部 -->
  <div class="card-header">
    <div class="template-icon">📝</div>
    <div class="template-info">
      <h3 class="template-name">{{ template.name }}</h3>
      <el-tag :type="getCategoryTagType(template.category)" size="small">
        {{ getCategoryLabel(template.category) }}
      </el-tag>
    </div>
    <el-dropdown v-if="template.isDefault">
      <el-tag type="success" size="small">⭐ 默认</el-tag>
    </el-dropdown>
  </div>

  <!-- 卡片内容 -->
  <div class="card-content">
    <div class="stat-item">
      <span class="label">版本</span>
      <span class="value">v{{ template.version }}</span>
    </div>
    <div class="stat-item">
      <span class="label">使用次数</span>
      <span class="value">{{ template.usageCount }}</span>
    </div>
    <div class="stat-item">
      <span class="label">成功率</span>
      <span class="value">{{ template.successRate }}%</span>
    </div>
    <div class="stat-item">
      <span class="label">平均信心度</span>
      <span class="value">{{ template.avgConfidence }}</span>
    </div>
  </div>

  <!-- 卡片操作 -->
  <div class="card-actions">
    <el-button size="small" @click="openEdit(template)">
      <el-icon><Edit /></el-icon> 编辑
    </el-button>
    <el-button size="small" type="primary" @click="openTest(template)">
      <el-icon><Document /></el-icon> 测试
    </el-button>
    <el-dropdown @command="handleMoreAction(template, $event)">
      <el-button size="small">
        <el-icon><MoreFilled /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="versions">版本历史</el-dropdown-item>
          <el-dropdown-item command="copy">复制</el-dropdown-item>
          <el-dropdown-item command="setDefault" v-if="!template.isDefault">
            设为默认
          </el-dropdown-item>
          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</el-card>
```

#### 样式实现

```css
.template-card {
  border-radius: 12px;
  transition: all 0.3s ease;
  margin-bottom: 20px;
}

.template-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--card-shadow-hover);
}

.template-card .card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}

.template-icon {
  font-size: 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: white;
}

.template-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--primary-color);
}

.card-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-item .label {
  font-size: 12px;
  color: #64748b;
}

.stat-item .value {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
}

.card-actions {
  display: flex;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.card-actions .el-button {
  flex: 1;
}
```

#### 创建/编辑模板对话框

```vue
<el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px">
  <el-form :model="form" label-width="120px">
    <!-- 基础信息 -->
    <el-divider content-position="left">基础信息</el-divider>
    <el-form-item label="模板名称">
      <el-input v-model="form.name" placeholder="例如：默认分类模板" />
    </el-form-item>

    <el-form-item label="模板分类">
      <el-radio-group v-model="form.category">
        <el-radio value="classification">分类标签</el-radio>
        <el-radio value="extraction">信息提取</el-radio>
        <el-radio value="summary">摘要生成</el-radio>
        <el-radio value="custom">自定义</el-radio>
      </el-radio-group>
    </el-form-item>

    <el-form-item label="模板描述">
      <el-input type="textarea" v-model="form.description" :rows="2"
        placeholder="简要描述模板的用途和适用场景" />
    </el-form-item>

    <!-- 提示词内容 -->
    <el-divider content-position="left">提示词内容</el-divider>

    <el-form-item label="系统提示词">
      <el-input type="textarea" v-model="form.systemPrompt" :rows="6"
        placeholder="定义AI的角色和输出格式要求，例如：你是专业的数据标注助手..." />
      <div class="form-tip">
        💡 提示：系统提示词定义AI的角色和行为准则
      </div>
    </el-form-item>

    <el-form-item label="用户提示词模板">
      <el-input type="textarea" v-model="form.userPromptTemplate" :rows="8"
        placeholder="支持变量占位符，例如：请判断${labelName}..." />
      <div class="form-tip">
        💡 支持的变量：${labelName}、${labelRule}、${rowData}、${focusColumns}、${extractFields}
      </div>
    </el-form-item>

    <!-- 模型参数 -->
    <el-divider content-position="left">模型参数</el-divider>
    <el-form-item label="温度参数">
      <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" :marks="{0: '精确', 1: '平衡', 2: '创意'}" />
      <span class="param-value">{{ form.temperature }}</span>
    </el-form-item>

    <el-form-item label="最大Token数">
      <el-input-number v-model="form.maxTokens" :min="10" :max="8000" :step="10" />
    </el-form-item>

    <!-- 测试区域 -->
    <el-divider content-position="left">测试模板</el-divider>
    <el-form-item label="测试数据">
      <el-input type="textarea" v-model="testData" :rows="4"
        placeholder='输入JSON格式的测试数据，例如：{"labelName":"涉警人员","labelRule":"包含警务相关关键词","rowData":{"姓名":"张三","备注":"嫌疑人"}}' />
    </el-form-item>
    <el-button @click="runTest" :loading="testing">
      <el-icon><VideoPlay /></el-icon> 运行测试
    </el-button>

    <!-- 测试结果 -->
    <div v-if="testResult" class="test-result">
      <div class="result-header">
        <span>测试结果</span>
        <el-tag :type="testResult.success ? 'success' : 'danger'">
          {{ testResult.success ? '✅ 成功' : '❌ 失败' }}
        </el-tag>
      </div>
      <div class="result-content">
        <pre>{{ testResult.response }}</pre>
        <div class="result-meta">
          <span>Token数: {{ testResult.tokens }}</span>
          <span>耗时: {{ testResult.duration }}ms</span>
        </div>
      </div>
    </div>
  </el-form>

  <template #footer>
    <el-button @click="dialogVisible = false">取消</el-button>
    <el-button type="primary" @click="submit">保存模板</el-button>
  </template>
</el-dialog>
```

---

### 2.2 标签创建页面升级 (`LabelsView.vue`)

#### 新增规则模式选择

在现有标签创建对话框中，新增规则模式选择：

```vue
<el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px">
  <el-form :model="form" label-width="120px">
    <!-- 基础信息（保持不变） -->
    <el-form-item label="标签名称">
      <el-input v-model="form.name" placeholder="例如：涉警人员" />
    </el-form-item>

    <el-form-item label="标签类型">
      <el-radio-group v-model="form.type">
        <el-radio value="classification">分类判断（是/否）</el-radio>
        <el-radio value="extraction">信息提取</el-radio>
      </el-radio-group>
    </el-form-item>

    <!-- 【新增】规则模式选择 -->
    <el-form-item label="规则模式">
      <el-radio-group v-model="form.ruleType">
        <el-tooltip content="使用大模型理解语义，准确性高" placement="top">
          <el-radio value="llm">
            <div class="rule-mode-option">
              <span class="option-icon">💬</span>
              <div class="option-content">
                <div class="option-title">大模型判断</div>
                <div class="option-desc">AI理解语义，准确性高</div>
              </div>
            </div>
          </el-radio>
        </el-tooltip>

        <el-tooltip content="使用预置规则快速匹配，速度快" placement="top">
          <el-radio value="rule">
            <div class="rule-mode-option">
              <span class="option-icon">⚡</span>
              <div class="option-content">
                <div class="option-title">规则匹配</div>
                <div class="option-desc">正则匹配，速度快</div>
              </div>
            </div>
          </el-radio>
        </el-tooltip>

        <el-tooltip content="规则预筛 + LLM确认，平衡速度与准确性" placement="top">
          <el-radio value="hybrid">
            <div class="rule-mode-option">
              <span class="option-icon">🔀</span>
              <div class="option-content">
                <div class="option-title">混合模式</div>
                <div class="option-desc">规则预筛 + AI确认</div>
              </div>
            </div>
          </el-radio>
        </el-tooltip>
      </el-radio-group>
    </el-form-item>

    <!-- 动态表单：根据规则模式显示不同内容 -->
    <div v-if="form.ruleType === 'llm'" class="rule-config-section">
      <!-- 大模型配置 -->
      <el-form-item label="提示词模板">
        <el-select v-model="form.promptTemplateId" placeholder="选择提示词模板">
          <el-option v-for="tpl in promptTemplates" :key="tpl.id"
            :label="tpl.name" :value="tpl.id">
            <div class="template-option">
              <span>{{ tpl.name }}</span>
              <el-tag size="small" :type="getCategoryTagType(tpl.category)">
                {{ getCategoryLabel(tpl.category) }}
              </el-tag>
            </div>
          </el-option>
        </el-select>
        <el-button link type="primary" @click="openTemplateManager">
          <el-icon><Setting /></el-icon> 管理模板
        </el-button>
      </el-form-item>

      <el-form-item label="标签规则">
        <el-input type="textarea" v-model="form.description" :rows="4"
          placeholder="描述标签的判断规则，例如：数据中包含姓名、电话等个人敏感信息，且与警务工作相关" />
      </el-form-item>

      <el-form-item label="关注列">
        <el-select v-model="form.focusColumns" multiple placeholder="选择重点关注的数据列">
          <el-option v-for="col in availableColumns" :key="col" :label="col" :value="col" />
        </el-select>
      </el-form-item>
    </div>

    <div v-if="form.ruleType === 'rule'" class="rule-config-section">
      <!-- 规则匹配配置 -->
      <el-form-item label="添加规则">
        <el-select @change="addBuiltinRule" placeholder="选择内置规则">
          <el-option-group v-for="category in ruleCategories" :key="category.name"
            :label="category.displayName">
            <el-option v-for="rule in category.rules" :key="rule.id"
              :label="rule.displayName" :value="rule.id">
              <div class="rule-option">
                <span class="rule-name">{{ rule.displayName }}</span>
                <span class="rule-category">{{ category.displayName }}</span>
              </div>
            </el-option>
          </el-option-group>
        </el-select>
      </el-form-item>

      <!-- 已选规则列表 -->
      <div v-if="form.rules.length > 0" class="selected-rules">
        <div class="section-title">已选规则</div>
        <div v-for="(rule, index) in form.rules" :key="index" class="rule-item">
          <el-tag closable @close="removeRule(index)">
            {{ rule.displayName }}
          </el-tag>
          <el-select v-model="rule.targetColumn" placeholder="选择目标列" size="small">
            <el-option v-for="col in availableColumns" :key="col" :label="col" :value="col" />
          </el-select>
        </div>

        <el-form-item label="逻辑关系">
          <el-radio-group v-model="form.logic">
            <el-radio value="AND">
              <span style="font-weight: 600; color: var(--accent-color);">AND</span>
              <span style="margin-left: 4px; font-size: 12px; color: #64748b;">且（所有规则都满足）</span>
            </el-radio>
            <el-radio value="OR">
              <span style="font-weight: 600; color: var(--warning-color);">OR</span>
              <span style="margin-left: 4px; font-size: 12px; color: #64748b;">或（任一规则满足）</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </div>
    </div>

    <div v-if="form.ruleType === 'hybrid'" class="rule-config-section">
      <!-- 混合模式配置 -->
      <el-collapse>
        <el-collapse-item title="第一阶段：规则预筛选" name="first">
          <!-- 同"规则匹配"配置 -->
          <div class="hybrid-stage">
            <el-form-item label="添加规则">
              <el-select @change="addBuiltinRule" placeholder="选择内置规则">
                <!-- 同上 -->
              </el-select>
            </el-form-item>
            <div class="selected-rules">
              <!-- 已选规则列表 -->
            </div>
            <el-form-item label="逻辑关系">
              <el-radio-group v-model="form.logic">
                <el-radio value="AND">AND（且）</el-radio>
                <el-radio value="OR">OR（或）</el-radio>
              </el-radio-group>
            </el-form-item>
          </div>
        </el-collapse-item>

        <el-collapse-item title="第二阶段：大模型确认" name="second">
          <div class="hybrid-stage">
            <el-form-item label="触发条件">
              <el-radio-group v-model="form.hybridTrigger">
                <el-radio value="first_pass">
                  第一阶段通过时执行
                  <div class="radio-tip">当规则匹配成功时，使用LLM进一步确认</div>
                </el-radio>
                <el-radio value="first_fail">
                  第一阶段失败时执行
                  <div class="radio-tip">当规则匹配失败时，使用LLM判断</div>
                </el-radio>
                <el-radio value="always">
                  总是执行
                  <div class="radio-tip">无论规则匹配结果如何，都使用LLM判断</div>
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="提示词模板">
              <el-select v-model="form.promptTemplateId">
                <!-- 同上 -->
              </el-select>
            </el-form-item>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 测试区域 -->
    <el-divider content-position="left">测试规则</el-divider>
    <el-form-item label="测试数据">
      <el-input type="textarea" v-model="testData" :rows="3"
        placeholder='输入JSON格式的测试数据，例如：{"姓名":"张三","联系方式":"13812345678"}' />
    </el-form-item>
    <el-button @click="runLabelTest" :loading="testing">
      <el-icon><VideoPlay /></el-icon> 运行测试
    </el-button>

    <!-- 测试结果 -->
    <div v-if="testResult" class="test-result-label">
      <el-alert :type="testResult.match ? 'success' : 'info'" :closable="false">
        <template #title>
          <div class="result-title">
            <span>测试结果: {{ testResult.match ? '✅ 匹配' : '❌ 不匹配' }}</span>
            <el-tag size="small">信心度: {{ testResult.confidence }}%</el-tag>
          </div>
        </template>
        <div v-if="testResult.extracted" class="result-extracted">
          <strong>提取内容:</strong>
          <pre>{{ JSON.stringify(testResult.extracted, null, 2) }}</pre>
        </div>
        <div v-if="testResult.reasoning" class="result-reasoning">
          <strong>判断依据:</strong> {{ testResult.reasoning }}
        </div>
      </el-alert>
    </div>
  </el-form>

  <template #footer>
    <el-button @click="dialogVisible = false">取消</el-button>
    <el-button type="primary" @click="submit">保存标签</el-button>
  </template>
</el-dialog>
```

#### 样式实现

```css
/* 规则模式选择样式 */
.rule-mode-option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 2px solid #e2e8f0;
  border-radius: 8px;
  transition: all 0.3s ease;
  cursor: pointer;
}

.rule-mode-option:hover {
  border-color: var(--accent-color);
  background: #f0f7ff;
}

.el-radio.is-checked .rule-mode-option {
  border-color: var(--accent-color);
  background: linear-gradient(135deg, #e6f0ff 0%, #f0f7ff 100%);
}

.option-icon {
  font-size: 24px;
}

.option-content {
  flex: 1;
}

.option-title {
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 4px;
}

.option-desc {
  font-size: 12px;
  color: #64748b;
}

/* 规则配置区域 */
.rule-config-section {
  background: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  margin-top: 12px;
}

.section-title {
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 12px;
}

/* 已选规则列表 */
.selected-rules {
  background: white;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  margin-top: 12px;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.rule-item:last-child {
  margin-bottom: 0;
}

/* 测试结果样式 */
.test-result-label {
  margin-top: 16px;
}

.result-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
}

.result-extracted {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 6px;
}

.result-extracted pre {
  margin: 8px 0 0 0;
  padding: 8px;
  background: white;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
}

.result-reasoning {
  margin-top: 8px;
  color: #64748b;
  font-size: 14px;
}

/* 混合模式折叠面板 */
.hybrid-stage {
  padding: 12px;
  background: #f8fafc;
  border-radius: 6px;
}

.radio-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  margin-left: 24px;
}
```

---

### 2.3 外接数据源管理页面 (`DataSourcesView.vue`)

#### 页面布局

```
┌─────────────────────────────────────────────────────────┐
│  外接数据源管理                    [+ 添加数据源] [搜索]  │
├─────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────┐  │
│  │ 数据源列表（表格视图）                             │  │
│  │                                                   │  │
│  │  ┌─────┬──────────┬──────┬──────┬──────┬──────┐  │  │
│  │  │ 名称 │ 类型     │ 主机 │ 状态 │ 操作 │      │  │  │
│  │  ├─────┼──────────┼──────┼──────┼──────┼──────┤  │  │
│  │  │ Oracle│ Oracle   │ ...  │ ✓ 已 │ 测试 │      │  │  │
│  │  │ 数据库│          │      │ 连接 │ 查询 │      │  │  │
│  │  │      │          │      │      │ 删除 │      │  │  │
│  │  └─────┴──────────┴──────┴──────┴──────┴──────┘  │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

#### SQL 查询构建器对话框（核心功能）

```vue
<el-dialog v-model="queryBuilderVisible" title="SQL 查询构建器" width="1200px" fullscreen>
  <div class="query-builder-layout">
    <!-- 左侧：数据源浏览器 -->
    <div class="data-browser">
      <div class="browser-header">
        <h4>📊 数据库浏览器</h4>
        <el-select v-model="currentDataSourceId" @change="loadTables">
          <el-option v-for="ds in dataSources" :key="ds.id"
            :label="ds.name" :value="ds.id" />
        </el-select>
      </div>

      <div class="browser-content">
        <!-- 表列表 -->
        <el-tree :data="tables" @node-click="loadColumns" class="table-tree">
          <template #default="{ node, data }">
            <div class="tree-node">
              <span class="node-icon">📋</span>
              <span class="node-label">{{ data.name }}</span>
            </div>
          </template>
        </el-tree>

        <!-- 列信息详情 -->
        <div v-if="selectedTable" class="columns-panel">
          <div class="panel-header">
            <h5>📄 {{ selectedTable.name }}</h5>
            <el-button size="small" @click="insertTableToSql">
              <el-icon><Plus /></el-icon> 插入到SQL
            </el-button>
          </div>
          <div class="columns-list">
            <div v-for="col in selectedTable.columns" :key="col.name"
              class="column-item"
              @click="insertColumnToSql(col)">
              <div class="column-info">
                <span class="column-name">{{ col.name }}</span>
                <el-tag size="small" :type="getColumnTagType(col.type)">
                  {{ col.type }}
                </el-tag>
              </div>
              <div class="column-desc">{{ col.remarks || '无描述' }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧：SQL编辑器 -->
    <div class="sql-editor-panel">
      <div class="editor-header">
        <h4>✍️ SQL 编辑器</h4>
        <div class="editor-actions">
          <el-button @click="formatSql">
            <el-icon><Document /></el-icon> 格式化
          </el-button>
          <el-button @click="clearSql">
            <el-icon><Delete /></el-icon> 清空
          </el-button>
          <el-button type="primary" @click="previewQuery">
            <el-icon><VideoPlay /></el-icon> 预览结果
          </el-button>
        </div>
      </div>

      <div class="editor-content">
        <el-input type="textarea" v-model="sqlQuery" :rows="15"
          placeholder="在此输入SQL查询语句，或从左侧选择表和列自动生成..."
          class="sql-textarea" />
      </div>

      <!-- 查询历史 -->
      <el-collapse v-if="queryHistory.length > 0" class="query-history">
        <el-collapse-item title="查询历史" name="history">
          <div v-for="(history, index) in queryHistory" :key="index"
            class="history-item"
            @click="loadHistoryQuery(history)">
            <div class="history-header">
              <span class="history-name">{{ history.name }}</span>
              <span class="history-time">{{ formatTime(history.executedAt) }}</span>
            </div>
            <div class="history-sql">{{ history.sqlQuery.substring(0, 100) }}...</div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <!-- 预览结果 -->
      <div v-if="previewResult" class="preview-result">
        <div class="result-header">
          <h5>📊 查询结果预览（前20行）</h5>
          <el-tag type="success">共 {{ previewResult.rowCount }} 行</el-tag>
        </div>
        <el-table :data="previewResult.rows" stripe max-height="400"
          class="result-table">
          <el-table-column
            v-for="col in previewResult.columnNames"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
            show-overflow-tooltip />
        </el-table>
      </div>
    </div>
  </div>

  <template #footer>
    <el-button @click="queryBuilderVisible = false">关闭</el-button>
    <el-button @click="saveQuery">保存查询</el-button>
    <el-button type="primary" @click="importAsDataset">
      <el-icon><FolderOpened /></el-icon> 导入为数据集
    </el-button>
  </template>
</el-dialog>
```

#### 样式实现

```css
/* 查询构建器布局 */
.query-builder-layout {
  display: flex;
  gap: 20px;
  height: calc(100vh - 200px);
}

/* 左侧数据浏览器 */
.data-browser {
  flex: 0 0 350px;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 12px;
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.browser-header {
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.browser-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--primary-color);
}

.browser-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-tree {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  transition: all 0.2s ease;
  cursor: pointer;
}

.tree-node:hover {
  background: #f0f7ff;
}

.node-icon {
  font-size: 16px;
}

.node-label {
  font-size: 14px;
  color: #374151;
}

/* 列信息面板 */
.columns-panel {
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
  max-height: 400px;
  overflow-y: auto;
}

.panel-header {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e2e8f0;
}

.panel-header h5 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
}

.columns-list {
  padding: 12px;
}

.column-item {
  padding: 10px 12px;
  background: white;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.column-item:hover {
  background: #f0f7ff;
  transform: translateX(4px);
}

.column-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.column-name {
  font-weight: 600;
  color: var(--primary-color);
  font-size: 13px;
}

.column-desc {
  font-size: 11px;
  color: #64748b;
}

/* 右侧SQL编辑器 */
.sql-editor-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 12px;
  box-shadow: var(--card-shadow);
  overflow: hidden;
}

.editor-header {
  padding: 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.editor-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--primary-color);
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.editor-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.sql-textarea {
  font-family: 'Monaco', 'Menlo', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.sql-textarea :deep(textarea) {
  font-family: inherit;
  font-size: inherit;
}

/* 查询历史 */
.query-history {
  border-top: 1px solid #e2e8f0;
  padding: 0 16px;
}

.history-item {
  padding: 12px;
  background: #f8fafc;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-item:hover {
  background: #e6f0ff;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.history-name {
  font-weight: 600;
  color: var(--primary-color);
  font-size: 13px;
}

.history-time {
  font-size: 11px;
  color: #64748b;
}

.history-sql {
  font-size: 12px;
  color: #374151;
  font-family: 'Monaco', monospace;
}

/* 预览结果 */
.preview-result {
  border-top: 1px solid #e2e8f0;
  padding: 16px;
  background: #f8fafc;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.result-header h5 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
}

.result-table {
  border-radius: 8px;
  overflow: hidden;
}
```

---

## 🧩 三、前端组件架构设计

### 3.1 新增组件清单

| 组件名称 | 路径 | 功能描述 |
|----------|------|----------|
| `PromptTemplateCard.vue` | `components/templates/` | 提示词模板卡片 |
| `RuleModeSelector.vue` | `components/labels/` | 规则模式选择器 |
| `BuiltinRuleSelector.vue` | `components/labels/` | 内置规则选择器 |
| `QueryBuilder.vue` | `components/datasources/` | SQL查询构建器 |
| `DatabaseBrowser.vue` | `components/datasources/` | 数据库浏览器 |
| `SqlEditor.vue` | `components/datasources/` | SQL编辑器 |
| `TestResultPanel.vue` | `components/common/` | 测试结果面板 |

### 3.2 目录结构

```
frontend/src/
├── api/
│   ├── promptTemplates.ts      # 提示词模板API
│   ├── builtinRules.ts          # 内置规则API
│   └── dataSources.ts           # 数据源API
├── components/
│   ├── templates/
│   │   ├── PromptTemplateCard.vue
│   │   └── TemplateTestDialog.vue
│   ├── labels/
│   │   ├── RuleModeSelector.vue
│   │   ├── BuiltinRuleSelector.vue
│   │   ├── RuleConfigForm.vue
│   │   └── LabelTestResult.vue
│   ├── datasources/
│   │   ├── DataSourceCard.vue
│   │   ├── DatabaseBrowser.vue
│   │   ├── SqlEditor.vue
│   │   ├── QueryPreview.vue
│   │   └── ConnectionTestDialog.vue
│   └── common/
│       ├── TestResultPanel.vue
│       └── CodeHighlight.vue
├── views/
│   ├── PromptTemplatesView.vue
│   ├── LabelsView.vue (升级)
│   └── DataSourcesView.vue
└── types/
    ├── templates.ts
    ├── rules.ts
    └── datasources.ts
```

---

## 📱 四、响应式设计

### 4.1 断点定义

```css
/* 断点 */
@media (max-width: 1920px) { /* 大屏幕 */ }
@media (max-width: 1440px) { /* 中等屏幕 */ }
@media (max-width: 1024px) { /* 小屏幕 */ }
@media (max-width: 768px)  { /* 平板 */ }
@media (max-width: 480px)  { /* 手机 */ }
```

### 4.2 响应式适配策略

| 组件 | 大屏幕 (≥1440px) | 中等屏幕 (1024-1440px) | 小屏幕 (≤1024px) |
|------|------------------|------------------------|-------------------|
| **模板卡片** | Grid 3列 | Grid 2列 | Grid 1列 |
| **查询构建器** | 左右分栏 | 左右分栏（可折叠） | 上下堆叠 |
| **规则选择器** | 横向排列 | 横向排列（紧凑） | 纵向堆叠 |
| **对话框** | 宽度 900px | 宽度 700px | 全屏 |

---

## ⚡ 五、性能优化建议

### 5.1 列表虚拟滚动

```vue
<!-- 大数据量列表使用虚拟滚动 -->
<el-table-v2
  :columns="columns"
  :data="items"
  :width="1200"
  :height="600"
  fixed
/>
```

### 5.2 防抖与节流

```typescript
import { useDebounceFn, useThrottleFn } from '@vueuse/core'

// 搜索输入防抖
const handleSearch = useDebounceFn(() => {
  fetchList()
}, 500)

// 滚动加载节流
const handleScroll = useThrottleFn(() => {
  loadMore()
}, 200)
```

### 5.3 组件懒加载

```typescript
// 路由懒加载
const PromptTemplatesView = () => import('@/views/PromptTemplatesView.vue')
const DataSourcesView = () => import('@/views/DataSourcesView.vue')
```

---

## 🎭 六、动画与交互效果

### 6.1 页面切换动画

```css
/* 淡入淡出 */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

/* 滑动进入 */
.slide-enter-active, .slide-leave-active {
  transition: all 0.3s ease;
}
.slide-enter-from {
  transform: translateX(-20px);
  opacity: 0;
}
.slide-leave-to {
  transform: translateX(20px);
  opacity: 0;
}
```

### 6.2 加载状态

```vue
<!-- 骨架屏 -->
<el-skeleton :rows="5" animated v-if="loading" />

<!-- 或进度条 -->
<el-progress :percentage="progress" :stroke-width="12" />
```

---

## 📊 七、数据可视化建议

### 7.1 统计图表（使用 ECharts）

```typescript
// 使用统计折线图
const usageChartOption = {
  title: { text: '模板使用次数趋势' },
  xAxis: { type: 'category', data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] },
  yAxis: { type: 'value' },
  series: [{
    data: [120, 200, 150, 80, 70, 110, 130],
    type: 'line',
    smooth: true,
    itemStyle: { color: '#3182ce' }
  }]
}
```

### 7.2 成功率仪表盘

```typescript
const successRateGaugeOption = {
  series: [{
    type: 'gauge',
    detail: { formatter: '{value}%' },
    data: [{ value: 85, name: '成功率' }],
    axisLine: {
      lineStyle: {
        color: [[0.3, '#e53e3e'], [0.7, '#d69e2e'], [1, '#38a169']]
      }
    }
  }]
}
```

---

## 📝 八、表单验证规范

### 8.1 验证规则

```typescript
const templateNameRule = [
  { required: true, message: '请输入模板名称', trigger: 'blur' },
  { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' }
]

const sqlQueryRule = [
  { required: true, message: '请输入SQL查询语句', trigger: 'blur' },
  {
    validator: (rule, value, callback) => {
      const trimmed = value.trim().toUpperCase()
      if (!trimmed.startsWith('SELECT')) {
        callback(new Error('只允许SELECT查询'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }
]
```

---

## 🎨 九、可访问性 (A11y)

### 9.1 键盘导航

- 所有交互元素支持 Tab 键导航
- 快捷键支持（如 Ctrl+S 保存）
- 焦点状态清晰可见

### 9.2 语义化HTML

```vue
<!-- 使用语义化标签 -->
<header>
  <nav>
    <ul>
      <li><a href="/datasets">数据集</a></li>
    </ul>
  </nav>
</header>

<main>
  <article>
    <h1>提示词模板管理</h1>
  </article>
</main>

<footer>...</footer>
```

---

## 🧪 十、测试建议

### 10.1 单元测试

```typescript
import { mount } from '@vue/test-utils'
import RuleModeSelector from '@/components/labels/RuleModeSelector.vue'

describe('RuleModeSelector', () => {
  it('emits selection when a mode is clicked', async () => {
    const wrapper = mount(RuleModeSelector)
    await wrapper.find('.rule-mode-option').trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
  })
})
```

### 10.2 E2E 测试

```typescript
// 使用 Cypress 测试完整流程
describe('Create Label with Rule Mode', () => {
  it('should create a label with rule mode', () => {
    cy.visit('/labels')
    cy.get('[data-test="create-label-btn"]').click()
    cy.get('[data-test="rule-mode-rule"]').click()
    cy.get('[data-test="builtin-rule-select"]').click()
    cy.get('.el-select-dropdown__item').first().click()
    cy.get('[data-test="save-btn"]').click()
    cy.contains('创建成功')
  })
})
```

---

## ✅ 十一、设计检查清单

在开发每个页面时，请确保：

- [ ] 遵循配色方案和视觉风格
- [ ] 实现响应式布局
- [ ] 添加加载状态和错误处理
- [ ] 表单验证完善
- [ ] 操作有明确的反馈（成功/失败提示）
- [ ] 支持键盘导航
- [ ] 组件代码可复用
- [ ] 性能优化（懒加载、虚拟滚动）
- [ ] 测试覆盖（单元测试 + E2E测试）
- [ ] 文档注释完善

---

**文档版本**：V1.0
**最后更新**：2025-12-29
**设计团队**：智能数据标注平台前端组
