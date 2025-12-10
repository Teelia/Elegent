import requests

def query_deepseek(system_prompt,user_prompt):
    url = "http://127.0.0.1:11434/api/chat"
    headers = {"Content-Type": "application/json"}
    data = {
        "model": "deepseek-r1:1.5b",
        "prompt":user_prompt,
        "messages": [{"role":"system","content":system_prompt},
        {"role":"user","content":user_prompt}],
        "stream":False   
    }
    response = requests.post(url, json=data, headers=headers)
    return response.json()["message"]["content"]

try:
    sys_prom=input("请输入限定词：")
    user_prom=input("请输入问题：")
    print(query_deepseek(sys_prom,user_prom))

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
