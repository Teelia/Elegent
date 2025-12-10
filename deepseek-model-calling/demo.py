import requests
import json
def classify_text(text, categories):
    url = "http://127.0.0.1:11434/api/generate"
    headers = {
        "Content-Type": "application/json"
    }
    data = {
        "model":"deepseek-r1:1.5b",
        "text": text,
        "categories": categories,
        "temperature":0.7,
        "max_tokens": 256,
        "stream":False
    }
    response = requests.post(url, headers=headers, data=json.dumps(data))
    return response.json()
# 示例调用
text = "这款手机续航时间长，拍照效果优秀。"
categories = ["电子产品", "餐饮", "旅游"]
result = classify_text(text, categories)
print(result)