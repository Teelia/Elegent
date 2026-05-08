# Spring Boot 开发热部署配置指南

## 📋 概述

本项目已配置 Spring Boot DevTools 实现开发环境的热部署功能，支持代码修改后自动重启应用，无需手动重启服务器。

## ✅ 已完成的配置

### 1. Maven 依赖（pom.xml）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

### 2. Spring Boot Maven Plugin 配置

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <fork>true</fork>           <!-- 启用进程分离 -->
        <addResources>true</addResources>  <!-- 添加资源文件监控 -->
    </configuration>
</plugin>
```

### 3. application.yml 配置

```yaml
spring:
  devtools:
    restart:
      enabled: true  # 启用热重启
      additional-paths: src/main/java  # 监控Java源码目录
      exclude: static/**,public/**,templates/**  # 排除静态资源
      poll-interval: 1000  # 文件变化检测间隔（毫秒）
      quiet-period: 400  # 静默期，避免频繁重启
    livereload:
      enabled: true  # 启用LiveReload（前端资源自动刷新）
      port: 35729  # LiveReload服务端口
```

## 🔧 IDE 配置

### IntelliJ IDEA 配置（推荐）

#### 方式一：自动编译（推荐）

1. **启用自动编译**
   - 打开 `File` → `Settings` (Windows/Linux) 或 `IntelliJ IDEA` → `Preferences` (macOS)
   - 导航到 `Build, Execution, Deployment` → `Compiler`
   - 勾选 `Build project automatically`
   - 点击 `Apply` 和 `OK`

2. **启用运行时自动编译**
   - 按 `Ctrl + Shift + A` (Windows/Linux) 或 `Cmd + Shift + A` (macOS) 打开 Action 搜索
   - 输入 `Registry`
   - 找到并勾选 `compiler.automake.allow.when.app.running`
   - 关闭 Registry 窗口

3. **运行应用**
   - 使用 `Run` 模式（不是 Debug 模式）启动应用
   - 修改代码后，IDEA 会自动编译，DevTools 会自动重启应用

#### 方式二：手动触发编译

如果不想启用自动编译，可以手动触发：
- 修改代码后，按 `Ctrl + F9` (Windows/Linux) 或 `Cmd + F9` (macOS) 手动编译
- DevTools 检测到 class 文件变化后会自动重启

#### 方式三：Maven 命令启动（推荐用于调试）

```bash
# 进入 backend 目录
cd backend

# 使用 Maven 启动（支持热部署）
mvn spring-boot:run

# 或者使用 Maven Wrapper（如果有）
./mvnw spring-boot:run  # Linux/macOS
mvnw.cmd spring-boot:run  # Windows
```

修改代码后，在另一个终端执行：
```bash
mvn compile
```

### Eclipse 配置

Eclipse 默认支持自动编译，无需额外配置：
1. 确保 `Project` → `Build Automatically` 已勾选
2. 使用 `Run As` → `Spring Boot App` 启动应用
3. 修改代码保存后会自动触发热部署

### VS Code 配置

1. 安装插件：
   - `Spring Boot Extension Pack`
   - `Java Extension Pack`

2. 配置 `.vscode/settings.json`：
```json
{
    "java.autobuild.enabled": true,
    "java.compile.nullAnalysis.mode": "automatic"
}
```

3. 使用 Spring Boot Dashboard 启动应用

## 🚀 使用方法

### 启动应用

**方式一：IDE 直接运行**
```
运行主类：com.datalabeling.DataLabelingApplication
```

**方式二：Maven 命令**
```bash
cd backend
mvn spring-boot:run
```

**方式三：打包后运行（不支持热部署）**
```bash
mvn clean package
java -jar target/data-labeling-backend.jar
```

### 触发热部署

1. **修改 Java 代码**
   - 修改 Controller、Service、Entity 等任何 Java 类
   - 保存文件（IDEA 自动编译或手动 `Ctrl+F9`）
   - 等待 1-2 秒，控制台会显示重启日志

2. **修改配置文件**
   - 修改 `application.yml` 或 `application.properties`
   - 保存后自动触发重启

3. **修改静态资源**
   - 修改 `static/` 或 `templates/` 下的文件
   - 不会触发重启，但 LiveReload 会刷新浏览器

## 📊 热部署日志示例

成功触发热部署时，控制台会显示：

```
2026-01-03 10:30:15.123  INFO 12345 --- [  restartedMain] c.d.DataLabelingApplication              : Started DataLabelingApplication in 2.345 seconds (JVM running for 3.456)
2026-01-03 10:30:45.678  INFO 12345 --- [      Thread-10] o.s.b.d.a.OptionalLiveReloadServer       : LiveReload server is running on port 35729

... 修改代码并保存 ...

2026-01-03 10:31:10.123  INFO 12345 --- [      Thread-11] o.s.b.d.a.ConditionEvaluationDeltaLoggingListener : Condition evaluation unchanged
2026-01-03 10:31:10.456  INFO 12345 --- [  restartedMain] c.d.DataLabelingApplication              : Started DataLabelingApplication in 1.234 seconds (JVM running for 56.789)
```

## ⚙️ 高级配置

### 自定义触发文件

在 `src/main/resources` 下创建 `.reloadtrigger` 文件，修改此文件可手动触发重启：

```yaml
spring:
  devtools:
    restart:
      trigger-file: .reloadtrigger
```

### 排除特定文件

```yaml
spring:
  devtools:
    restart:
      exclude: static/**,public/**,templates/**,**/*Test.class
