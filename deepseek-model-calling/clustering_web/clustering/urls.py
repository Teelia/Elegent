from django.urls import path
from . import views

urlpatterns = [
    path('', views.home, name='home'),
    path('cluster/', views.cluster_texts, name='cluster_texts'),
    path('check-server/', views.check_server_status, name='check_server_status'),
]
