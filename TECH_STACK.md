# 小型团队项目任务协作管理系统：技术栈

2026-11-03 说明：本文保留为 2026-10-31 的初始记录，不继续维护正文。后续技术栈以 `Team-Work-Memory/02-技术文档/技术栈与本机环境.md` 为准。数据库目标已改为沿用本机 MySQL 8.0.34，不再以 8.4 LTS 为当前目标。

记录日期：2026-10-31（Australia/Perth）

## 技术选型

采用前后端分离、后端模块化单体架构。前端通过 HTTP API 访问后端，后端负责业务权限、事务和数据库访问。

| 层次 | 采用技术 | 版本基线或管理方式 | 用途 |
|---|---|---|---|
| 后端语言 | Java | 25；本机已验证 | 业务逻辑 |
| 后端框架 | Spring Boot | 4.x；初始化时锁定兼容的稳定补丁版 | Web API、配置、事务 |
| 权限框架 | Spring Security | 由 Spring Boot 依赖管理确定 | 身份认证与接口访问控制 |
| 数据访问 | MyBatis-Plus | 3.5.18 为官方文档已提供的 Boot 4 集成版本；初始化时锁定 | 查询、分页、持久化 |
| 数据库 | MySQL | 目标 8.4 LTS；本机实际版本尚未确认 | 关系数据存储 |
| 存储引擎 | InnoDB | 随 MySQL 提供 | 事务、行级锁、外键 |
| 数据库驱动 | MySQL Connector/J | 优先由 Spring Boot 依赖管理确定 | JDBC 连接 |
| 数据库迁移 | Flyway | 优先由 Spring Boot 依赖管理确定；初始化时包含 MySQL 支持模块 | 数据库结构版本管理 |
| 后端构建 | Maven | 目标 3.9.x，初始化时使用 Maven Wrapper 固定版本 | 依赖与构建 |
| 前端框架 | Vue | 3.x；初始化时锁定具体版本 | 组件和页面 |
| 前端语言 | TypeScript | 初始化时按 Vue/Vite 兼容要求锁定 | 静态类型检查 |
| 前端构建 | Vite | 初始化时锁定兼容稳定版本 | 开发服务与打包 |
| UI 组件 | Element Plus | 2.x；初始化时锁定具体版本 | 表格、表单、弹窗 |
| 页面路由 | Vue Router | 4.x；初始化时锁定具体版本 | 页面导航 |
| 状态管理 | Pinia | 初始化时锁定 Vue 3 兼容版本 | 必要的共享状态 |
| 前端运行环境 | Node.js | 24.22.1；本机已验证 | 前端工具运行环境 |
| 前端包管理 | pnpm | 11.25.0；本机已验证 | 安装依赖、执行脚本 |
| 版本控制 | Git | 2.56.0.windows.2；本机已验证 | 代码版本管理 |

“选型确定”与“依赖已安装”分开记录：尚未初始化的框架、库不声称已在本机安装。版本范围是选型基线，不是可复现构建的最终版本清单。初始化时应在 pom.xml、Maven Wrapper 配置、package.json、pnpm-lock.yaml 中固定实际版本，并同步更新本文档。

## 本机环境实测

检测方式：仅执行下列版本命令，不读取环境变量值、数据库配置或凭据，不扫描安装目录。

| 工具 | 命令 | 实测结果 |
|---|---|---|
| Java | java -version | Java 25；Oracle Java(TM) SE Runtime Environment，build 25+37-LTS-3491 |
| Java 编译器 | javac -version | javac 25 |
| Maven | mvn -version | PATH 中未找到命令，实际安装状态及版本未知 |
| Node.js | node --version | v24.22.1 |
| npm | npm --version | PATH 中未找到命令，实际安装状态及版本未知；项目采用 pnpm |
| pnpm | pnpm --version | 11.25.0 |
| MySQL 客户端 | mysql --version | PATH 中未找到命令，实际安装状态及版本未知 |
| MySQL 服务端程序 | mysqld --version | PATH 中未找到命令，实际安装状态及版本未知 |
| Docker | docker --version | PATH 中未找到命令；作为后续可选部署工具 |
| Git | git --version | 2.56.0.windows.2 |

未在 PATH 找到命令不代表软件未安装。MySQL 客户端版本也不能代替实际连接的数据库服务端版本；本次未连接数据库，服务端版本、运行状态和实际表引擎均未验证。

## 实施边界与待办

- 本次仅确定选型和记录环境，不安装软件、不初始化代码、不修改系统环境。
- 初始化前确认 Maven 使用方式，以及 MySQL 的安装位置或部署方式；不预设需要重新安装。
- 前端框架和后端依赖的精确版本，在初始化阶段通过依赖解析及最小启动验证后回填。
- Spring Security 负责认证与接口保护；团队成员资格、项目访问范围等业务权限仍须在后端实现。具体登录方案后续设计。
- 初期不引入 Redis、消息队列或微服务；出现明确业务需求后另行评估。
- Docker Compose 为后续部署候选，不作为当前本机开发前提。

## 官方依据

- [Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)：本次查询页面列出的 4.1.2 支持 Java 17 至 27，覆盖本机 Java 25；最终以选定版本的要求为准。
- [MyBatis-Plus 快速开始](https://baomidou.com/en/getting-started/)：提供 mybatis-plus-spring-boot4-starter:3.5.18 集成示例，Boot 4 项目应选用对应 starter。
- [Vite 入门](https://vite.dev/guide/)：本次查询要求 Node.js 20.19+、22.12+；本机 Node.js 24.22.1 满足该版本门槛，仍需遵守所选模板要求。

官方页面会更新；本文记录的是本次查证结果，不将其视为永久兼容承诺。