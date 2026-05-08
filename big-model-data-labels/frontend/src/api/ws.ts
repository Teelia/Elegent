import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { TaskProgress } from './tasks'

export function createProgressClient(taskId: number, onMessage: (p: TaskProgress) => void) {
  const client = new Client({
    webSocketFactory: () => new SockJS('/api/ws'),
    reconnectDelay: 2000,
    debug: () => {},
  })

  client.onConnect = () => {
    client.subscribe(`/topic/tasks/${taskId}/progress`, (msg) => {
      try {
        onMessage(JSON.parse(msg.body))
      } catch {
        // ignore
      }
    })
  }

  return client
}

