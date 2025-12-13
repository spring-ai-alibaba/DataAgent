-- 初始化数据文件
-- 只在表为空时插入示例数据

-- 智能体示例数据
INSERT IGNORE INTO `agent` (`id`, `name`, `description`, `avatar`, `status`, `prompt`, `category`, `admin_id`, `tags`, `create_time`, `update_time`) VALUES
(1, '中国人口GDP数据智能体', '专门处理中国人口和GDP相关数据查询分析的智能体', '/avatars/china-gdp-agent.png', 'draft', '你是一个专业的数据分析助手，专门处理中国人口和GDP相关的数据查询。请根据用户的问题，生成准确的SQL查询语句。', '数据分析', 2100246635, '人口数据,GDP分析,经济统计', NOW(), NOW()),
(2, '销售数据分析智能体', '专注于销售数据分析和业务指标计算的智能体', '/avatars/sales-agent.png', 'draft', '你是一个销售数据分析专家，能够帮助用户分析销售趋势、客户行为和业务指标。', '业务分析', 2100246635, '销售分析,业务指标,客户分析', NOW(), NOW()),
(3, '财务报表智能体', '专门处理财务数据和报表分析的智能体', '/avatars/finance-agent.png', 'draft', '你是一个财务分析专家，专门处理财务数据查询和报表生成。', '财务分析', 2100246635, '财务数据,报表分析,会计', NOW(), NOW()),
(4, '库存管理智能体', '专注于库存数据管理和供应链分析的智能体', '/avatars/inventory-agent.png', 'draft', '你是一个库存管理专家，能够帮助用户查询库存状态、分析供应链数据。', '供应链', 2100246635, '库存管理,供应链,物流', NOW(), NOW());

-- 业务知识示例数据
INSERT IGNORE INTO `business_knowledge` (`id`, `business_term`, `description`, `synonyms`, `is_recall`, `data_set_id`, `agent_id`, `created_time`, `updated_time`) VALUES 
(1, 'Customer Satisfaction', 'Measures how satisfied customers are with the service or product.', 'customer happiness, client contentment', 0, 'dataset_001', 1, NOW(), NOW()),
(2, 'Net Promoter Score', 'A measure of the likelihood of customers recommending a company to others.', 'NPS, customer loyalty score', 0, 'dataset_002', 1, NOW(), NOW()),
(3, 'Customer Retention Rate', 'The percentage of customers who continue to use a service over a given period.', 'retention, customer loyalty', 0, 'dataset_003', 2, NOW(), NOW());

-- 语义模型示例数据
INSERT IGNORE INTO `semantic_model` (`id`, `agent_id`, `field_name`, `synonyms`, `origin_name`, `description`, `origin_description`, `type`, `created_time`, `updated_time`, `is_recall`, `status`) VALUES 
(1, 1, 'customerSatisfactionScore', 'satisfaction score, customer rating', 'csat_score', 'Customer satisfaction rating from 1-10', 'Customer satisfaction score', 'integer', NOW(), NOW(), 0, 0),
(2, 1, 'netPromoterScore', 'NPS, promoter score', 'nps_value', 'Net Promoter Score from -100 to 100', 'NPS calculation result', 'integer', NOW(), NOW(), 0, 0),
(3, 2, 'customerRetentionRate', 'retention rate, loyalty rate', 'retention_pct', 'Percentage of retained customers', 'Customer retention percentage', 'decimal', NOW(), NOW(), 0, 0);

