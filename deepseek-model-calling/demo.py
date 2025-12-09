import requests
#api url
url='http://127.0.0.1:11434/api/chat'
#发送请求数据
data={
    "model":"deepseek-r1:1.5b",
    "messages":[
        {"role":"system","content":"你是一个资深的数据分析专家"},
        {"role":"user",
         "content":"数据分析专家是一名具有丰富的分析经验人士"}
    ],
    "stream":False
}

try:
    #向ollama发送POST请求
    response=requests.post(url,json=data)
    response.raise_for_status() #检查请求是否成功
    result=response.json() #直接解析响应的json内容
    #输出响应的内容
    print(result['message'],['content'])

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
