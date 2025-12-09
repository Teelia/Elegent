import requests

def query_deepseek(system_prompt,user_prompt):
    url = "http://127.0.0.1:11434/api/chat"
    headers = {"Content-Type": "application/json"}
    data = {
        "model": "deepseek-r1:1.5b",

        "messages": [{"role":"system","content":system_prompt},
        {"role":"user","content":user_prompt}],
        "temperature": 0.7,
        "max_tokens": 256,
        "stream":False
        
    }
    response = requests.post(url, json=data, headers=headers)
    return response.json()["message"],["content"]

sys_prom=input("请输入限定词：")
user_prom=input("请输入问题：")

print(query_deepseek(sys_prom,user_prom))