-- 初始化数据文件
-- 幂等初始化示例数据；已有记录仅在内容变化时更新

-- 智能体示例数据（必须先于带 agent_id 外键的知识和语义模型写入）
INSERT INTO `agent` (`id`, `name`, `description`, `avatar`, `status`, `api_key`, `api_key_enabled`, `prompt`, `category`, `admin_id`, `tags`, `create_time`, `update_time`) VALUES
(1, '电商订单分析智能体', '基于用户、商品、订单和分类数据进行查询与经营分析', NULL, 'draft', NULL, 0, '你是一个电商运营数据分析助手。请仅基于已连接数据库中的用户、商品、订单、订单明细和分类数据回答问题，生成与真实字段一致的SQL。', '电商分析', 2100246635, '订单分析,商品分析,用户分析', NOW(), NOW()),
(2, '销售数据分析智能体', '专注于销售数据分析和业务指标计算的智能体', NULL, 'draft', NULL, 0, '你是一个销售数据分析专家，能够帮助用户分析销售趋势、客户行为和业务指标。', '业务分析', 2100246635, '销售分析,业务指标,客户分析', NOW(), NOW()),
(3, '财务报表智能体', '专门处理财务数据和报表分析的智能体', NULL, 'draft', NULL, 0, '你是一个财务分析专家，专门处理财务数据查询和报表生成。', '财务分析', 2100246635, '财务数据,报表分析,会计', NOW(), NOW()),
(4, '库存管理智能体', '专注于库存数据管理和供应链分析的智能体', NULL, 'draft', NULL, 0, '你是一个库存管理专家，能够帮助用户查询库存状态、分析供应链数据。', '供应链', 2100246635, '库存管理,供应链,物流', NOW(), NOW())
ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), avatar=VALUES(avatar), prompt=VALUES(prompt), category=VALUES(category), tags=VALUES(tags), update_time=NOW();

-- 业务知识示例数据
-- 参考 KNOWLEDGE_USAGE.md：业务名称用标准术语，描述要"讲人话"说明计算公式和过滤条件，同义词枚举所有可能叫法
INSERT INTO `business_knowledge` (`id`, `business_term`, `description`, `synonyms`, `is_recall`, `agent_id`, `created_time`, `updated_time`, `embedding_status`) VALUES
-- 电商场景示例
(1, 'GMV', 'GMV（商品交易总额）= orders 表中 status = ''completed'' 的订单 total_amount 总和。pending 和 cancelled 状态不计入。', '流水, 交易额, 全站销售额, 总成交额, 商品交易总额', 1, 1, NOW(), NOW(), 'PENDING'),
(2, '成交用户数', '成交用户数是在选定时间段内，orders 表中 status = ''completed'' 的去重 user_id 数量。当前示例库没有登录日志，不能据此计算 DAU、WAU 或 MAU。', '购买用户数, 成交人数, 下单用户数', 1, 1, NOW(), NOW(), 'PENDING'),
(3, '复购率', '复购率 = 完成订单数大于等于2的去重用户数 / 至少有1笔完成订单的去重用户数 × 100%。订单口径使用 orders.status = ''completed''。', '重复购买率, 回头率, 再次购买比例', 1, 1, NOW(), NOW(), 'PENDING'),
-- 销售场景示例
(4, '客单价', '客单价 = orders 表中已完成订单的 total_amount 总和 / 已完成订单数，过滤条件为 status = ''completed''。', '平均订单金额, 每单平均金额, 单均金额, AOV', 1, 2, NOW(), NOW(), 'PENDING'),
(5, '转化率', '转化率需要访问用户数等流量数据作为分母。当前示例库只有订单和商品数据，不能可靠计算访问到下单的转化率；应先接入访问日志数据。', '转化比例, 购买转化率, 下单转化率, CVR', 1, 2, NOW(), NOW(), 'PENDING')
ON DUPLICATE KEY UPDATE
    embedding_status=IF(
        NOT (business_term <=> VALUES(business_term))
        OR NOT (description <=> VALUES(description))
        OR NOT (synonyms <=> VALUES(synonyms)),
        'PENDING',
        embedding_status
    ),
    business_term=VALUES(business_term),
    description=VALUES(description),
    synonyms=VALUES(synonyms),
    is_recall=VALUES(is_recall),
    agent_id=VALUES(agent_id),
    updated_time=NOW();