```

### 禁用热部署（生产环境）

生产环境打包时，DevTools 会自动被排除（因为 `scope=runtime` 且 `optional=true`）。

如需在开发环境临时禁用：
```yaml
spring:
  devtools:
    restart:
      enabled: false
```

## 🐛 常见问题

### 1. 修改代码后没有自动重启

**原因**：
- IDEA 未启用自动编译
- 使用了 Debug 模式（某些情况下会禁用热部署）
- 修改的是静态资源（不会触发重启）

**解决方案**：
- 检查 IDEA 自动编译配置
- 使用 Run 模式而非 Debug 模式
- 手动触发编译 `Ctrl+F9`

### 2. 重启速度慢

**原因**：
- 项目依赖过多
- JVM 参数配置不当

**解决方案**：
- 调整 `quiet-period` 参数（增加静默期）
- 优化 JVM 参数：
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx512m -Xms256m"
```

### 3. 类加载器冲突

**原因**：
- 某些第三方库不支持热部署

**解决方案**：
- 在 `application.yml` 中排除特定类：
```yaml
spring:
  devtools:
    restart:
      exclude: com/some/library/**
```

### 4. LiveReload 不工作

**原因**：
- 浏览器未安装 LiveReload 插件
- 端口 35729 被占用

**解决方案**：
- 安装浏览器插件：[LiveReload Chrome Extension](https://chrome.google.com/webstore/detail/livereload/jnihajbhpnppcggbcgedagnkighmdlei)
- 修改端口：
```yaml
spring:
  devtools:
    livereload:
      port: 35730
```

## 📝 性能对比

| 场景 | 无热部署 | 有热部署 | 节省时间 |
|------|---------|---------|---------|
| 修改单个类 | 30-60秒 | 2-5秒 | ~90% |
| 修改配置文件 | 30-60秒 | 2-5秒 | ~90% |
| 修改多个类 | 30-60秒 | 3-8秒 | ~85% |

## 🔒 安全提示

- **仅在开发环境使用**：DevTools 会自动在生产环境打包时被排除
- **不要提交敏感配置**：`.reloadtrigger` 等文件应加入 `.gitignore`
- **注意端口占用**：LiveReload 端口 35729 需要在防火墙中开放（仅本地开发）

## 📚 参考资料

- [Spring Boot DevTools 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.devtools)
- [IntelliJ IDEA 自动编译配置](https://www.jetbrains.com/help/idea/compiling-applications.html)
- [Maven Spring Boot Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/)

---

**配置完成！** 现在你可以享受快速的开发体验了 🎉
