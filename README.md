# PawSQL MCP Server

## Project Overview

PawSQL MCP Server is a SQL optimization service developed based on Spring AI, providing SQL performance analysis and optimization suggestions. It runs as an MCP (Model Control Protocol) server and provides SQL optimization capabilities through API interfaces.

## Key Features

* Supports both workspace and workspace-free optimization modes
* Provides SQL rewriting and index optimization suggestions
* Visual execution plan analysis (for database-connected workspaces)
* Performance evaluation reports

## Supported Databases

* MySQL
* PostgreSQL
* Oracle
* KingbaseES
* openGauss
* MogDB
* GaussDB
* DWS

## Installation Guide

1. Configure Claude Desktop:
   * Open Claude Desktop
   * Select "Settings", click "Developer" tab
   * Click "Edit Config"
   * Add MCP server configuration
   * Save the file
   * Restart Claude Desktop

2. MCP Server Configuration Template:

```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run",
        "-i",
        "--rm",
        "-e", "PAWSQL_VERSION=<version>",
        "-e", "PAWSQL_API_BASE_URL=<api-url>",
        "-e", "PAWSQL_API_EMAIL=<email>",
        "-e", "PAWSQL_API_PASSWORD=<password>",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

3. Configuration Parameters:

- `<version>`: Choose one of the following versions
  * `enterprise` - Enterprise Edition
  * `cloud` - Cloud Edition
  * `community` - Community Edition
- `<api-url>`: API service address
- `<email>`: Account email
- `<password>`: Account password

4. Version Configuration Examples:

Enterprise Edition:
```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "PAWSQL_VERSION=enterprise",
        "-e", "PAWSQL_API_BASE_URL=https://your-enterprise-api.com",
        "-e", "PAWSQL_API_EMAIL=admin@company.com",
        "-e", "PAWSQL_API_PASSWORD=your-password",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

Cloud Edition:
```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "PAWSQL_VERSION=cloud",
        "-e", "PAWSQL_API_EMAIL=user@example.com",
        "-e", "PAWSQL_API_PASSWORD=your-password",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

Community Edition:
```json
{
  "mcpServers": {
    "pawsql": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "PAWSQL_VERSION=community",
        "-e", "PAWSQL_API_BASE_URL=https://community-api.pawsql.com",
        "pawsql/pawsql-mcp-server:latest"
      ]
    }
  }
}
```

## Using in Claude

After configuration, you can use PawSQL MCP Server in different ways. Here are some examples:

### 1. Getting Workspace Information

Before using workspace-based optimization, you need to get the workspace information:

```
User: What workspaces are available?

Assistant: Here are the available workspaces:

| Workspace Name | Workspace ID | Database Type | Can Validate Optimization | Status |
|---------------|--------------|--------------|------------------------|--------|
| WS_MySQL_202505241801 | 1926217077522944002 | mysql | Yes | success |
```

### 2. SQL Optimization Methods

#### Method 1: Simple Query Optimization
Provide database type and SQL query:

```sql
Help me optimize this mysql query:

select *
  from customer
  where c_custkey = (select max(o_custkey)
                        from orders
                        where subdate(o_orderdate, interval '1' DAY) < '2022-12-20')
```

#### Method 2: Optimization with Table Structure
Provide database type, table structure (DDL), and SQL query:

```sql
I want to optimize this mysql query, here's the table structure:

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

#### Method 3: Workspace-based Optimization
Provide workspace name/ID and SQL query for more accurate optimization with actual database context:

```sql
Optimize this query in workspace WS_MySQL_202505241801:

select *
  from customer
  where c_custkey = (select max(o_custkey)
                        from orders
                        where subdate(o_orderdate, interval '1' DAY) < '2022-12-20')
```

### Note on Workspace Information

You can obtain workspace information through two methods:
1. **Using PawSQL MCP Tools**: Ask the AI assistant to list available workspaces using built-in commands
2. **Web Interface**: Visit your configured PawSQL service web interface to view and manage workspaces

## Optimization Report Description

The system will return an optimization report containing the following:

1. Analysis Report Link
   * View detailed analysis results

2. Analysis Environment Details
   * Contains SQL analysis context information

3. Optimization Suggestions
   * SQL rewriting suggestions
   * Index optimization suggestions
   * Execution plan analysis (for validation-enabled workspaces only)
   * Performance improvement estimates