-- 语义模型示例数据
-- 参考 KNOWLEDGE_USAGE.md：业务名称用口语化标准名，同义词解决问法多样性，业务描述解释枚举值和特殊逻辑
INSERT INTO `semantic_model` (`id`, `agent_id`, `datasource_id`, `table_name`, `column_name`, `business_name`, `synonyms`, `business_description`, `column_comment`, `data_type`, `created_time`, `updated_time`, `status`) VALUES
-- 电商订单表语义模型
(1, 1, 1, 'orders', 'total_amount', '订单金额', '订单价格, 成交价, 销售额, 支付金额, amount, 实付金额', '订单总金额，单位为元。统计成交金额时应结合 status 过滤已完成订单。', '订单总金额', 'decimal(10,2)', NOW(), NOW(), 1),
(2, 1, 1, 'orders', 'status', '订单状态', '状态, 订单状态码, order_status', '订单状态枚举：pending=待处理，completed=已完成，cancelled=已取消。统计成交额时只包含 completed。', '订单状态', 'varchar(20)', NOW(), NOW(), 1),
(3, 1, 1, 'orders', 'user_id', '用户ID', '会员ID, 客户编号, 买家ID, uid', '下单用户的唯一标识，关联 users 表的 id 字段。', '用户ID', 'int', NOW(), NOW(), 1),
(4, 1, 1, 'orders', 'order_date', '下单时间', '创建时间, 订单时间, 购买时间, create_time', '订单创建时间，格式为 YYYY-MM-DD HH:mm:ss。按天统计时使用 DATE(order_date) 分组。', '下单时间', 'datetime', NOW(), NOW(), 1),
-- 用户表语义模型
(5, 1, 1, 'users', 'username', '用户名', '客户名称, 用户名称, 会员名称, user_name', '用户的显示名称，可通过 users.id 与 orders.user_id 关联订单。', '用户名', 'varchar(50)', NOW(), NOW(), 1),
(6, 1, 1, 'users', 'created_at', '注册时间', '注册日期, 开户时间, 加入时间, register_time', '用户记录创建时间，格式为 YYYY-MM-DD HH:mm:ss。', '用户注册时间', 'datetime', NOW(), NOW(), 1)
ON DUPLICATE KEY UPDATE datasource_id=VALUES(datasource_id), table_name=VALUES(table_name), column_name=VALUES(column_name), business_name=VALUES(business_name), synonyms=VALUES(synonyms), business_description=VALUES(business_description), column_comment=VALUES(column_comment), data_type=VALUES(data_type), updated_time=NOW(), status=VALUES(status);

