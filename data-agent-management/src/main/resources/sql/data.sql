-- 初始化数据文件
-- 只在表为空时插入示例数据

-- 业务知识示例数据
INSERT IGNORE INTO `business_knowledge` (`id`, `business_term`, `description`, `synonyms`, `is_recall`, `agent_id`, `created_time`, `updated_time`) VALUES
(1, 'Customer Satisfaction', 'Measures how satisfied customers are with the service or product.', 'customer happiness, client contentment', 0, 1, NOW(), NOW()),
(2, 'Net Promoter Score', 'A measure of the likelihood of customers recommending a company to others.', 'NPS, customer loyalty score', 0, 1, NOW(), NOW()),
(3, 'Customer Retention Rate', 'The percentage of customers who continue to use a service over a given period.', 'retention, customer loyalty', 0, 2, NOW(), NOW());

-- 智能体示例数据
INSERT IGNORE INTO `agent` (`id`, `name`, `description`, `avatar`, `status`, `api_key`, `api_key_enabled`, `prompt`, `category`, `admin_id`, `tags`, `create_time`, `update_time`) VALUES
(1, '中国人口GDP数据智能体', '专门处理中国人口和GDP相关数据查询分析的智能体', '/avatars/china-gdp-agent.png', 'draft', NULL, 0, '你是一个专业的数据分析助手，专门处理中国人口和GDP相关的数据查询。请根据用户的问题，生成准确的SQL查询语句。', '数据分析', 2100246635, '人口数据,GDP分析,经济统计', NOW(), NOW()),
(2, '销售数据分析智能体', '专注于销售数据分析和业务指标计算的智能体', '/avatars/sales-agent.png', 'draft', NULL, 0, '你是一个销售数据分析专家，能够帮助用户分析销售趋势、客户行为和业务指标。', '业务分析', 2100246635, '销售分析,业务指标,客户分析', NOW(), NOW()),
(3, '财务报表智能体', '专门处理财务数据和报表分析的智能体', '/avatars/finance-agent.png', 'draft', NULL, 0, '你是一个财务分析专家，专门处理财务数据查询和报表生成。', '财务分析', 2100246635, '财务数据,报表分析,会计', NOW(), NOW()),
(4, '库存管理智能体', '专注于库存数据管理和供应链分析的智能体', '/avatars/inventory-agent.png', 'draft', NULL, 0, '你是一个库存管理专家，能够帮助用户查询库存状态、分析供应链数据。', '供应链', 2100246635, '库存管理,供应链,物流', NOW(), NOW());

-- 智能体知识示例数据
INSERT IGNORE INTO `agent_knowledge` (`id`, `agent_id`, `title`, `content`, `type`, `is_recall`, `embedding_status`, `file_type`, `created_time`, `updated_time`) VALUES 
(1, 1, '中国人口统计数据说明', '中国人口统计数据包含了历年的人口总数、性别比例、年龄结构、城乡分布等详细信息。数据来源于国家统计局，具有权威性和准确性。查询时请注意数据的时间范围和统计口径。', 'DOCUMENT', 1, 'PENDING', 'text', NOW(), NOW()),
(2, 1, 'GDP数据使用指南', 'GDP（国内生产总值）数据反映了国家经济发展水平。包含名义GDP、实际GDP、GDP增长率等指标。数据按季度和年度进行统计，支持按地区、行业进行分类查询。', 'DOCUMENT', 1, 'PENDING', 'text', NOW(), NOW()),
(3, 1, '常见查询问题', '问：如何查询2023年的人口数据？\n答：可以使用"SELECT * FROM population WHERE year = 2023"进行查询。\n\n问：如何计算GDP增长率？\n答：GDP增长率 = (当年GDP - 上年GDP) / 上年GDP * 100%', 'QA', 1, 'PENDING', 'text', NOW(), NOW()),
(4, 2, '销售数据字段说明', '销售数据表包含以下关键字段：\n- sales_amount：销售金额\n- customer_id：客户ID\n- product_id：产品ID\n- sales_date：销售日期\n- region：销售区域\n- sales_rep：销售代表', 'DOCUMENT', 1, 'PENDING', 'text', NOW(), NOW()),
(5, 2, '客户分析指标体系', '客户分析包含多个维度：\n1. 客户价值分析：RFM模型（最近购买时间、购买频次、购买金额）\n2. 客户生命周期：新客户、活跃客户、流失客户\n3. 客户满意度：NPS评分、满意度调研\n4. 客户行为分析：购买偏好、渠道偏好', 'DOCUMENT', 1, 'PENDING', 'text', NOW(), NOW()),
(6, 3, '财务报表模板', '标准财务报表包含：\n1. 资产负债表：反映企业财务状况\n2. 利润表：反映企业经营成果\n3. 现金流量表：反映企业现金流动情况\n4. 所有者权益变动表：反映股东权益变化', 'DOCUMENT', 1, 'PENDING', 'pdf', NOW(), NOW()),
(7, 4, '库存管理最佳实践', '库存管理的核心要点：\n1. 安全库存设置：确保不断货\n2. ABC分类管理：重点管理A类物料\n3. 先进先出原则：避免库存积压\n4. 定期盘点：确保数据准确性\n5. 供应商管理：建立稳定供应关系', 'DOCUMENT', 1, 'PENDING', 'text', NOW(), NOW());

