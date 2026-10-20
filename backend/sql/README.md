# MySQL 初始化

`schema.sql` 定义 **11 张数据库表**，只包含 DDL，没有真实账号或业务数据。

```bash
mysql -u root -p < backend/sql/schema.sql
```

后端连接信息请在 `backend/src/main/resources/application-local.yml`（本地、不可上传）
中设置，参见同目录 `application-local.example.yml`。

知识库原始法律条文、Qdrant 向量、咨询数据与历史评测记录均未提供。
请自行导入合法数据进行运行和测试。
