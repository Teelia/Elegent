<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DataSource, DbType, OracleConnectionMode } from '../api/dataSources'
import * as dataSourcesApi from '../api/dataSources'

const loading = ref(false)
const items = ref<DataSource[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editing = ref<DataSource | null>(null)

// 表单数据
const form = reactive<{
  name: string
  dbType: DbType
  host: string
  port: number
  databaseName: string
  username: string
  password: string
  tableName: string
  importQuery: string
  timestampColumn: string
  // Oracle 专用
  connectionMode: OracleConnectionMode
  oracleSid: string
  oracleServiceName: string
}>({
  name: '',
  dbType: 'mysql',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  tableName: '',
  importQuery: '',
  timestampColumn: '',
  connectionMode: 'standard',
  oracleSid: '',
  oracleServiceName: '',
})

// 端口默认值
const portDefaults: Record<DbType, number> = {
  mysql: 3306,
  postgresql: 5432,
  sqlserver: 1433,
  oracle: 1521,
}

async function fetchList() {
  loading.value = true
  try {
    items.value = await dataSourcesApi.listDataSources()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editing.value = null
  Object.assign(form, {
    name: '',
    dbType: 'mysql',
    host: '',
    port: 3306,
    databaseName: '',
    username: '',
    password: '',
    tableName: '',
    importQuery: '',
    timestampColumn: '',
    connectionMode: 'standard',
    oracleSid: '',
    oracleServiceName: '',
  })
  dialogVisible.value = true
}

function openEdit(row: DataSource) {
  dialogMode.value = 'edit'
  editing.value = row
  Object.assign(form, {
    name: row.name,
    dbType: row.dbType,
    host: row.host,
    port: row.port,
    databaseName: row.databaseName || '',
    username: row.username,
    password: '', // 编辑时不显示密码
    tableName: row.tableName || '',
    importQuery: row.importQuery || '',
    timestampColumn: row.timestampColumn || '',
    connectionMode: row.connectionMode || 'standard',
    oracleSid: row.oracleSid || '',
    oracleServiceName: row.oracleServiceName || '',
  })
  dialogVisible.value = true
}

// 当数据库类型改变时，更新默认端口
function onDbTypeChange() {
  form.port = portDefaults[form.dbType]
}

async function submit() {
  try {
    const data = { ...form }

    if (dialogMode.value === 'create') {
      await dataSourcesApi.createDataSource(data)
      ElMessage.success('创建成功')
    } else if (editing.value) {
      // 编辑时，如果密码为空则不更新密码
      await dataSourcesApi.updateDataSource(editing.value.id, {
        ...data,
        password: data.password || undefined,
      })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function onDelete(row: DataSource) {
  try {
    await ElMessageBox.confirm(`确认删除数据源「${row.name}」？`, '提示', {
      type: 'warning'
    })
    await dataSourcesApi.deleteDataSource(row.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '删除失败')
  }
}

const testing = ref(false)

async function onTestConnection(row: DataSource) {
  testing.value = true
  try {
    const result = await dataSourcesApi.testConnection(row.id)
    if (result.success) {
      ElMessage.success('连接测试成功')
      // 更新本地状态
      row.connectionTestStatus = 'success'
      row.connectionTestTime = new Date().toISOString()
    } else {
      ElMessage.error(`连接测试失败: ${result.message}`)
      row.connectionTestStatus = 'failed'
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '测试失败')
  } finally {
    testing.value = false
  }
}

// 计算属性：是否显示 Oracle 配置
const showOracleConfig = computed(() => form.dbType === 'oracle')

// 计算属性：是否显示数据库名
const showDatabaseName = computed(() => form.dbType !== 'oracle')

onMounted(fetchList)
</script>

<template>
  <div class="data-sources-view">
    <div class="header">
      <h2>数据源管理</h2>
      <el-button type="primary" @click="openCreate">新建数据源</el-button>
    </div>

    <el-table :data="items" v-loading="loading" style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="dbType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.dbType.toUpperCase() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="host" label="主机" min-width="140" />
      <el-table-column prop="port" label="端口" width="80" />
      <el-table-column prop="databaseName" label="数据库" min-width="100" />
      <el-table-column prop="tableName" label="表名" min-width="120" />
      <el-table-column prop="connectionTestStatus" label="连接状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.connectionTestStatus === 'success'" type="success" size="small">
            正常
          </el-tag>
          <el-tag v-else-if="row.connectionTestStatus === 'failed'" type="danger" size="small">
            失败
          </el-tag>
          <el-tag v-else type="info" size="small">未测试</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button size="small" @click="onTestConnection(row)" :loading="testing">
            测试连接
          </el-button>
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建数据源' : '编辑数据源'"
      width="640px"
    >
      <el-form label-width="140px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="数据源配置名称" />
        </el-form-item>

        <el-form-item label="数据库类型" required>
          <el-select v-model="form.dbType" style="width: 100%" @change="onDbTypeChange">
            <el-option label="MySQL" value="mysql" />
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="SQL Server" value="sqlserver" />
            <el-option label="Oracle" value="oracle" />
          </el-select>
        </el-form-item>

        <el-form-item label="主机地址" required>
          <el-input v-model="form.host" placeholder="localhost 或 IP 地址" />
        </el-form-item>

        <el-form-item label="端口" required>
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>

        <!-- 非 Oracle 数据库的数据库名字段 -->
        <el-form-item v-if="showDatabaseName" label="数据库名">
          <el-input v-model="form.databaseName" />
        </el-form-item>

        <!-- Oracle 连接模式配置 -->
        <template v-if="showOracleConfig">
          <el-form-item label="连接模式">
            <el-select v-model="form.connectionMode" style="width: 100%">
              <el-option label="标准模式（自动选择）" value="standard" />
              <el-option label="SID 模式" value="sid" />
              <el-option label="Service Name 模式" value="service_name" />
              <el-option label="TNS 模式" value="tns" />
            </el-select>
          </el-form-item>

          <el-form-item
            v-if="form.connectionMode === 'sid' || form.connectionMode === 'standard'"
            label="Oracle SID"
          >
            <el-input v-model="form.oracleSid" placeholder="例如: ORCL" />
          </el-form-item>

          <el-form-item
            v-if="form.connectionMode === 'service_name' || form.connectionMode === 'standard'"
            label="Service Name"
          >
            <el-input v-model="form.oracleServiceName" placeholder="例如: ORCLPDB" />
          </el-form-item>
        </template>

        <el-form-item label="用户名" required>
          <el-input v-model="form.username" />
        </el-form-item>

        <el-form-item label="密码" :required="dialogMode === 'create'">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="dialogMode === 'edit' ? '留空表示不更新' : ''"
          />
        </el-form-item>

        <el-form-item label="表名">
          <el-input v-model="form.tableName" placeholder="要导入的表名（支持 schema.table）" />
        </el-form-item>

        <el-form-item label="查询条件">
          <el-input
            v-model="form.importQuery"
            type="textarea"
            :rows="3"
            placeholder="可选的 WHERE 条件，例如：created_at > '2024-01-01' AND status = 'active'"
          />
        </el-form-item>

        <el-form-item label="时间戳列名">
          <el-input
            v-model="form.timestampColumn"
            placeholder="用于增量更新的时间戳列名，例如：created_at、updated_at"
          />
          <div class="form-hint">
            指定源表中的时间戳列，系统将基于此列进行增量更新筛选
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.data-sources-view {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
