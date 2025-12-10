import subprocess
import sys
import os
import time

# 获取当前脚本所在目录
base_dir = os.path.dirname(os.path.abspath(__file__))
clustering_web_dir = os.path.join(base_dir, 'clustering_web')

# 切换到clustering_web目录
os.chdir(clustering_web_dir)
print(f"当前工作目录: {os.getcwd()}")

# 执行数据库迁移
print("\n正在执行数据库迁移...")
migrate_result = subprocess.run(
    [sys.executable, 'manage.py', 'migrate'],
    capture_output=True,
    text=True
)
print(f"迁移输出: {migrate_result.stdout}")
if migrate_result.stderr:
    print(f"迁移错误: {migrate_result.stderr}")

# 执行Django开发服务器命令
print("\n正在启动Django开发服务器...")
print("访问地址: http://127.0.0.1:8000/")
print("按 Ctrl+C 停止服务器")
print("=" * 50)

try:
    server_process = subprocess.Popen(
        [sys.executable, 'manage.py', 'runserver', '8000'],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    # 等待服务器启动
    time.sleep(2)
    
    # 检查服务器状态
    if server_process.poll() is None:
        print("Django服务器已成功启动!")
        print("可以访问 http://127.0.0.1:8000/ 查看Web UI")
        
        # 保持脚本运行
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n正在停止服务器...")
            server_process.terminate()
            server_process.wait()
            print("服务器已停止")
    else:
        print("服务器启动失败")
        stdout, stderr = server_process.communicate()
        print(f"输出: {stdout}")
        print(f"错误: {stderr}")
        
except Exception as e:
    print(f"启动服务器时出错: {e}")
