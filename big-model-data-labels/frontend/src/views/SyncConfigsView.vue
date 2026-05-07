<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DbType, SyncConfig, TableSchema } from '../api/sync'
import * as syncApi from '../api/sync'

const loading = ref(false)
const items = ref<SyncConfig[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editing = ref<SyncConfig | null>(null)
const form = reactive({
  name: '',
  dbType: 'mysql' as DbType,
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  tableName: '',
})

const schemaVisible = ref(false)
const schema = ref<TableSchema | null>(null)

async function fetchList() {
  loading.value = true
  try {
    items.value = await syncApi.listSyncConfigs()
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
  })
  dialogVisible.value = true
}

function openEdit(row: SyncConfig) {
  dialogMode.value = 'edit'
  editing.value = row
  Object.assign(form, {
    name: row.name,
    dbType: row.dbType,
    host: row.host,
    port: row.port,
    databaseName: row.databaseName,
    username: row.username,
    password: '',
    tableName: row.tableName,
  })
  dialogVisible.value = true
}

async function submit() {
  try {
    if (dialogMode.value === 'create') {
      await syncApi.createSyncConfig({ ...form })
      ElMessage.success('创建成功')
    } else if (editing.value) {
      await syncApi.updateSyncConfig(editing.value.id, {
        ...form,
        password: form.password ? form.password : undefined,
      })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function onDelete(row: SyncConfig) {
  try {
    await ElMessageBox.confirm(`确认删除同步配置「${row.name}」？`, '提示', { type: 'warning' })
    await syncApi.deleteSyncConfig(row.id)
    ElMessage.success('删除成功')
    await fetchList()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(e?.message || '删除失败')
  }
}

async function openSchema(row: SyncConfig) {
  schemaVisible.value = true
  schema.value = null
  try {
    schema.value = await syncApi.getTableSchema(row.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '读取表结构失败')
  }
}

onMounted(fetchList)
</script>

<template>
  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
    <h2 style="margin:0">同步配置</h2>
    <el-button type="primary" @click="openCreate">新建配置</el-button>
  </div>

  <el-table :data="items" v-loading="loading" style="width: 100%">
    <el-table-column prop="name" label="名称" min-width="140" />
    <el-table-column prop="dbType" label="类型" width="110" />
    <el-table-column prop="host" label="主机" min-width="160" />
    <el-table-column prop="port" label="端口" width="90" />
    <el-table-column prop="databaseName" label="库名" min-width="140" />
    <el-table-column prop="tableName" label="表名" min-width="140" />
    <el-table-column label="操作" width="220">
      <template #default="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-button size="small" @click="openSchema(row)">表结构</el-button>
        <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>

  <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建同步配置' : '编辑同步配置'" width="560px">
    <el-form label-width="110px">
      <el-form-item label="名称">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="数据库类型">
        <el-select v-model="form.dbType" style="width: 100%">
          <el-option label="MySQL" value="mysql" />
          <el-option label="PostgreSQL" value="postgresql" />
          <el-option label="SQL Server" value="sqlserver" />
        </el-select>
      </el-form-item>
      <el-form-item label="主机">
        <el-input v-model="form.host" />
      </el-form-item>
      <el-form-item label="端口">
        <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
      </el-form-item>
      <el-form-item label="数据库名">
        <el-input v-model="form.databaseName" />
      </el-form-item>
      <el-form-item label="用户名">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password :placeholder="dialogMode === 'edit' ? '留空表示不更新' : ''" />
      </el-form-item>
      <el-form-item label="目标表名">
        <el-input v-model="form.tableName" placeholder="支持 schema.table" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="schemaVisible" title="目标表结构" size="40%">
    <el-table v-if="schema" :data="schema.columns" style="width: 100%">
      <el-table-column prop="name" label="字段" min-width="160" />
      <el-table-column prop="type" label="类型" min-width="120" />
      <el-table-column prop="nullable" label="可空" width="80">
        <template #default="{ row }">
          {{ row.nullable ? '是' : '否' }}
        </template>
      </el-table-column>
    </el-table>
    <div v-else style="color:#999">加载中或无数据</div>
  </el-drawer>
</template>

