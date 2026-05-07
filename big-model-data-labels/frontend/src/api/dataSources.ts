import { http, unwrap } from './http'

// ==================== 类型定义 ====================

/**
 * 数据库类型
 */
export type DbType = 'mysql' | 'postgresql' | 'sqlserver' | 'oracle'

/**
 * 数据方向
 */
export type DataDirection = 'import' | 'export'

/**
 * Oracle 连接模式
 */
export type OracleConnectionMode = 'standard' | 'sid' | 'service_name' | 'tns'

/**
 * 连接测试状态
 */
export type ConnectionTestStatus = 'success' | 'failed' | 'unknown'

/**
 * 导入状态
 */
export type ImportStatus = 'pending' | 'importing' | 'completed' | 'failed'

/**
 * 增量更新模式
 */
export type IncrementUpdateMode = 'append' | 'replace'

/**
 * 外部数据源配置
 */
export interface DataSource {
  id: number
  userId: number
  name: string
  direction: DataDirection
  dbType: DbType
  host: string
  port: number
  databaseName?: string
  username: string
  passwordEncrypted?: string // 仅创建/更新时使用，不返回明文密码
  tableName?: string
  importQuery?: string
  timestampColumn?: string // 时间戳列名，用于增量更新

  // Oracle 专用字段
  oracleSid?: string
  oracleServiceName?: string
  connectionMode?: OracleConnectionMode

  // 状态字段
  isActive?: boolean
  connectionTestStatus?: ConnectionTestStatus
  connectionTestTime?: string
  importStatus?: ImportStatus
  lastImportTime?: string

  createdAt: string
  updatedAt?: string
}

/**
 * 数据库项
 */
export interface DatabaseItem {
  name: string
  tables: TableItem[]
}

/**
 * 表项
 */
export interface TableItem {
  name: string
  type: 'TABLE' | 'VIEW'
  rowCount?: number
  comment?: string
}

/**
 * 数据库浏览器响应
 */
export interface DatabaseExplorer {
  databases: DatabaseItem[]
}

/**
 * 表预览响应
 */
export interface TablePreview {
  tableName: string
  columns: string[]
  rows: Record<string, any>[]
  totalRows?: number
  sql: string
}

/**
 * 连接测试响应
 */
export interface ConnectionTestResult {
  success: boolean
  message: string
  latency?: number
}

/**
 * 创建数据源请求
 */
export interface CreateDataSourceRequest {
  name: string
  direction?: DataDirection
  dbType: DbType
  host: string
  port: number
  databaseName?: string
  username: string
  password: string
  tableName?: string
  importQuery?: string
  timestampColumn?: string

  // Oracle 专用
  oracleSid?: string
  oracleServiceName?: string
  connectionMode?: OracleConnectionMode
}

/**
 * 更新数据源请求
 */
export interface UpdateDataSourceRequest {
  name?: string
  direction?: DataDirection
  host?: string
  port?: number
  databaseName?: string
  username?: string
  password?: string // 可选，留空表示不更新
  tableName?: string
  importQuery?: string
  timestampColumn?: string

  // Oracle 专用
  oracleSid?: string
  oracleServiceName?: string
  connectionMode?: OracleConnectionMode
}

/**
 * 创建导入任务请求
 */
export interface CreateImportTaskRequest {
  tableName: string
  datasetName: string
  importQuery?: string
  description?: string
}

/**
 * 创建导入任务响应
 */
export interface CreateImportTaskResponse {
  datasetId: number
  status: string
  message: string
}

/**
 * 导入进度响应
 */
export interface ImportProgressResponse {
  datasetId: number
  status: 'importing' | 'uploaded' | 'failed'
  totalRows: number
  message: string
}

/**
 * 增量更新请求
 */
export interface IncrementUpdateRequest {
  mode: IncrementUpdateMode
}

/**
 * 增量更新响应
 */
export interface IncrementUpdateResponse {
  datasetId: number
  status: string
  mode: IncrementUpdateMode
  message: string
}

// ==================== API 函数 ====================

/**
 * 获取数据源列表（仅导入类型）
 */
export async function listDataSources(): Promise<DataSource[]> {
  return unwrap(http.get('/data-sources'))
}

/**
 * 获取数据源详情
 */
export async function getDataSource(id: number): Promise<DataSource> {
  return unwrap(http.get(`/data-sources/${id}`))
}

/**
 * 创建数据源
 */
export async function createDataSource(data: CreateDataSourceRequest): Promise<DataSource> {
  return unwrap(http.post('/data-sources', data))
}

/**
 * 更新数据源
 */
export async function updateDataSource(id: number, data: UpdateDataSourceRequest): Promise<DataSource> {
  return unwrap(http.put(`/data-sources/${id}`, data))
}

/**
 * 删除数据源
 */
export async function deleteDataSource(id: number): Promise<void> {
  await unwrap(http.delete(`/data-sources/${id}`))
}

/**
 * 测试数据源连接
 */
export async function testConnection(id: number): Promise<ConnectionTestResult> {
  return unwrap(http.post(`/data-sources/${id}/test`))
}

/**
 * 浏览数据库和表
 */
export async function exploreDatabases(id: number): Promise<DatabaseExplorer> {
  return unwrap(http.get(`/data-sources/${id}/explore`))
}

/**
 * 预览表数据
 */
export async function previewTableData(
  id: number,
  tableName: string,
  whereClause?: string
): Promise<TablePreview> {
  return unwrap(http.get(`/data-sources/${id}/preview`, {
    params: { tableName, whereClause }
  }))
}

/**
 * 创建导入任务
 */
export async function createImportTask(
  id: number,
  data: CreateImportTaskRequest
): Promise<CreateImportTaskResponse> {
  return unwrap(http.post(`/data-sources/${id}/import`, data))
}

/**
 * 增量更新数据集
 */
export async function incrementUpdateDataset(
  datasetId: number,
  data: IncrementUpdateRequest
): Promise<IncrementUpdateResponse> {
  return unwrap(http.post(`/data-sources/datasets/${datasetId}/increment-update`, data))
}

/**
 * 查询数据集导入进度
 */
export async function getImportProgress(datasetId: number): Promise<ImportProgressResponse> {
  return unwrap(http.get(`/datasets/${datasetId}/import-progress`))
}
