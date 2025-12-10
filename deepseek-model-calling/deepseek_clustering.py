import requests
import numpy as np
from sklearn.cluster import KMeans
import matplotlib.pyplot as plt
from sklearn.manifold import TSNE

class DeepSeekClustering:
    def __init__(self, model_name="deepseek-r1:1.5b", api_url="http://127.0.0.1:11434/api"):
        """初始化DeepSeek聚类分析器"""
        self.model_name = model_name
        self.api_url = api_url
        self.embeddings_endpoint = f"{api_url}/embeddings"
        self.tags_endpoint = f"{api_url}/tags"
    
    def check_server_status(self):
        """检查Ollama服务器是否正在运行"""
        try:
            response = requests.get(self.tags_endpoint, timeout=5)
            return response.status_code == 200
        except requests.exceptions.ConnectionError:
            return False
    
    def get_embedding(self, text):
        """获取单条文本的嵌入向量"""
        try:
            payload = {
                "model": self.model_name,
                "prompt": text
            }
            response = requests.post(self.embeddings_endpoint, json=payload, timeout=10)
            response.raise_for_status()
            result = response.json()
            return result.get("embedding", [])
        except Exception as e:
            print(f"获取嵌入向量失败: {str(e)}")
            return []
    
    def get_embeddings(self, texts):
        """获取多条文本的嵌入向量"""
        embeddings = []
        for i, text in enumerate(texts):
            print(f"正在处理文本 {i+1}/{len(texts)}: {text[:30]}...")
            embedding = self.get_embedding(text)
            if embedding:
                embeddings.append(embedding)
        return np.array(embeddings)
    
    def cluster_texts(self, texts, n_clusters=3):
        """对文本进行聚类分析"""
        # 检查服务器状态
        if not self.check_server_status():
            print("错误: 无法连接到Ollama服务器，请确保服务器已启动")
            return None, None
        
        # 获取嵌入向量
        embeddings = self.get_embeddings(texts)
        if len(embeddings) == 0:
            print("错误: 未能获取任何嵌入向量")
            return None, None
        
        # 执行K-means聚类
        print(f"正在执行K-means聚类，簇数: {n_clusters}")
        kmeans = KMeans(n_clusters=n_clusters, random_state=42)
        clusters = kmeans.fit_predict(embeddings)
        
        return clusters, embeddings
    
    def visualize_clusters(self, embeddings, clusters, texts, title="文本聚类可视化"):
        """可视化聚类结果"""
        # 使用TSNE降维到2D空间
        print("正在使用TSNE进行降维...")
        tsne = TSNE(n_components=2, random_state=42, perplexity=min(5, len(embeddings)-1))
        embeddings_2d = tsne.fit_transform(embeddings)
        
        # 创建散点图
        plt.figure(figsize=(12, 8))
        
        # 为每个簇绘制点
        unique_clusters = np.unique(clusters)
        colors = plt.cm.get_cmap('viridis', len(unique_clusters))
        
        for cluster_id in unique_clusters:
            indices = np.where(clusters == cluster_id)
            plt.scatter(
                embeddings_2d[indices, 0], 
                embeddings_2d[indices, 1], 
                c=[colors(cluster_id)], 
                label=f'簇 {cluster_id}',
                alpha=0.7
            )
            
            # 添加文本标签（可选，只显示前10个点）
            for i, idx in enumerate(indices[0][:10]):
                plt.annotate(
                    texts[idx][:15] + "...",
                    (embeddings_2d[idx, 0], embeddings_2d[idx, 1]),
                    fontsize=8,
                    alpha=0.7
                )
        
        plt.title(title)
        plt.legend()
        plt.grid(True, alpha=0.3)
        plt.savefig("clustering_result.png", dpi=300, bbox_inches='tight')
        print("聚类可视化已保存到 clustering_result.png")
        plt.show()

# 示例用法
if __name__ == "__main__":
    # 测试文本数据
    test_texts = [
        "这款手机续航时间长，拍照效果优秀。",
        "这部电影剧情紧凑，演员演技出色。",
        "这道菜味道鲜美，食材新鲜。",
        "这家餐厅环境优雅，服务周到。",
        "这本书内容丰富，观点独特。",
        "这部小说情节跌宕起伏，引人入胜。",
        "这款笔记本电脑性能强劲，外观时尚。",
        "这个景区风景秀丽，值得一游。",
        "这款游戏画面精美，玩法创新。",
        "这门课程内容实用，老师讲解清晰。",
        "这家酒店设施齐全，位置便利。",
        "这首歌旋律优美，歌词感人。"
    ]
    
    # 创建聚类分析器
    clustering = DeepSeekClustering()
    
    # 执行聚类
    clusters, embeddings = clustering.cluster_texts(test_texts, n_clusters=4)
    
    if clusters is not None and embeddings is not None:
        # 打印聚类结果
        print("\n=== 聚类结果 ===")
        for cluster_id in np.unique(clusters):
            print(f"\n簇 {cluster_id}:")
            cluster_texts = [test_texts[i] for i, c in enumerate(clusters) if c == cluster_id]
            for text in cluster_texts:
                print(f"  - {text}")
        
        # 可视化聚类结果
        clustering.visualize_clusters(embeddings, clusters, test_texts)
