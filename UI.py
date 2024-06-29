import datetime
import tkinter as tk
from tkinter import messagebox
def save_result():
    text = entry.get()
    if text:
        with open("text.txt", "a", encoding="utf-8") as f:
            f.write(f'{text}\n')
        messagebox.showinfo("提示", "保存成功！")
    else:
        messagebox.showinfo("警告", "文本为空！")


# Create the main window
root = tk.Tk()
root.title("调试跟踪表")
root.geometry('500x500')
# 创建一个Frame作为网格容器
grid_frame = tk.Frame(root)

# 使用grid()方法将网格添加到窗口中
grid_frame.grid()

# Create a label widget
label = tk.Label(grid_frame, text="噪声值（dB）")

# Pack the label into the main window
label.grid(row=0, column=0)
# Create an entry widget
entry = tk.Entry(grid_frame)

# Pack the entry widget into the main window
entry.grid(row=0, column=1, padx=1)
# Create a button widget
button = tk.Button(grid_frame, text="保存", command=save_result)

# Pack the button into the main window
button.grid(row=0, column=2)

# Start the Tkinter event loop
root.mainloop()
