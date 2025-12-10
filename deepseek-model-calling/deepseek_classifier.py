import requests
import json
import sys

def check_server_status(url="http://127.0.0.1:11434"):
    """检查Ollama服务器是否正在运行"""
    print(f"Debug: 检查服务器状态: {url}")
    try:
        # 添加超时设置
        response = requests.get(f"{url}/api/tags", timeout=10)
        response.raise_for_status()
        return True, "服务器运行正常"
    except requests.exceptions.Timeout:
        return False, "服务器连接超时"
    except requests.exceptions.ConnectionError:
        return False, "无法连接到服务器，请确保Ollama已启动"
    except requests.exceptions.HTTPError:
        return False, "服务器响应错误"
    except Exception as e:
        return False, f"服务器检查失败: {str(e)}"

def check_model_exists(model_name="deepseek-r1:1.5b", url="http://127.0.0.1:11434"):
    """检查指定模型是否已安装"""
    print(f"Debug: 检查模型是否存在: {model_name}")
    try:
        # 添加超时设置
        response = requests.get(f"{url}/api/tags", timeout=10)
        response.raise_for_status()
        models = response.json().get("models", [])
        print(f"Debug: 已安装的模型: {[model.get('name') for model in models]}")
        for model in models:
            if model.get("name") == model_name:
                return True, "模型已安装"
        return False, f"模型 {model_name} 未安装，请使用 'ollama pull {model_name}' 安装"
    except requests.exceptions.Timeout:
        return False, "模型检查超时"
    except Exception as e:
        return False, f"检查模型失败: {str(e)}"

def classify_text(text, categories, model_name="deepseek-r1:1.5b", url="http://127.0.0.1:11434"):
    """使用Deepseek模型进行文本分类"""
    print("Debug: 开始文本分类")
    
    # 检查服务器状态
    print("Debug: 执行服务器状态检查")
    server_status, server_msg = check_server_status(url)
    if not server_status:
        raise ConnectionError(server_msg)
    print(f"Debug: 服务器状态: {server_msg}")
    
    # 检查模型是否存在
    print("Debug: 执行模型存在检查")
    model_status, model_msg = check_model_exists(model_name, url)
    if not model_status:
        raise ValueError(model_msg)
    print(f"Debug: 模型状态: {model_msg}")
    
    # 构建分类请求
    api_url = f"{url}/api/generate"
    headers = {"Content-Type": "application/json"}
    
    # 构建提示词
    prompt = f"请将文本 '{text}' 分类到以下类别之一：{', '.join(categories)}。只返回类别名称，不要添加任何解释或额外内容。"
    
    data = {
        "model": model_name,
        "prompt": prompt,
        "stream": False,
        "temperature": 0.1,  # 设置低温度以获得更确定性的结果
        "max_tokens": 50     # 限制输出长度
    }
    
    try:
        # 添加超时设置
        response = requests.post(api_url, headers=headers, json=data, timeout=30)
        print(f"Debug: 请求状态码: {response.status_code}")
        print(f"Debug: 响应内容: {response.text}")
        
        response.raise_for_status()
        result = response.json()
        
        # 提取并清理分类结果
        category = result.get("response", "").strip()
        print(f"Debug: 原始分类结果: '{category}'")
        
        # 确保结果在指定类别中
        if category in categories:
            return category
        else:
            # 如果模型返回的结果不在指定类别中，尝试匹配最接近的
            for cat in categories:
                if cat in category:
                    return cat
            return "未分类"
            
    except requests.exceptions.Timeout:
        raise TimeoutError("API请求超时，请检查模型服务器性能")
    except requests.exceptions.HTTPError as http_err:
        raise Exception(f"API请求错误: {str(http_err)}")
    except json.JSONDecodeError as json_err:
        raise ValueError(f"服务器返回无效的JSON响应: {str(json_err)}")
    except Exception as e:
        raise Exception(f"分类失败: {str(e)}")

# 主函数，用于演示
if __name__ == "__main__":
    print("=== Deepseek 文本分类演示 ===")
    
    # 示例使用
    text = "这款手机续航时间长，拍照效果优秀。"
    categories = ["电子产品", "餐饮", "旅游", "体育", "教育"]
    
    try:
        print(f"待分类文本: {text}")
        print(f"分类类别: {', '.join(categories)}")
        
        result = classify_text(text, categories)
        print(f"\n✅ 分类结果: {result}")
        
    except Exception as e:
        print(f"\n❌ 错误: {str(e)}")
        sys.exit(1)
