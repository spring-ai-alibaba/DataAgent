-- 初始化数据文件
-- 只在表为空时插入示例数据

-- 智能体示例数据（必须先插入，因为其他表依赖它）
INSERT INTO agent (id, name, description, avatar, status, api_key, api_key_enabled, prompt, category, admin_id, tags, create_time, update_time) VALUES
(1, '电商订单分析智能体', '基于用户、商品、订单和分类数据进行查询与经营分析', NULL, 'draft', NULL, 0, '你是一个电商运营数据分析助手。请仅基于已连接数据库中的用户、商品、订单、订单明细和分类数据回答问题，生成与真实字段一致的SQL。', '电商分析', 2100246635, '订单分析,商品分析,用户分析', NOW(), NOW()),
(2, '销售数据分析智能体', '专注于销售数据分析和业务指标计算的智能体', NULL, 'draft', NULL, 0, '你是一个销售数据分析专家，能够帮助用户分析销售趋势、客户行为和业务指标。', '业务分析', 2100246635, '销售分析,业务指标,客户分析', NOW(), NOW()),
(3, '财务报表智能体', '专门处理财务数据和报表分析的智能体', NULL, 'draft', NULL, 0, '你是一个财务分析专家，专门处理财务数据查询和报表生成。', '财务分析', 2100246635, '财务数据,报表分析,会计', NOW(), NOW()),
(4, '库存管理智能体', '专注于库存数据管理和供应链分析的智能体', NULL, 'draft', NULL, 0, '你是一个库存管理专家，能够帮助用户查询库存状态、分析供应链数据。', '供应链', 2100246635, '库存管理,供应链,物流', NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), avatar=VALUES(avatar), prompt=VALUES(prompt), category=VALUES(category), tags=VALUES(tags);

-- 数据源示例数据（必须先插入，因为其他表依赖它）
-- 示例数据源可以运行docker-compose-datasource.yml建立，或者手动修改为自己的数据源
INSERT INTO datasource (id, name, type, host, port, database_name, username, password, connection_url, status, test_status, description, creator_id, create_time, update_time) VALUES 
(1, '生产环境MySQL数据库', 'mysql', 'mysql-data', 3306, 'product_db', 'root', 'root', 'jdbc:mysql://mysql-data:3306/product_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true', 'inactive', 'unknown', '生产环境主数据库，包含核心业务数据', 2100246635, NOW(), NOW()),
(2, '数据仓库PostgreSQL', 'postgresql', 'postgres-data', 5432, 'data_warehouse', 'postgres', 'postgres', 'jdbc:postgresql://postgres-data:5432/data_warehouse', 'inactive', 'unknown', '数据仓库，用于数据分析和报表生成', 2100246635, NOW(), NOW()),
(3, 'product_db', 'h2', 'nl2sql_database', 0, 'product_db', 'root', 'root', 'jdbc:h2:mem:nl2sql_database;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE', 'inactive', 'unknown', 'h2测试数据库，包含核心业务数据', 2100246635, NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 业务知识示例数据（依赖 agent）
INSERT INTO business_knowledge (id, business_term, description, synonyms, is_recall, agent_id, created_time, updated_time) VALUES
(1, '成交金额', '成交金额为 status = ''completed'' 的订单 total_amount 之和。', '销售额, 成交额, 已完成订单金额', 1, 1, NOW(), NOW()),
(2, '订单用户', '订单通过 orders.user_id = users.id 关联用户。', '客户, 买家, 下单用户', 1, 1, NOW(), NOW()),
(3, 'Customer Retention Rate', 'The percentage of customers who continue to use a service over a given period.', 'retention, customer loyalty', 0, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE business_term=VALUES(business_term);

-- 语义模型示例数据（依赖 agent 和 datasource）
INSERT INTO semantic_model (id, agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, created_time, updated_time, status) VALUES
(1, 1, 3, 'orders', 'total_amount', '订单金额', '销售额, 成交金额, 支付金额', '订单总金额，统计成交额时应过滤 status = completed。', '订单总金额', 'decimal(10,2)', NOW(), NOW(), 1),
(2, 1, 3, 'orders', 'status', '订单状态', '状态, order_status', '订单状态为 pending、completed 或 cancelled。', '订单状态', 'varchar(20)', NOW(), NOW(), 1),
(3, 2, 1, 'customer_metrics', 'retention_pct', 'customerRetentionRate', 'retention rate, loyalty rate', 'Percentage of retained customers', '客户保留率', 'decimal', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE business_name=VALUES(business_name);

-- 智能体知识示例数据（依赖 agent）
INSERT INTO agent_knowledge (id, agent_id, title, content, type, is_recall, embedding_status, file_type, question, created_time, updated_time) VALUES
(1, 1, '电商示例数据库表结构说明', '核心表包括 users、products、orders、order_items、categories 和 product_categories。orders.user_id 关联 users.id，order_items 关联订单和商品。', 'QA', 1, 'PENDING', 'text', '电商示例数据库有哪些核心表和关联关系？', NOW(), NOW()),
(2, 1, '订单经营分析标准流程', '订单分析先按 status 过滤，再按 order_date 确定时间范围。成交金额使用 SUM(total_amount)，成交订单状态为 completed。', 'QA', 1, 'PENDING', 'text', '如何进行订单经营分析？', NOW(), NOW()),
(3, 1, '统计已完成订单金额', 'SELECT SUM(total_amount) FROM orders WHERE status = ''completed''。', 'QA', 1, 'PENDING', 'text', '如何统计已完成订单金额？', NOW(), NOW()),
(4, 2, '销售数据字段说明', '销售分析以真实 orders 字段为准：order_date、total_amount、status、user_id。商品维度通过 order_items 连接 products。', 'QA', 1, 'PENDING', 'text', '销售分析使用哪些真实字段？', NOW(), NOW()),
(5, 2, '客户分析指标体系', '客户价值可按已完成订单金额、订单数和最近下单时间分析，使用 orders.user_id 关联 users.id。', 'FAQ', 1, 'PENDING', 'text', '如何基于订单分析客户价值？', NOW(), NOW()),
(6, 3, '财务报表能力边界', '当前示例数据没有会计科目、凭证和现金流表，不能生成可靠财务报表；应先绑定包含财务数据的数据源。', 'FAQ', 1, 'PENDING', 'text', '当前示例库能否生成财务报表？', NOW(), NOW()),
(7, 4, '库存分析能力边界', '当前示例库可使用 products.stock 查看商品库存，但没有入库、出库、仓库和供应商流水，无法完成完整供应链分析。', 'FAQ', 1, 'PENDING', 'text', '当前示例库支持哪些库存分析？', NOW(), NOW())
ON DUPLICATE KEY UPDATE title=VALUES(title), content=VALUES(content), type=VALUES(type), file_type=VALUES(file_type), question=VALUES(question);

-- 智能体数据源关联示例数据（依赖 agent 和 datasource）
INSERT INTO agent_datasource (id, agent_id, datasource_id, is_active, create_time, update_time) VALUES 
(1, 1, 3, 1, NOW(), NOW()),  -- 电商订单分析智能体使用H2示例数据库
(2, 2, 1, 0, NOW(), NOW()),  -- 销售数据分析智能体使用生产环境数据库
(3, 3, 1, 0, NOW(), NOW()),  -- 财务报表智能体使用生产环境数据库
(4, 4, 1, 0, NOW(), NOW())  -- 库存管理智能体使用生产环境数据库
ON DUPLICATE KEY UPDATE agent_id=VALUES(agent_id);
