import { http, unwrap } from './http'

export type DbType = 'mysql' | 'postgresql' | 'sqlserver'

export interface SyncConfig {
  id: number
  userId: number
  name: string
  dbType: DbType
  host: string
  port: number
  databaseName: string
  username: string
  tableName: string
  fieldMappings: Record<string, unknown>
  isActive: boolean
  createdAt?: string
  updatedAt?: string
}

export interface TableColumn {
  name: string
  type: string
  nullable: boolean
}

export interface TableSchema {
  columns: TableColumn[]
}

export async function listSyncConfigs(params?: { userId?: number }): Promise<SyncConfig[]> {
  return unwrap(http.get('/sync-configs', { params }))
}

export async function createSyncConfig(payload: {
  name: string
  dbType: DbType
  host: string
  port: number
  databaseName: string
  username: string
  password: string
  tableName: string
}): Promise<SyncConfig> {
  return unwrap(http.post('/sync-configs', payload))
}

export async function updateSyncConfig(
  id: number,
  payload: {
    name: string
    dbType: DbType
    host: string
    port: number
    databaseName: string
    username: string
    password?: string
    tableName: string
  },
): Promise<SyncConfig> {
  return unwrap(http.put(`/sync-configs/${id}`, payload))
}

export async function deleteSyncConfig(id: number): Promise<void> {
  await unwrap(http.delete(`/sync-configs/${id}`))
}

export async function getTableSchema(id: number): Promise<TableSchema> {
  return unwrap(http.get(`/sync-configs/${id}/table-schema`))
}

export async function syncTask(
  taskId: number,
  payload: { syncConfigId: number; fieldMappings: Record<string, string>; strategy: 'insert' },
): Promise<void> {
  await unwrap(http.post(`/tasks/${taskId}/sync`, payload))
}

