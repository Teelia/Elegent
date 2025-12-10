import requests
import numpy as np
from sklearn.cluster import KMeans

class SimpleDeepSeekClustering:
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
            return None
        
        # 获取嵌入向量
        embeddings = self.get_embeddings(texts)
        if len(embeddings) == 0:
            print("错误: 未能获取任何嵌入向量")
            return None
        
        # 执行K-means聚类
        print(f"\n正在执行K-means聚类，簇数: {n_clusters}")
        kmeans = KMeans(n_clusters=n_clusters, random_state=42)
        clusters = kmeans.fit_predict(embeddings)
        
        # 打印聚类结果
        print("\n=== 聚类结果 ===")
        for cluster_id in np.unique(clusters):
            print(f"\n簇 {cluster_id}:")
            cluster_texts = [texts[i] for i, c in enumerate(clusters) if c == cluster_id]
            for text in cluster_texts:
                print(f"  - {text}")
        
        # 返回聚类标签和嵌入向量
        return {
            "clusters": clusters,
            "embeddings": embeddings,
            "texts": texts
        }

# 示例用法
if __name__ == "__main__":
    # 测试文本数据 - 按主题分类
    test_texts = [
        # 电子产品
        "这款手机续航时间长，拍照效果优秀。",
        "这款笔记本电脑性能强劲，外观时尚。",
        "这款游戏主机画面渲染效果出色，运行流畅。",
        
        # 影视娱乐
        "这部电影剧情紧凑，演员演技出色。",
        "这部电视剧制作精良，故事情节引人入胜。",
        "这首歌旋律优美，歌词感人，深受听众喜爱。",
        
        # 餐饮美食
        "这道菜味道鲜美，食材新鲜，制作精细。",
        "这家餐厅环境优雅，服务周到，菜品丰富。",
        "这家咖啡店的咖啡香气浓郁，口感醇厚。",
        
        # 旅游住宿
        "这个景区风景秀丽，值得一游。",
        "这家酒店设施齐全，位置便利，服务热情。",
        "这个度假村环境优美，设施完善，适合度假休闲。"
    ]
    
    # 创建聚类分析器
    clustering = SimpleDeepSeekClustering()
    
    # 执行聚类
    result = clustering.cluster_texts(test_texts, n_clusters=4)
    
    if result:
        print(f"\n聚类完成！共处理 {len(result['texts'])} 条文本，分为 {len(np.unique(result['clusters']))} 个簇。")
