from fastapi import FastAPI, WebSocket, WebSocketDisconnect
import asyncio
app = FastAPI()
class ConnectionManager:
    def __init__(self):
        self.active_connections: list[WebSocket] = []
    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
    def disconnect(self, websocket: WebSocket):
        self.active_connections.remove(websocket)
manager = ConnectionManager()
@app.websocket("/stream")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            # 这里添加模型推理逻辑
            async for token in generate_tokens(data):
                await websocket.send_text(token)
    except WebSocketDisconnect:
        manager.disconnect(websocket)