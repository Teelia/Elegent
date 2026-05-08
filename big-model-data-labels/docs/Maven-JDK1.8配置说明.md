# Maven JDK 1.8 配置说明

> **更新日期**: 2025-12-30
> **项目**: data-labeling-backend

---

## 一、pom.xml 配置

### 1. Java 版本属性

```xml
<properties>
    <java.version>1.8</java.version>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
</properties>
```

### 2. Maven Compiler Plugin

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.10.1</version>
    <configuration>
        <source>1.8</source>
        <target>1.8</target>
        <encoding>UTF-8</encoding>
        <compilerVersion>1.8</compilerVersion>
        <fork>true</fork>
        <meminitial>1024m</meminitial>
        <maxmem>2048m</maxmem>
    </configuration>
</plugin>
```

### 3. Maven Enforcer Plugin（新增）

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.3.0</version>
    <executions>
        <execution>
            <id>enforce-java-version</id>
            <goals>
                <goal>enforce</goal>
            </goals>
            <configuration>
                <rules>
                    <requireJavaVersion>
                        <version>[1.8,1.9)</version>
                    </requireJavaVersion>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 二、验证当前配置

### 检查JDK版本

```bash
cd backend
mvn compile exec:java -Dexec.mainClass="com.datalabeling.util.JDKVersionCheck"
```

**预期输出**：
```
Java版本信息:
  java.version: 1.8.0_xxx
  java.vendor: Oracle Corporation
  java.home: F:\software\devtools\JDK1.8\1.8\jre

检查结果:
  ✅ 当前JDK版本正确: 1.8
```

---

## 三、IDE 配置

### IntelliJ IDEA

1. **项目结构设置**
   - File → Project Structure → Project
   - SDK: 选择 JDK 1.8
   - Language level: 8

2. **模块设置**
   - File → Project Structure → Modules
   - Language level: 8

3. **Maven设置**
   - File → Settings → Build, Execution, Deployment → Build Tools → Maven
   - Maven home path: 选择Maven目录
   - JDK for importer: 选择 JDK 1.8

### Eclipse

1. **项目属性**
   - 右键项目 → Properties → Java Build Path
   - Libraries → 添加 JRE System Library [jdk1.8.0_xxx]

2. **编译器设置**
   - Window → Preferences → Java → Compiler
   - Compiler compliance level: 1.8

### VS Code

1. **设置 java.home**
   - 创建 `.vscode/settings.json`
   - 添加配置：
   ```json
   {
     "java.configuration.runtimes": [
       {
         "name": "JavaSE-1.8",
         "default": true,
         "path": "F:\\software\\devtools\\JDK1.8\\1.8"
       }
     ]
   }
   ```

---

## 四、环境变量配置

### Windows

```cmd
REM 设置 JAVA_HOME
set JAVA_HOME=F:\software\devtools\JDK1.8\1.8

REM 添加到 PATH
set PATH=%JAVA_HOME%\bin;%PATH%

REM 验证
java -version
javac -version
```

### Linux/Mac

```bash
# 设置 JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk

# 添加到 PATH
export PATH=$JAVA_HOME/bin:$PATH

# 验证
java -version
javac -version
```

---

## 五、常见问题排查

### 问题1: 编译错误 "Unsupported class version"

**原因**: JDK版本不匹配
**解决**: 确认JAVA_HOME指向JDK 1.8

### 问题2: Maven编译使用了错误的JDK

**原因**: 系统PATH中存在多个JDK版本
**解决**:
```bash
# 查看当前JAVA_HOME
echo %JAVA_HOME%

# 临时设置
set JAVA_HOME=F:\software\devtools\JDK1.8\1.8

# 重新编译
mvn clean compile
```

### 问题3: IDE提示 "无效的目标发行版本"

**解决**: 将项目语言级别和SDK都设置为8

---

## 六、Maven 命令

### 清理并重新编译

```bash
mvn clean compile
```

### 打包（跳过测试）

```bash
mvn clean package -DskipTests
```

### 运行应用

```bash
mvn spring-boot:run
```

---

**配置状态**: ✅ 已完成