-- 数据源示例数据
-- 示例数据源可以运行docker-compose-datasource.yml建立，或者手动修改为自己的数据源
INSERT IGNORE INTO `datasource` (`id`, `name`, `type`, `host`, `port`, `database_name`, `username`, `password`, `connection_url`, `status`, `test_status`, `description`, `creator_id`, `create_time`, `update_time`) VALUES 
(1, '生产环境MySQL数据库', 'mysql', 'mysql-data', 3306, 'product_db', 'root', 'root', 'jdbc:mysql://mysql-data:3306/product_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true', 'inactive', 'unknown', '生产环境主数据库，包含核心业务数据', 2100246635, NOW(), NOW()),
(2, '数据仓库PostgreSQL', 'postgresql', 'postgres-data', 5432, 'data_warehouse', 'postgres', 'postgres', 'jdbc:postgresql://postgres-data:5432/data_warehouse', 'inactive', 'unknown', '数据仓库，用于数据分析和报表生成', 2100246635, NOW(), NOW());

-- 智能体数据源关联示例数据
INSERT IGNORE INTO `agent_datasource` (`id`, `agent_id`, `datasource_id`, `is_active`, `create_time`, `update_time`) VALUES 
(1, 1, 2, 0, NOW(), NOW()),  -- 中国人口GDP数据智能体使用数据仓库
(2, 2, 1, 0, NOW(), NOW()),  -- 销售数据分析智能体使用生产环境数据库
(3, 3, 1, 0, NOW(), NOW()),  -- 财务报表智能体使用生产环境数据库
(4, 4, 1, 0, NOW(), NOW());  -- 库存管理智能体使用生产环境数据库
INSERT IGNORE INTO `semantic_table`
(`id`, `agent_id`, `datasource_id`, `table_name`, `business_name`, `synonyms`, `business_description`, `table_comment`, `is_visible`, `status`, `created_time`, `updated_time`)
VALUES
(1001, 2, 1, 'users', '用户', '会员,客户,消费者,user,customer', '记录商城用户基础信息、会员等级、注册渠道和生命周期价值。', '电商用户表', 1, 1, NOW(), NOW()),
(1002, 2, 1, 'products', '商品', '货品,SKU,产品,product,item', '记录商品主数据，包括品牌、类目、售价、成本和库存。', '商品表', 1, 1, NOW(), NOW()),
(1003, 2, 1, 'orders', '订单', '销售订单,客户订单,交易单,order,sales order', '记录订单主信息，包括下单时间、订单状态、支付状态、地区和净收入。', '订单主表', 1, 1, NOW(), NOW()),
(1004, 2, 1, 'order_items', '订单明细', '子订单,行项目,订单行,item line,order line', '记录每笔订单中的商品明细、数量、成交单价和实付金额。', '订单明细表', 1, 1, NOW(), NOW()),
(1005, 2, 1, 'payments', '支付流水', '支付单,付款记录,payment,transaction', '记录订单支付方式、支付金额和支付状态。', '支付流水表', 1, 1, NOW(), NOW()),
(1006, 2, 1, 'refunds', '退款', '售后退款,退单,refund,return refund', '记录退款申请原因、退款金额和退款完成状态。', '退款表', 1, 1, NOW(), NOW()),
(1007, 2, 1, 'inventory_snapshots', '库存快照', '库存日报,库存日快照,stock snapshot,inventory daily', '记录商品在仓库维度的每日可售库存、锁定库存和在途数量。', '库存日快照表', 1, 1, NOW(), NOW()),
(1008, 2, 1, 'customer_service_tickets', '客服工单', '售后工单,服务单,ticket,service ticket', '记录订单相关客服问题、优先级、状态和满意度评分。', '客服工单表', 1, 1, NOW(), NOW());

