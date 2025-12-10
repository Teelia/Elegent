import os
import sys

# 确保在正确的目录下
os.chdir('clustering_web')

# 执行Django开发服务器
os.system(f"{sys.executable} manage.py runserver 8000")