-- 智能体知识示例数据（使用新表结构）
INSERT IGNORE INTO `agent_knowledge` (`id`, `agent_id`, `title`, `type`, `question`, `content`, `is_recall`, `embedding_status`, `error_msg`, `source_filename`, `file_path`, `file_type`, `created_time`, `updated_time`) VALUES
(1, 1, '中国人口统计数据说明', 'DOCUMENT', NULL, '中国人口统计数据包含了历年的人口总数、性别比例、年龄结构、城乡分布等详细信息。数据来源于国家统计局，具有权威性和准确性。查询时请注意数据的时间范围和统计口径。', 1, 'COMPLETED', NULL, 'population_guide.pdf', '/uploads/agent-knowledge/population_guide.pdf', 'application/pdf', NOW(), NOW()),
(2, 1, 'GDP数据使用指南', 'DOCUMENT', NULL, 'GDP（国内生产总值）数据反映了国家经济发展水平。包含名义GDP、实际GDP、GDP增长率等指标。数据按季度和年度进行统计，支持按地区、行业进行分类查询。', 1, 'PROCESSING', NULL, 'gdp_guide.pdf', '/uploads/agent-knowledge/gdp_guide.pdf', 'application/pdf', NOW(), NOW()),
(3, 1, '如何查询人口数据', 'QA', '如何查询2023年的人口数据？', '可以使用 SELECT * FROM population WHERE year = 2023 进行查询。注意检查数据表名称和字段名是否正确。', 1, 'COMPLETED', NULL, NULL, NULL, NULL, NOW(), NOW()),
(4, 1, '向量化失败测试数据', 'DOCUMENT', NULL, '这是一个用于测试向量化失败和重试功能的测试数据。', 1, 'FAILED', 'Vector store connection timeout after 30s', 'test_failed.txt', '/uploads/agent-knowledge/test_failed.txt', 'text/plain', NOW(), NOW()),
(5, 2, '销售数据字段说明', 'DOCUMENT', NULL, '销售数据表包含以下关键字段：sales_amount（销售金额）、customer_id（客户ID）、product_id（产品ID）、sales_date（销售日期）、region（销售区域）、sales_rep（销售代表）', 1, 'COMPLETED', NULL, 'sales_fields.md', '/uploads/agent-knowledge/sales_fields.md', 'text/markdown', NOW(), NOW()),
(6, 2, '客户分析FAQ', 'FAQ', '如何进行客户RFM分析？', 'RFM分析包括三个维度：Recency（最近购买时间）、Frequency（购买频次）、Monetary（购买金额）。通过这三个指标可以对客户进行分类和价值评估。', 1, 'COMPLETED', NULL, NULL, NULL, NULL, NOW(), NOW()),
(7, 1, '另一个失败测试', 'QA', '测试问题', '测试答案', 1, 'FAILED', 'Embedding model API rate limit exceeded', NULL, NULL, NULL, NOW(), NOW()),
(8, 3, '财务报表模板', 'DOCUMENT', NULL, '标准财务报表包含资产负债表、利润表、现金流量表和所有者权益变动表。', 1, 'COMPLETED', NULL, 'finance_template.xlsx', '/uploads/agent-knowledge/finance_template.xlsx', 'application/vnd.ms-excel', NOW(), NOW()),
(9, 3, '成本核算方法', 'DOCUMENT', NULL, '成本核算包括品种法、分批法、分步法，需根据企业生产特点选择。', 1, 'PENDING', NULL, 'cost_accounting.pdf', '/uploads/agent-knowledge/cost_accounting.pdf', 'application/pdf', NOW(), NOW()),
(10, 3, '税务筹划技巧', 'FAQ', '企业如何进行合法税务筹划？', '通过合理利用税收优惠政策、优化业务流程、选择合适的组织形式等方式降低税负。', 1, 'FAILED', 'Vector store connection refused', NULL, NULL, NULL, NOW(), NOW()),
(11, 4, '库存管理最佳实践', 'DOCUMENT', NULL, '库存管理核心包括安全库存设置、ABC分类管理、先进先出原则和定期盘点。', 1, 'COMPLETED', NULL, 'inventory_best_practices.md', '/uploads/agent-knowledge/inventory_best_practices.md', 'text/markdown', NOW(), NOW()),
(12, 4, '供应链风险管理', 'DOCUMENT', NULL, '供应链风险包括供应中断、需求波动、价格波动，需建立风险预警机制。', 1, 'PROCESSING', NULL, 'supply_chain_risk.pdf', '/uploads/agent-knowledge/supply_chain_risk.pdf', 'application/pdf', NOW(), NOW()),
(13, 1, '人口普查方法', 'DOCUMENT', NULL, '人口普查每10年进行一次，采用全面调查方法，了解人口数量、结构、分布。', 1, 'COMPLETED', NULL, 'census_method.pdf', '/uploads/agent-knowledge/census_method.pdf', 'application/pdf', NOW(), NOW()),
(14, 1, '区域经济指标', 'QA', '如何评价区域经济发展水平？', '通过GDP总量、人均GDP、产业结构、投资效率、创新能力等多维度指标综合评价。', 1, 'COMPLETED', NULL, NULL, NULL, NULL, NOW(), NOW()),
(15, 1, '数据质量控制', 'DOCUMENT', NULL, '数据质量控制贯穿数据采集、录入、审核、汇总全过程，确保数据真实可靠。', 1, 'FAILED', 'File parsing error: unsupported format', 'quality_control.doc', '/uploads/agent-knowledge/quality_control.doc', 'application/msword', NOW(), NOW()),
(16, 2, '销售预测模型', 'DOCUMENT', NULL, '销售预测常用时间序列分析、回归分析、机器学习等方法。', 1, 'COMPLETED', NULL, 'sales_forecast.pdf', '/uploads/agent-knowledge/sales_forecast.pdf', 'application/pdf', NOW(), NOW()),
(17, 2, '客户细分策略', 'DOCUMENT', NULL, '客户细分可按地理、人口统计、心理、行为等维度进行，制定差异化营销策略。', 1, 'COMPLETED', NULL, 'customer_segmentation.pptx', '/uploads/agent-knowledge/customer_segmentation.pptx', 'application/vnd.ms-powerpoint', NOW(), NOW()),
(18, 2, '客户流失预警', 'FAQ', '如何预防客户流失？', '建立流失预警模型，分析客户行为数据，提前识别高风险客户并采取挽留措施。', 1, 'FAILED', 'Insufficient memory for embedding generation', NULL, NULL, NULL, NOW(), NOW()),
(19, 3, '财务比率分析', 'DOCUMENT', NULL, '财务比率包括偿债能力、营运能力、盈利能力、发展能力四大类指标。', 1, 'COMPLETED', NULL, 'financial_ratios.xlsx', '/uploads/agent-knowledge/financial_ratios.xlsx', 'application/vnd.ms-excel', NOW(), NOW()),
(20, 3, '预算管理体系', 'DOCUMENT', NULL, '全面预算管理包括预算编制、执行、控制、分析四个环节。', 1, 'PENDING', NULL, 'budget_management.pdf', '/uploads/agent-knowledge/budget_management.pdf', 'application/pdf', NOW(), NOW()),
(21, 3, '现金流管理', 'QA', '如何优化企业现金流？', '加强应收账款管理、合理安排付款周期、保持适当现金储备、优化融资结构。', 1, 'COMPLETED', NULL, NULL, NULL, NULL, NOW(), NOW()),
(22, 4, '仓储管理优化', 'DOCUMENT', NULL, '仓储优化包括仓库布局、货位管理、拣货路径优化、提升库存周转率。', 1, 'COMPLETED', NULL, 'warehouse_optimization.pdf', '/uploads/agent-knowledge/warehouse_optimization.pdf', 'application/pdf', NOW(), NOW()),
(23, 4, '采购策略技巧', 'DOCUMENT', NULL, '采购策略包括供应商选择、批量决策、时机选择、价格谈判等关键环节。', 1, 'FAILED', 'Network timeout during vector store operation', 'procurement_strategy.pdf', '/uploads/agent-knowledge/procurement_strategy.pdf', 'application/pdf', NOW(), NOW()),
(24, 4, '物流配送优化', 'FAQ', '如何优化配送路径？', '运用运筹学方法，考虑时间窗、车辆容量、道路状况等约束，制定最优配送方案。', 1, 'COMPLETED', NULL, NULL, NULL, NULL, NOW(), NOW()),
(25, 1, '统计数据可视化', 'DOCUMENT', NULL, '数据可视化选择合适图表类型：柱状图、折线图、饼图、散点图等。', 1, 'COMPLETED', NULL, 'data_visualization.pdf', '/uploads/agent-knowledge/data_visualization.pdf', 'application/pdf', NOW(), NOW()),
(26, 1, '经济指标解读', 'QA', 'CPI和PPI有什么区别？', 'CPI是居民消费价格指数，反映消费品价格变化；PPI是生产者价格指数，反映生产资料价格变化。', 1, 'FAILED', 'Embedding service temporarily unavailable', NULL, NULL, NULL, NOW(), NOW()),
(27, 2, '营销自动化工具', 'DOCUMENT', NULL, '营销自动化可提高效率，包括邮件营销、社交媒体管理、客户关系管理等功能。', 1, 'COMPLETED', NULL, 'marketing_automation.pdf', '/uploads/agent-knowledge/marketing_automation.pdf', 'application/pdf', NOW(), NOW()),
(28, 2, '销售团队管理', 'FAQ', '如何激励销售团队？', '建立合理薪酬体系、提供培训机会、设定清晰目标、营造竞争氛围、及时认可成就。', 1, 'PENDING', NULL, NULL, NULL, NULL, NOW(), NOW()),
(29, 3, '内部控制制度', 'DOCUMENT', NULL, '内部控制包括控制环境、风险评估、控制活动、信息沟通、监督检查五要素。', 1, 'COMPLETED', NULL, 'internal_control.pdf', '/uploads/agent-knowledge/internal_control.pdf', 'application/pdf', NOW(), NOW()),
(30, 4, '供应商评估体系', 'DOCUMENT', NULL, '供应商评估从质量、价格、交货、服务、财务状况等维度进行综合评价。', 1, 'FAILED', 'Document too large for embedding processing', 'supplier_evaluation.pdf', '/uploads/agent-knowledge/supplier_evaluation.pdf', 'application/pdf', NOW(), NOW());