INSERT IGNORE INTO `semantic_column`
(`id`, `agent_id`, `datasource_id`, `table_name`, `column_name`, `business_name`, `synonyms`, `business_description`, `column_comment`, `data_type`, `is_visible`, `status`, `created_time`, `updated_time`)
VALUES
(2001, 2, 1, 'users', 'user_no', '用户编号', '会员编号,客户编号,user no', '用户唯一编号，适合做用户标识查询。', '用户编号', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2002, 2, 1, 'users', 'member_level', '会员等级', '会员层级,等级,level,tier', '标识会员分层，如 bronze、silver、gold、platinum。', '会员等级', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2003, 2, 1, 'users', 'register_channel', '注册渠道', '拉新渠道,来源渠道,channel,source', '用户注册来源渠道，如 app、web、mini_program。', '注册渠道', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2004, 2, 1, 'users', 'lifetime_value', '生命周期价值', 'LTV,用户价值,customer lifetime value', '用户累计价值估计，可用于高价值用户分析。', '生命周期价值', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2005, 2, 1, 'products', 'product_name', '商品名称', '品名,货品名称,product name,item name', '商品展示名称。', '商品名称', 'VARCHAR(128)', 1, 1, NOW(), NOW()),
(2006, 2, 1, 'products', 'sale_price', '销售价', '成交价,售价,selling price', '商品当前销售价格。', '销售价', 'DECIMAL(10,2)', 1, 1, NOW(), NOW()),
(2007, 2, 1, 'products', 'cost_price', '成本价', '成本,采购成本,cost', '商品成本价格，可用于毛利分析。', '成本价', 'DECIMAL(10,2)', 1, 1, NOW(), NOW()),
(2008, 2, 1, 'products', 'stock_quantity', '当前库存', '现货库存,库存量,on hand stock', '商品当前总库存数量。', '当前库存', 'INT', 1, 1, NOW(), NOW()),
(2009, 2, 1, 'products', 'rating', '商品评分', '口碑评分,好评度,rating,score', '商品平均评分。', '商品评分', 'DECIMAL(3,2)', 1, 1, NOW(), NOW()),
(2010, 2, 1, 'orders', 'order_time', '下单时间', '成交时间,购买时间,order date', '用户下单发生时间。', '下单时间', 'DATETIME', 1, 1, NOW(), NOW()),
(2011, 2, 1, 'orders', 'order_status', '订单状态', '交易状态,订单进度,status', '订单整体状态，如 completed、cancelled、refunded。', '订单状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2012, 2, 1, 'orders', 'payment_status', '支付状态', '付款状态,payment state', '订单支付结果状态。', '支付状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2013, 2, 1, 'orders', 'order_channel', '下单渠道', '成交渠道,购买渠道,order source', '订单来源渠道，如 app、web、mini_program。', '下单渠道', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2014, 2, 1, 'orders', 'gross_amount', '订单原价金额', '商品原额,订单总额,gross sales', '订单商品原始金额，未扣减优惠。', '商品原始金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2015, 2, 1, 'orders', 'discount_amount', '优惠金额', '折扣金额,立减金额,discount', '订单层面的优惠金额。', '优惠金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2016, 2, 1, 'orders', 'refund_amount', '退款金额', '售后退款额,refund value', '订单累计退款金额。', '退款金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2017, 2, 1, 'orders', 'net_amount', '订单净收入', '实收金额,净销售额,net revenue', '订单最终净收入，适合做 GMV/净收入分析。', '订单净收入', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2018, 2, 1, 'order_items', 'quantity', '购买件数', '销量,购买数量,sales qty,units sold', '该订单明细购买的商品件数。', '购买件数', 'INT', 1, 1, NOW(), NOW()),
(2019, 2, 1, 'order_items', 'unit_price', '成交单价', '下单单价,unit selling price', '订单明细的成交单价。', '成交单价', 'DECIMAL(10,2)', 1, 1, NOW(), NOW()),
(2020, 2, 1, 'order_items', 'final_amount', '明细实付金额', '行实收金额,实付小计,line revenue', '订单明细最终实付金额。', '明细实付金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2021, 2, 1, 'payments', 'payment_method', '支付方式', '付款方式,pay channel,payment channel', '订单支付方式，如 alipay、wechat_pay、credit_card。', '支付方式', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2022, 2, 1, 'payments', 'payment_amount', '支付金额', '付款金额,实付金额,payment amount', '支付流水中的付款金额。', '支付金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2023, 2, 1, 'payments', 'payment_status', '支付结果', '付款结果,payment result', '支付是否成功。', '支付状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2024, 2, 1, 'refunds', 'refund_reason', '退款原因', '售后原因,退货原因,refund reason', '退款申请原因。', '退款原因', 'VARCHAR(128)', 1, 1, NOW(), NOW()),
(2025, 2, 1, 'refunds', 'refund_amount', '退款金额', '退款额,refund amount', '单次退款金额。', '退款金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2026, 2, 1, 'refunds', 'refund_status', '退款状态', '售后状态,refund state', '退款处理状态。', '退款状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2027, 2, 1, 'inventory_snapshots', 'snapshot_date', '快照日期', '库存日期,统计日期,snapshot date', '库存快照统计日期。', '快照日期', 'DATE', 1, 1, NOW(), NOW()),
(2028, 2, 1, 'inventory_snapshots', 'available_stock', '可售库存', '可用库存,available qty', '当前可用于销售的库存数量。', '可售库存', 'INT', 1, 1, NOW(), NOW()),
(2029, 2, 1, 'inventory_snapshots', 'locked_stock', '锁定库存', '占用库存,reserved stock', '已被订单占用但尚未释放的库存。', '锁定库存', 'INT', 1, 1, NOW(), NOW()),
(2030, 2, 1, 'inventory_snapshots', 'inbound_qty', '在途数量', '入库在途,采购在途,in transit qty', '已采购或调拨但尚未入库的数量。', '在途数量', 'INT', 1, 1, NOW(), NOW()),
(2031, 2, 1, 'customer_service_tickets', 'ticket_type', '工单类型', '问题类型,售后类型,ticket category', '客服工单业务类型，如 refund、shipping、invoice。', '工单类型', 'VARCHAR(64)', 1, 1, NOW(), NOW()),
(2032, 2, 1, 'customer_service_tickets', 'priority', '优先级', '紧急程度,priority level', '客服工单优先级。', '优先级', 'VARCHAR(16)', 1, 1, NOW(), NOW()),
(2033, 2, 1, 'customer_service_tickets', 'status', '工单状态', '处理状态,ticket status', '客服工单处理状态。', '工单状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2034, 2, 1, 'customer_service_tickets', 'satisfaction_score', '满意度评分', '服务评分,满意度,cs score', '客服处理完成后的满意度评分。', '满意度评分', 'INT', 1, 1, NOW(), NOW());

INSERT IGNORE INTO `semantic_relation`
(`id`, `agent_id`, `datasource_id`, `source_table_name`, `source_column_names`, `target_table_name`, `target_column_names`, `relation_type`, `description`, `status`, `created_time`, `updated_time`)
VALUES
(3001, 2, 1, 'orders', 'user_id', 'users', 'id', 'many_to_one', '订单归属到下单用户，可做用户购买行为分析。', 1, NOW(), NOW()),
(3002, 2, 1, 'order_items', 'order_id', 'orders', 'id', 'many_to_one', '订单明细归属到订单主表，可统计订单内商品和销量。', 1, NOW(), NOW()),
(3003, 2, 1, 'order_items', 'product_id', 'products', 'id', 'many_to_one', '订单明细关联商品主数据，可分析品类、品牌和单品销售。', 1, NOW(), NOW()),
(3004, 2, 1, 'payments', 'order_id', 'orders', 'id', 'one_to_one', '支付流水关联订单，可分析支付方式与支付成功率。', 1, NOW(), NOW()),
(3005, 2, 1, 'refunds', 'order_id', 'orders', 'id', 'many_to_one', '退款记录关联订单，可分析退款订单分布。', 1, NOW(), NOW()),
(3006, 2, 1, 'refunds', 'order_item_id', 'order_items', 'id', 'many_to_one', '退款记录可回溯到具体退款商品明细。', 1, NOW(), NOW()),
(3007, 2, 1, 'inventory_snapshots', 'product_id', 'products', 'id', 'many_to_one', '库存快照关联商品，可分析单品库存走势。', 1, NOW(), NOW()),
(3008, 2, 1, 'customer_service_tickets', 'order_id', 'orders', 'id', 'many_to_one', '客服工单关联订单，可分析售后问题来源订单。', 1, NOW(), NOW());

INSERT IGNORE INTO `agent_datasource` (`id`, `agent_id`, `datasource_id`, `is_active`, `create_time`, `update_time`) VALUES
(8, 2, 8, 1, NOW(), NOW());

INSERT IGNORE INTO `semantic_table`
(`id`, `agent_id`, `datasource_id`, `table_name`, `business_name`, `synonyms`, `business_description`, `table_comment`, `is_visible`, `status`, `created_time`, `updated_time`)
VALUES
(1801, 2, 8, 'users', '用户', '会员,客户,消费者,user,customer', '记录商城用户基础信息、会员等级、注册渠道和生命周期价值。', '电商用户表', 1, 1, NOW(), NOW()),
(1802, 2, 8, 'products', '商品', '货品,SKU,产品,product,item', '记录商品主数据，包括品牌、类目、售价、成本和库存。', '商品表', 1, 1, NOW(), NOW()),
(1803, 2, 8, 'orders', '订单', '销售订单,客户订单,交易单,order,sales order', '记录订单主信息，包括下单时间、订单状态、支付状态、地区和净收入。', '订单主表', 1, 1, NOW(), NOW()),
(1804, 2, 8, 'order_items', '订单明细', '子订单,行项目,订单行,item line,order line', '记录每笔订单中的商品明细、数量、成交单价和实付金额。', '订单明细表', 1, 1, NOW(), NOW()),
(1805, 2, 8, 'payments', '支付流水', '支付单,付款记录,payment,transaction', '记录订单支付方式、支付金额和支付状态。', '支付流水表', 1, 1, NOW(), NOW()),
(1806, 2, 8, 'refunds', '退款', '售后退款,退单,refund,return refund', '记录退款申请原因、退款金额和退款完成状态。', '退款表', 1, 1, NOW(), NOW()),
(1807, 2, 8, 'inventory_snapshots', '库存快照', '库存日报,库存日快照,stock snapshot,inventory daily', '记录商品在仓库维度的每日可售库存、锁定库存和在途数量。', '库存日快照表', 1, 1, NOW(), NOW()),
(1808, 2, 8, 'customer_service_tickets', '客服工单', '售后工单,服务单,ticket,service ticket', '记录订单相关客服问题、优先级、状态和满意度评分。', '客服工单表', 1, 1, NOW(), NOW());

INSERT IGNORE INTO `semantic_column`
(`id`, `agent_id`, `datasource_id`, `table_name`, `column_name`, `business_name`, `synonyms`, `business_description`, `column_comment`, `data_type`, `is_visible`, `status`, `created_time`, `updated_time`)
VALUES
(2801, 2, 8, 'users', 'user_no', '用户编号', '会员编号,客户编号,user no', '用户唯一编号，适合做用户标识查询。', '用户编号', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2802, 2, 8, 'users', 'member_level', '会员等级', '会员层级,等级,level,tier', '标识会员分层，如 bronze、silver、gold、platinum。', '会员等级', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2803, 2, 8, 'users', 'register_channel', '注册渠道', '拉新渠道,来源渠道,channel,source', '用户注册来源渠道，如 app、web、mini_program。', '注册渠道', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2804, 2, 8, 'users', 'lifetime_value', '生命周期价值', 'LTV,用户价值,customer lifetime value', '用户累计价值估计，可用于高价值用户分析。', '生命周期价值', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2805, 2, 8, 'products', 'product_name', '商品名称', '品名,货品名称,product name,item name', '商品展示名称。', '商品名称', 'VARCHAR(128)', 1, 1, NOW(), NOW()),
(2806, 2, 8, 'products', 'sale_price', '销售价', '成交价,售价,selling price', '商品当前销售价格。', '销售价', 'DECIMAL(10,2)', 1, 1, NOW(), NOW()),
(2807, 2, 8, 'products', 'cost_price', '成本价', '成本,采购成本,cost', '商品成本价格，可用于毛利分析。', '成本价', 'DECIMAL(10,2)', 1, 1, NOW(), NOW()),
(2808, 2, 8, 'products', 'stock_quantity', '当前库存', '现货库存,库存量,on hand stock', '商品当前总库存数量。', '当前库存', 'INT', 1, 1, NOW(), NOW()),
(2809, 2, 8, 'products', 'rating', '商品评分', '口碑评分,好评度,rating,score', '商品平均评分。', '商品评分', 'DECIMAL(3,2)', 1, 1, NOW(), NOW()),
(2810, 2, 8, 'orders', 'order_time', '下单时间', '成交时间,购买时间,order date', '用户下单发生时间。', '下单时间', 'DATETIME', 1, 1, NOW(), NOW()),
(2811, 2, 8, 'orders', 'order_status', '订单状态', '交易状态,订单进度,status', '订单整体状态，如 completed、cancelled、refunded。', '订单状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2812, 2, 8, 'orders', 'payment_status', '支付状态', '付款状态,payment state', '订单支付结果状态。', '支付状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2813, 2, 8, 'orders', 'order_channel', '下单渠道', '成交渠道,购买渠道,order source', '订单来源渠道，如 app、web、mini_program。', '下单渠道', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2814, 2, 8, 'orders', 'gross_amount', '订单原价金额', '商品原额,订单总额,gross sales', '订单商品原始金额，未扣减优惠。', '商品原始金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2815, 2, 8, 'orders', 'discount_amount', '优惠金额', '折扣金额,立减金额,discount', '订单层面的优惠金额。', '优惠金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2816, 2, 8, 'orders', 'refund_amount', '退款金额', '售后退款额,refund value', '订单累计退款金额。', '退款金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2817, 2, 8, 'orders', 'net_amount', '订单净收入', '实收金额,净销售额,net revenue', '订单最终净收入，适合做 GMV/净收入分析。', '订单净收入', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2818, 2, 8, 'order_items', 'quantity', '购买件数', '销量,购买数量,sales qty,units sold', '该订单明细购买的商品件数。', '购买件数', 'INT', 1, 1, NOW(), NOW()),
(2819, 2, 8, 'order_items', 'unit_price', '成交单价', '下单单价,unit selling price', '订单明细的成交单价。', '成交单价', 'DECIMAL(10,2)', 1, 1, NOW(), NOW()),
(2820, 2, 8, 'order_items', 'final_amount', '明细实付金额', '行实收金额,实付小计,line revenue', '订单明细最终实付金额。', '明细实付金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2821, 2, 8, 'payments', 'payment_method', '支付方式', '付款方式,pay channel,payment channel', '订单支付方式，如 alipay、wechat_pay、credit_card。', '支付方式', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2822, 2, 8, 'payments', 'payment_amount', '支付金额', '付款金额,实付金额,payment amount', '支付流水中的付款金额。', '支付金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2823, 2, 8, 'payments', 'payment_status', '支付结果', '付款结果,payment result', '支付是否成功。', '支付状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2824, 2, 8, 'refunds', 'refund_reason', '退款原因', '售后原因,退货原因,refund reason', '退款申请原因。', '退款原因', 'VARCHAR(128)', 1, 1, NOW(), NOW()),
(2825, 2, 8, 'refunds', 'refund_amount', '退款金额', '退款额,refund amount', '单次退款金额。', '退款金额', 'DECIMAL(12,2)', 1, 1, NOW(), NOW()),
(2826, 2, 8, 'refunds', 'refund_status', '退款状态', '售后状态,refund state', '退款处理状态。', '退款状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2827, 2, 8, 'inventory_snapshots', 'snapshot_date', '快照日期', '库存日期,统计日期,snapshot date', '库存快照统计日期。', '快照日期', 'DATE', 1, 1, NOW(), NOW()),
(2828, 2, 8, 'inventory_snapshots', 'available_stock', '可售库存', '可用库存,available qty', '当前可用于销售的库存数量。', '可售库存', 'INT', 1, 1, NOW(), NOW()),
(2829, 2, 8, 'inventory_snapshots', 'locked_stock', '锁定库存', '占用库存,reserved stock', '已被订单占用但尚未释放的库存。', '锁定库存', 'INT', 1, 1, NOW(), NOW()),
(2830, 2, 8, 'inventory_snapshots', 'inbound_qty', '在途数量', '入库在途,采购在途,in transit qty', '已采购或调拨但尚未入库的数量。', '在途数量', 'INT', 1, 1, NOW(), NOW()),
(2831, 2, 8, 'customer_service_tickets', 'ticket_type', '工单类型', '问题类型,售后类型,ticket category', '客服工单业务类型，如 refund、shipping、invoice。', '工单类型', 'VARCHAR(64)', 1, 1, NOW(), NOW()),
(2832, 2, 8, 'customer_service_tickets', 'priority', '优先级', '紧急程度,priority level', '客服工单优先级。', '优先级', 'VARCHAR(16)', 1, 1, NOW(), NOW()),
(2833, 2, 8, 'customer_service_tickets', 'status', '工单状态', '处理状态,ticket status', '客服工单处理状态。', '工单状态', 'VARCHAR(32)', 1, 1, NOW(), NOW()),
(2834, 2, 8, 'customer_service_tickets', 'satisfaction_score', '满意度评分', '服务评分,满意度,cs score', '客服处理完成后的满意度评分。', '满意度评分', 'INT', 1, 1, NOW(), NOW());

INSERT IGNORE INTO `semantic_relation`
(`id`, `agent_id`, `datasource_id`, `source_table_name`, `source_column_names`, `target_table_name`, `target_column_names`, `relation_type`, `description`, `status`, `created_time`, `updated_time`)
VALUES
(3801, 2, 8, 'orders', 'user_id', 'users', 'id', 'many_to_one', '订单归属到下单用户，可做用户购买行为分析。', 1, NOW(), NOW()),
(3802, 2, 8, 'order_items', 'order_id', 'orders', 'id', 'many_to_one', '订单明细归属到订单主表，可统计订单内商品和销量。', 1, NOW(), NOW()),
(3803, 2, 8, 'order_items', 'product_id', 'products', 'id', 'many_to_one', '订单明细关联商品主数据，可分析品类、品牌和单品销售。', 1, NOW(), NOW()),
(3804, 2, 8, 'payments', 'order_id', 'orders', 'id', 'one_to_one', '支付流水关联订单，可分析支付方式与支付成功率。', 1, NOW(), NOW()),
(3805, 2, 8, 'refunds', 'order_id', 'orders', 'id', 'many_to_one', '退款记录关联订单，可分析退款订单分布。', 1, NOW(), NOW()),
(3806, 2, 8, 'refunds', 'order_item_id', 'order_items', 'id', 'many_to_one', '退款记录可回溯到具体退款商品明细。', 1, NOW(), NOW()),
(3807, 2, 8, 'inventory_snapshots', 'product_id', 'products', 'id', 'many_to_one', '库存快照关联商品，可分析单品库存走势。', 1, NOW(), NOW()),
(3808, 2, 8, 'customer_service_tickets', 'order_id', 'orders', 'id', 'many_to_one', '客服工单关联订单，可分析售后问题来源订单。', 1, NOW(), NOW());
