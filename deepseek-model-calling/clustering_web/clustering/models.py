from django.db import models


class ClusteringResult(models.Model):
    """聚类结果模型"""
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='创建时间')
    n_clusters = models.IntegerField(verbose_name='簇数')
    total_texts = models.IntegerField(verbose_name='文本总数')
    result_data = models.JSONField(verbose_name='聚类结果数据')

    class Meta:
        verbose_name = '聚类结果'
        verbose_name_plural = '聚类结果'
