# CLAUDE.md

本文件是本项目的长期上下文，后续所有会话必须遵守。

## 项目简介

AI 智能记账系统，三个模块分离部署：bill-server（Spring Boot，:8080）、bill-web（Vue3+Vite，构建后由 Nginx 托管）、bill-ai（Python FastAPI，:8000）。数据库 MySQL bill_db。

## 目录约定

- 后端代码在 `bill-server/src/main/java/com/example/bill/` 下，按 entity / mapper / service(+impl) / controller / common / config 分包。
- 前端代码在 `bill-web/src`。
- 后端调用 AI 一律走 `http://127.0.0.1:8000`。

## 版本锁死（违反即返工）

- Java 17。
- spring-boot-starter-parent 3.5.16，禁止 4.x。
- mybatis-plus-spring-boot3-starter 3.5.18.1。
- 禁止 JPA/Hibernate、Spring Security、Redis、消息队列。
- 禁止 Java 21 语法（record pattern、虚拟线程等）。

## 代码规约

- 所有接口返回 `Result<T>{code,msg,data}`，code 0 成功、1 失败。
- Mapper 只 extends BaseMapper，不写 XML、不写 SQL。
- Service 继承 IService/ServiceImpl，不重复实现 CRUD。
- 查询用 LambdaQueryWrapper 动态拼条件。
- 金额用 BigDecimal，日期用 LocalDate，禁止 double 和 java.util.Date。
- 中文注释；单方法不超过 40 行；单文件不超过 300 行。

## 常用命令

- 后端：`cd bill-server && mvn -q compile`；打包运行 `mvn clean package && java -jar target/bill-server-1.0.0.jar`。
- 前端：`cd bill-web && npm install && npm run build`（必须构建成功）。
- AI：`cd bill-ai && source venv/bin/activate && uvicorn main:app --host 127.0.0.1 --port 8000`。
- 数据库初始化：`DB_PASSWORD=<你的密码> bash sql/init.sh`（需管理员 MySQL 连接）。

## 凭据与环境

- 数据库：bill_user@localhost，密码从环境变量 DB_PASSWORD 读取；仓库中禁止出现明文密码。
- DeepSeek API Key 必须从环境变量 DEEPSEEK_API_KEY 读取，任何文件中出现明文 Key 视为错误。
- .env 不进版本库。

## 安全边界

- 不擅自修改 /etc、不擅自 apt 安装软件包、不擅自执行 systemctl 操作。
- 需要 sudo 或改系统配置时，先说明理由并等待确认。
- 不执行 rm -rf，不删除已有文件，除非被明确要求。

## 完成标准

- 每个模块完成后自行执行编译/构建命令验证，失败先修复再继续。
- 汇报时只说明"改了什么文件 + 验证结果"，不贴大段代码。
