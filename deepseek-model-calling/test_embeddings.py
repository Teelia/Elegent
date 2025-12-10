import requests

# 测试Ollama API的embeddings端点
def test_embeddings():
    url = "http://127.0.0.1:11434/api/embeddings"
    
    # 测试数据
    payload = {
        "model": "deepseek-r1:1.5b",
        "prompt": "测试文本"
    }
    
    try:
        print("发送嵌入向量请求...")
        response = requests.post(url, json=payload, timeout=10)
        print(f"状态码: {response.status_code}")
        print(f"响应内容: {response.text}")
        
        if response.status_code == 200:
            result = response.json()
            if "embedding" in result:
                print(f"嵌入向量长度: {len(result['embedding'])}")
                print("嵌入向量API可用")
            else:
                print("响应中没有embedding字段")
        else:
            print(f"请求失败: {response.text}")
            
    except requests.exceptions.ConnectionError:
        print("无法连接到服务器")
    except Exception as e:
        print(f"测试失败: {str(e)}")

# 测试tags端点
def test_tags():
    url = "http://127.0.0.1:11434/api/tags"
    
    try:
        print("\n测试tags端点...")
        response = requests.get(url, timeout=5)
        print(f"状态码: {response.status_code}")
        if response.status_code == 200:
            result = response.json()
            print(f"已安装的模型: {[model.get('name') for model in result.get('models', [])]}")
        else:
            print(f"请求失败: {response.text}")
            
    except Exception as e:
        print(f"测试失败: {str(e)}")

if __name__ == "__main__":
    test_embeddings()
    test_tags()