-- 数据源示例数据
-- 示例数据源可以运行docker-compose-datasource.yml建立，或者手动修改为自己的数据源
INSERT IGNORE INTO `datasource` (`id`, `name`, `type`, `host`, `port`, `database_name`, `username`, `password`, `connection_url`, `status`, `test_status`, `description`, `creator_id`, `create_time`, `update_time`) VALUES 
(1, '生产环境MySQL数据库', 'mysql', 'mysql-data', 3306, 'product_db', 'root', 'root', 'jdbc:mysql://mysql-data:3306/product_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true', 'inactive', 'unknown', '生产环境主数据库，包含核心业务数据', 2100246635, NOW(), NOW()),
(2, '数据仓库PostgreSQL', 'postgresql', 'postgres-data', 5432, 'data_warehouse', 'postgres', 'postgres', 'jdbc:postgresql://postgres-data:5432/data_warehouse', 'inactive', 'unknown', '数据仓库，用于数据分析和报表生成', 2100246635, NOW(), NOW()),
(3, 'product_db', 'h2', null, null, 'product_db', 'root', 'root', 'jdbc:h2:mem:nl2sql_database;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE', 'inactive', 'unknown', 'h2测试数据库，包含核心业务数据', 2100246635, NOW(), NOW());

-- 智能体数据源关联示例数据
INSERT IGNORE INTO `agent_datasource` (`id`, `agent_id`, `datasource_id`, `is_active`, `create_time`, `update_time`) VALUES 
(1, 1, 2, 0, NOW(), NOW()),  -- 中国人口GDP数据智能体使用数据仓库
(2, 2, 3, 0, NOW(), NOW()),  -- 销售数据分析智能体使用生产环境数据库
(3, 3, 3, 0, NOW(), NOW()),  -- 财务报表智能体使用生产环境数据库
(4, 4, 3, 0, NOW(), NOW());  -- 库存管理智能体使用生产环境数据库
