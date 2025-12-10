from django.shortcuts import render
from django.http import JsonResponse
import requests
import numpy as np
from sklearn.cluster import KMeans
from .models import ClusteringResult


def home(request):
    """首页视图"""
    return render(request, 'clustering/home.html')


def get_embedding(text):
    """获取单条文本的嵌入向量"""
    try:
        payload = {
            "model": "deepseek-r1:1.5b",
            "prompt": text
        }
        response = requests.post(
            "http://127.0.0.1:11434/api/embeddings",
            json=payload,
            timeout=10
        )
        response.raise_for_status()
        result = response.json()
        return result.get("embedding", [])
    except Exception as e:
        print(f"获取嵌入向量失败: {str(e)}")
        return []


def cluster_texts(request):
    """聚类分析视图"""
    if request.method == 'POST':
        # 获取表单数据
        texts = request.POST.get('texts', '').strip()
        n_clusters = int(request.POST.get('n_clusters', 3))
        
        # 解析文本列表
        text_list = [text.strip() for text in texts.split('\n') if text.strip()]
        if not text_list:
            return JsonResponse({"error": "请输入至少一条文本"})
        
        # 获取嵌入向量
        embeddings = []
        valid_texts = []
        for text in text_list:
            embedding = get_embedding(text)
            if embedding:
                embeddings.append(embedding)
                valid_texts.append(text)
        
        if not embeddings:
            return JsonResponse({"error": "未能获取任何嵌入向量，请检查服务器连接"})
        
        # 执行聚类
        embeddings = np.array(embeddings)
        kmeans = KMeans(n_clusters=n_clusters, random_state=42)
        clusters = kmeans.fit_predict(embeddings)
        
        # 整理结果
        result = {}
        for cluster_id in range(n_clusters):
            result[cluster_id] = []
            for i, text in enumerate(valid_texts):
                if clusters[i] == cluster_id:
                    result[cluster_id].append(text)
        
        # 保存结果到数据库
        ClusteringResult.objects.create(
            n_clusters=n_clusters,
            total_texts=len(valid_texts),
            result_data=result
        )
        
        return JsonResponse({"success": True, "result": result})
    
    return JsonResponse({"error": "无效的请求方法"})


def check_server_status(request):
    """检查服务器状态"""
    try:
        response = requests.get("http://127.0.0.1:11434/api/tags", timeout=5)
        if response.status_code == 200:
            return JsonResponse({"status": "running"})
        else:
            return JsonResponse({"status": "error"})
    except requests.exceptions.ConnectionError:
        return JsonResponse({"status": "down"})
