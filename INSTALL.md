# 茶遇 - 安装指南

## 环境要求

- **Java**: JDK 17 或更高版本
- **Maven**: 3.6+ (可选，项目包含Maven Wrapper)

## 安装步骤

### 步骤 1: 安装 Java

#### 方法 A: 使用 Chocolatey (推荐)

1. 以管理员身份打开 PowerShell
2. 安装 Chocolatey:
```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
```

3. 安装 Java:
```powershell
choco install temurin17 -y
```

#### 方法 B: 手动安装

1. 访问 https://adoptium.net/temurin/releases/
2. 选择:
   - Version: 17 (LTS)
   - Operating System: Windows
   - Architecture: x64
3. 下载 .msi 安装包
4. 双击安装
5. 配置环境变量:
   - 新建 `JAVA_HOME`: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`
   - 在 `Path` 中添加: `%JAVA_HOME%\bin`

### 步骤 2: 安装 Maven (可选)

如果已有Maven Wrapper，可跳过此步骤。

#### 方法 A: 使用 Chocolatey
```powershell
choco install maven -y
```

#### 方法 B: 手动安装
1. 访问 https://maven.apache.org/download.cgi
2. 下载 Binary zip archive
3. 解压到 `C:\Program Files\Apache\maven`
4. 配置环境变量:
   - 新建 `M2_HOME`: `C:\Program Files\Apache\maven`
   - 在 `Path` 中添加: `%M2_HOME%\bin`

### 步骤 3: 验证安装

打开新的命令行窗口，运行:
```bash
java -version
mvn -version
```

### 步骤 4: 启动项目

双击运行 `install.bat` 或手动执行:

```bash
# 编译项目
mvn clean package -DskipTests

# 启动服务
mvn spring-boot:run
```

### 步骤 5: 访问应用

打开浏览器访问: http://localhost:8080

## 常见问题

### Q: 提示 'java' 不是内部或外部命令
A: Java 未正确安装或未配置环境变量。请按照步骤1重新安装。

### Q: 端口 8080 已被占用
A: 修改 `src/main/resources/application.yml` 中的端口号:
```yaml
server:
  port: 8081
```

### Q: 图片无法显示
A: 确保 `img/` 文件夹中有相应的图片文件。项目已包含100张示例图片。

## 快速启动

如果您已经安装好Java和Maven:

```bash
cd 项目文件夹
mvn spring-boot:run
```

然后访问 http://localhost:8080
