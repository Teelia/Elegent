import requests
#api url
url='http://127.0.0.1:11434/api/generate'
#发送请求数据
def query_deepseek(user_prompt):
    data={
        "model":"deepseek-r1:1.5b",
        "prompt":user_prompt,
        "temperature":0.7,
        "max_tokens": 256,
        "stream":False
    }
    response=requests.post(url,json=data)  #向ollama发送POST请求
    response.raise_for_status() #检查请求是否成功
    return response.json()["response"]#直接解析响应的json内容

try:
    user_prom=input("请输入问题：")
    #输出响应的内容
    print(query_deepseek(user_prom))

except requests.exceptions.HTTPError as http_err:
    print(f"HTTP错误发生:{http_err}")
    
except requests.exceptions.ConnectionError as conn_err:
    print(f"连接错误发生:{conn_err}")

except requests.exceptions.Timeout as timeout_err:
    print(f"请求超时:{timeout_err}")

except requests.exceptions.RequestException as req_err:
    print(f"请求错误发生:{req_err}")

except KeyError as key_err:
    print(f"解析响应时发生键错误:{key_err}")

except Exception as err:
    print(f"其他错误发生:{err}")
