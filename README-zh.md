# 🐾 PawSQL MCP Server 说明文档

## 项目概述

**PawSQL MCP Server** 是一款基于 **Spring AI** 构建的 **SQL 优化服务**，用于提供 SQL 性能分析与优化建议。
该服务以 **MCP（Model Control Protocol）服务器** 的形式运行，并通过标准 API 接口向外提供 SQL 优化能力。

---

## 主要功能

* 支持 **有工作区** 与 **无工作区** 的 SQL 优化模式
* 提供 **SQL 重写** 与 **索引优化** 建议
* 支持 **可视化执行计划分析**（当连接数据库时）
* 输出 **性能评估报告**，帮助提升 SQL 执行效率

---

## 支持的数据库类型

* MySQL
* PostgreSQL
* Oracle
* KingbaseES
* openGauss
* MogDB
* GaussDB
* DWS

---

## 安装指南

### 🟢 方式一：远程 SSE 模式（推荐）

#### **1. 部署服务**

运行以下命令拉取并启动 Docker 容器：

```bash
# 拉取并运行 PawSQL MCP Server 容器
docker run -d \
  --name pawsql-mcp-server \
  -p 8080:8080 \
  -e PAWSQL_API_BASE_URL=<api-url> \
  pawsql/pawsql-mcp-server-sse:latest
```

> 💡 **说明：**
>
> * 将 `<api-url>` 替换为你的 PawSQL API 服务地址，例如 `https://api.pawsql.com`。
> * 服务启动后，SSE 端点地址为：
    >
    >   ```
>   http://<server-ip>:8080/sse
>   ```

---

#### **2. 启用并配置 MCP 服务**

部署完成后，登录 **PawSQL 配置页面**，按以下步骤操作：

1. 打开 **功能开关 → 启用 MCP 服务**
2. 打开 **MCP 服务启用开关**
3. 在 **MCP Server URL** 字段中填写你部署的 SSE 服务地址，例如：

   ```
   http://<server-ip>:8080/sse
   ```
4. 点击 **保存配置**

---

#### **3. 获取 MCP 配置**

进入 **PawSQL Web 界面 → 用户设置** 页面，点击
**「获取 MCP 配置并复制」**，系统会生成完整配置片段，例如：

```json
{
  "mcpServers": {
    "PawSQLMcpServer": {
      "url": "http://xxx.xxx.xxx/sse",
      "headers": {
        "Authorization": "Bearer XXX"
      }
    }
  }
}
```

---

#### **4. 配置 Claude Desktop**

将上述配置内容添加到 Claude Desktop 的配置文件中。

* **macOS 路径**：

  ```
  ~/Library/Application Support/Claude/claude_desktop_config.json
  ```
* **Windows 路径**：

  ```
  %APPDATA%/Claude/claude_desktop_config.json
  ```

示例：

```json
{
  "mcpServers": {
    "PawSQLMcpServer": {
      "url": "http://xxx.xxx.xxx/sse",
      "headers": {
        "Authorization": "Bearer <your-token>"
      }
    }
  }
}
```

---

### ⚙️ 方式二：本地 STDIO 模式（传统方式）

#### **1. 配置 Claude Desktop**

1. 打开 Claude Desktop
2. 进入 **Settings（设置） → Developer（开发者）**
3. 点击 **Edit Config（编辑配置）**
4. 添加 MCP 配置
5. 保存并重启 Claude Desktop

#### **2. MCP 服务配置模板**

```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e", "PAWSQL_EDITION=<edition>",
        "-e", "PAWSQL_API_BASE_URL=<api-url>",
        "-e", "PAWSQL_API_EMAIL=<email>",
        "-e", "PAWSQL_API_PASSWORD=<password>",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

#### **3. 参数说明**

| 参数名          | 说明                                                  |
| ------------ | --------------------------------------------------- |
| `<edition>`  | 版本类型：`enterprise`（企业版）、`cloud`（云版）、`community`（社区版） |
| `<api-url>`  | API 服务地址                                            |
| `<email>`    | 登录邮箱                                                |
| `<password>` | 登录密码                                                |

---

#### **4. 各版本配置示例**

**企业版：**

```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "PAWSQL_EDITION=enterprise",
        "-e", "PAWSQL_API_BASE_URL=https://your-enterprise-api.com",
        "-e", "PAWSQL_API_EMAIL=admin@company.com",
        "-e", "PAWSQL_API_PASSWORD=your-password",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