-- 智能体知识示例数据
-- 参考 KNOWLEDGE_USAGE.md：文档用于SOP/Schema说明，Q&A用于纠正Agent错误行为（Few-Shot Learning）
INSERT INTO `agent_knowledge` (`id`, `agent_id`, `title`, `content`, `type`, `is_recall`, `embedding_status`, `file_type`, `question`, `created_time`, `updated_time`) VALUES
-- 文档类型：数据库Schema说明书
(1, 1, '电商示例数据库表结构说明', '核心表包括：users(id, username, email, created_at)、products(id, name, price, stock, created_at)、orders(id, user_id, order_date, total_amount, status)、order_items(id, order_id, product_id, quantity, unit_price)、categories(id, name)、product_categories(product_id, category_id)。关联关系：orders.user_id = users.id；order_items.order_id = orders.id；order_items.product_id = products.id；product_categories 连接商品与分类。', 'QA', 1, 'PENDING', 'text', '电商示例数据库有哪些核心表和关联关系？', NOW(), NOW()),
-- 文档类型：业务流程SOP
(2, 1, '订单经营分析标准流程', '订单分析应先按 status 过滤业务口径，再按 order_date 确定时间范围。成交订单使用 status = ''completed''；成交金额使用 SUM(total_amount)；用户维度通过 orders.user_id = users.id；商品维度通过 orders -> order_items -> products 关联。报告应明确时间范围、状态口径和使用的表。', 'QA', 1, 'PENDING', 'text', '如何进行订单经营分析？', NOW(), NOW()),
-- Q&A类型：纠正Agent常见错误（Few-Shot Learning）
(3, 1, '统计已完成订单金额', 'SELECT SUM(total_amount) AS completed_amount FROM orders WHERE status = ''completed''。不要使用不存在的 price2 或 order_status 字段。', 'QA', 1, 'PENDING', 'text', '如何统计已完成订单的总金额？', NOW(), NOW()),
(4, 1, '统计用户订单数量', 'SELECT u.id, u.username, COUNT(o.id) AS order_count FROM users u LEFT JOIN orders o ON o.user_id = u.id GROUP BY u.id, u.username ORDER BY order_count DESC。', 'QA', 1, 'PENDING', 'text', '如何统计每个用户的订单数量？', NOW(), NOW()),
-- 销售智能体知识
(5, 2, '销售数据分析指标体系', '销售数据分析包含以下核心指标：成交金额、客单价、订单数和复购率。表关联以真实Schema为准：orders.user_id = users.id；商品维度通过 order_items 连接 orders 和 products。', 'QA', 1, 'PENDING', 'text', '销售数据包含哪些核心指标和表关系？', NOW(), NOW()),
(6, 2, '销售额查询正确写法', 'SELECT DATE(order_date) AS sale_date, SUM(total_amount) AS total_amount, COUNT(*) AS order_count\nFROM orders\nWHERE status = ''completed''\n  AND order_date >= ''2024-01-01''\n  AND order_date < ''2024-02-01''\nGROUP BY DATE(order_date)\nORDER BY sale_date。使用真实字段 order_date、total_amount 和 status。', 'QA', 1, 'PENDING', 'text', '统计2024年1月的每日销售额', NOW(), NOW()),
(7, 2, '高价值用户定义', '高价值用户可按累计已完成订单金额或订单数定义。使用 orders.total_amount、orders.status 和 orders.user_id，并根据业务阈值筛选。', 'FAQ', 1, 'PENDING', 'text', '什么是高价值用户？如何筛选？', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    embedding_status=IF(
        NOT (title <=> VALUES(title))
        OR NOT (content <=> VALUES(content))
        OR NOT (type <=> VALUES(type))
        OR NOT (question <=> VALUES(question)),
        'PENDING',
        embedding_status
    ),
    title=VALUES(title),
    content=VALUES(content),
    type=VALUES(type),
    is_recall=VALUES(is_recall),
    file_type=VALUES(file_type),
    question=VALUES(question),
    updated_time=NOW();

-- 数据源示例数据
-- 示例数据源可以运行docker-compose-datasource.yml建立，或者手动修改为自己的数据源
INSERT IGNORE INTO `datasource` (`id`, `name`, `type`, `host`, `port`, `database_name`, `username`, `password`, `connection_url`, `status`, `test_status`, `description`, `creator_id`, `create_time`, `update_time`) VALUES 
(1, '生产环境MySQL数据库', 'mysql', 'mysql-data', 3306, 'product_db', 'root', 'root', 'jdbc:mysql://mysql-data:3306/product_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true', 'inactive', 'unknown', '生产环境主数据库，包含核心业务数据', 2100246635, NOW(), NOW()),
(2, '数据仓库PostgreSQL', 'postgresql', 'postgres-data', 5432, 'data_warehouse', 'postgres', 'postgres', 'jdbc:postgresql://postgres-data:5432/data_warehouse', 'inactive', 'unknown', '数据仓库，用于数据分析和报表生成', 2100246635, NOW(), NOW());

-- 智能体数据源关联示例数据
INSERT IGNORE INTO `agent_datasource` (`id`, `agent_id`, `datasource_id`, `is_active`, `create_time`, `update_time`) VALUES 
(1, 1, 1, 1, NOW(), NOW()),  -- 电商订单分析智能体使用生产环境数据库
(2, 2, 1, 0, NOW(), NOW()),  -- 销售数据分析智能体使用生产环境数据库
(3, 3, 1, 0, NOW(), NOW()),  -- 财务报表智能体使用生产环境数据库
(4, 4, 1, 0, NOW(), NOW());  -- 库存管理智能体使用生产环境数据库
