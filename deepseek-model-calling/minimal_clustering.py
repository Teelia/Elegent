import requests
import numpy as np
from sklearn.cluster import KMeans

# 简单的聚类分析实现
def minimal_clustering():
    # 测试文本数据
    texts = [
        "这款手机续航时间长",
        "这部电影剧情紧凑",
        "这道菜味道鲜美",
        "这个景区风景秀丽",
        "这款笔记本电脑性能强劲",
        "这首歌旋律优美",
        "这家餐厅环境优雅",
        "这家酒店设施齐全"
    ]
    
    # 获取嵌入向量
    embeddings = []
    for text in texts:
        try:
            response = requests.post(
                "http://127.0.0.1:11434/api/embeddings",
                json={"model": "deepseek-r1:1.5b", "prompt": text},
                timeout=5
            )
            if response.status_code == 200:
                result = response.json()
                embeddings.append(result.get("embedding", []))
        except Exception as e:
            print(f"处理 '{text}' 失败: {e}")
    
    if not embeddings:
        print("未能获取任何嵌入向量")
        return
    
    # 执行聚类
    embeddings = np.array(embeddings)
    kmeans = KMeans(n_clusters=2, random_state=42)
    clusters = kmeans.fit_predict(embeddings)
    
    # 打印结果
    print("\n=== 聚类结果 ===")
    for cluster_id in np.unique(clusters):
        print(f"\n簇 {cluster_id}:")
        for i, text in enumerate(texts):
            if clusters[i] == cluster_id:
                print(f"  - {text}")

if __name__ == "__main__":
    minimal_clustering()