**云版：**

```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "PAWSQL_EDITION=cloud",
        "-e", "PAWSQL_API_EMAIL=user@example.com",
        "-e", "PAWSQL_API_PASSWORD=your-password",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

**社区版：**

```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "PAWSQL_EDITION=community",
        "-e", "PAWSQL_API_BASE_URL=https://community-api.pawsql.com",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

---

## 在 Claude 中使用 PawSQL MCP Server

配置完成后，你可以在 Claude 中直接调用 PawSQL 的 SQL 优化能力。

### 示例 1：获取工作区信息

```
用户：有哪些可用的工作区？

助手：以下是当前可用工作区信息：

| 工作区名称 | 工作区 ID | 数据库类型 | 是否支持验证 | 状态 |
|------------|-----------|------------|--------------|------|
| WS_MySQL_202505241801 | 1926217077522944002 | mysql | 支持 | success |
```

---

### 示例 2：SQL 优化方式

#### ✅ 方法一：简单查询优化

只需提供数据库类型与 SQL 语句：

```sql
帮我优化这个 MySQL 查询：

select *
  from customer
  where c_custkey = (select max(o_custkey)
                        from orders
                        where subdate(o_orderdate, interval '1' DAY) < '2022-12-20')
```

---

#### ✅ 方法二：包含表结构的优化

提供表结构（DDL）与 SQL：

```sql
我要优化这个 MySQL 查询，表结构如下：

CREATE TABLE `customer` (
  `C_CUSTKEY` int NOT NULL,
  `C_NAME` varchar(25) NOT NULL,
  `C_ADDRESS` varchar(40) NOT NULL,
  `C_NATIONKEY` int NOT NULL,
  `C_PHONE` char(15) NOT NULL,
  `C_ACCTBAL` decimal(15,2) NOT NULL,
  `C_MKTSEGMENT` char(10) NOT NULL,
  `C_COMMENT` varchar(117) NOT NULL,
  PRIMARY KEY `PK_IDX1614428511` (`C_CUSTKEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE `orders` (
  `O_ORDERKEY` int NOT NULL,
  `O_CUSTKEY` int NOT NULL,
  `O_ORDERSTATUS` char(1) NOT NULL,
  `O_TOTALPRICE` decimal(15,2) NOT NULL,
  `O_ORDERDATE` date NOT NULL,
  `O_ORDERPRIORITY` char(15) NOT NULL,
  `O_CLERK` char(15) NOT NULL,
  `O_SHIPPRIORITY` int NOT NULL,
  `O_COMMENT` varchar(79) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

select *
  from customer
  where c_custkey = (select max(o_custkey)
                        from orders
                        where subdate(o_orderdate, interval '1' DAY) < '2022-12-20')
```

---

#### ✅ 方法三：基于工作区的优化

提供工作区名称或 ID，以获得基于真实数据库上下文的更精准优化：

```sql
请在工作区 WS_MySQL_202505241801 中优化以下查询：

select *
  from customer
  where c_custkey = (select max(o_custkey)
                        from orders
                        where subdate(o_orderdate, interval '1' DAY) < '2022-12-20')
```

---

### 🔍 关于工作区信息

你可以通过以下两种方式查看工作区：

1. **使用 PawSQL MCP 工具**：
   直接在 Claude 中让助手列出可用工作区
2. **通过 Web 界面**：
   登录 PawSQL Web 控制台查看和管理你的工作区

---

## 优化报告说明

优化完成后，系统会返回一份完整的优化报告，包含以下内容：

1. **分析报告链接**

    * 可点击访问详细分析结果页面

2. **分析环境信息**

    * 包含 SQL 分析时的上下文信息

3. **优化建议内容**

    * SQL 重写建议
    * 索引优化建议
    * 执行计划分析（仅验证型工作区可用）
    * 性能提升评估

