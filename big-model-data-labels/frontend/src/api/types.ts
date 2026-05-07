export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  pages: number
  hasNext: boolean
  hasPrevious: boolean
}

