import requests
import json

def classify_text(text, categories):
    """使用Deepseek模型进行文本分类的简单实现"""
    # Ollama API URL
    url = "http://127.0.0.1:11434/api/generate"
    
    # 构建提示词
    prompt = f"请将以下文本分类到给定类别之一，只返回类别名称：\n\n文本：{text}\n类别：{', '.join(categories)}\n\n分类结果："
    
    # 请求数据
    data = {
        "model": "deepseek-r1:1.5b",
        "prompt": prompt,
        "stream": False,
        "temperature": 0.1
    }
    
    try:
        # 发送请求（添加超时）
        response = requests.post(url, json=data, timeout=30)
        
        # 解析响应
        result = response.json()
        category = result.get("response", "").strip()
        
        # 确保结果在类别列表中
        if category in categories:
            return category
        else:
            # 尝试匹配部分结果
            for cat in categories:
                if cat in category:
                    return cat
            return "未分类"
            
    except requests.exceptions.Timeout:
        print("错误：请求超时")
        return None
    except requests.exceptions.ConnectionError:
        print("错误：无法连接到服务器")
        return None
    except Exception as e:
        print(f"错误：{str(e)}")
        return None

# 测试
if __name__ == "__main__":
    print("=== Deepseek 文本分类测试 ===")
    
    # 测试用例
    test_text = "这款手机续航时间长，拍照效果优秀。"
    test_categories = ["电子产品", "餐饮", "旅游", "体育", "教育"]
    
    print(f"文本：{test_text}")
    print(f"类别：{', '.join(test_categories)}")
    
    result = classify_text(test_text, test_categories)
    
    if result:
        print(f"\n✅ 分类结果：{result}")
    else:
        print("\n❌ 分类失败")
