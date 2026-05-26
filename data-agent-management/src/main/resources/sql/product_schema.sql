-- 电商示例库 Schema（MySQL）

-- 用户表
CREATE TABLE users (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  user_no VARCHAR(32) NOT NULL UNIQUE COMMENT '用户编号',
  username VARCHAR(64) NOT NULL COMMENT '用户名',
  email VARCHAR(128) NOT NULL COMMENT '邮箱',
  gender VARCHAR(16) DEFAULT NULL COMMENT '性别',
  birth_date DATE DEFAULT NULL COMMENT '出生日期',
  register_channel VARCHAR(32) DEFAULT NULL COMMENT '注册渠道',
  member_level VARCHAR(32) DEFAULT NULL COMMENT '会员等级',
  province VARCHAR(64) DEFAULT NULL COMMENT '省份',
  city VARCHAR(64) DEFAULT NULL COMMENT '城市',
  is_premium TINYINT DEFAULT 0 COMMENT '是否高价值会员',
  lifetime_value DECIMAL(12, 2) DEFAULT 0 COMMENT '生命周期价值',
  status VARCHAR(16) DEFAULT 'active' COMMENT '用户状态',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  last_active_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  INDEX idx_users_created_at (created_at),
  INDEX idx_users_member_level (member_level),
  INDEX idx_users_city (city),
  INDEX idx_users_status (status)
) COMMENT='电商用户表';

-- 品牌表
CREATE TABLE brands (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '品牌ID',
  brand_name VARCHAR(64) NOT NULL UNIQUE COMMENT '品牌名称',
  brand_tier VARCHAR(32) DEFAULT NULL COMMENT '品牌层级',
  origin_country VARCHAR(64) DEFAULT NULL COMMENT '品牌所属国家',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='商品品牌表';

-- 类目表
CREATE TABLE categories (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '类目ID',
  parent_id INT DEFAULT NULL COMMENT '父类目ID',
  category_name VARCHAR(64) NOT NULL COMMENT '类目名称',
  category_level INT NOT NULL COMMENT '类目层级',
  is_leaf TINYINT DEFAULT 1 COMMENT '是否叶子类目',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_categories_parent_id (parent_id),
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
) COMMENT='商品类目表';

-- 仓库表
CREATE TABLE warehouses (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '仓库ID',
  warehouse_code VARCHAR(32) NOT NULL UNIQUE COMMENT '仓库编码',
  warehouse_name VARCHAR(128) NOT NULL COMMENT '仓库名称',
  province VARCHAR(64) NOT NULL COMMENT '所在省份',
  city VARCHAR(64) NOT NULL COMMENT '所在城市',
  warehouse_type VARCHAR(32) NOT NULL COMMENT '仓库类型',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='仓库表';

-- 营销活动表
CREATE TABLE promotions (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '活动ID',
  promotion_code VARCHAR(32) NOT NULL UNIQUE COMMENT '活动编码',
  promotion_name VARCHAR(128) NOT NULL COMMENT '活动名称',
  promotion_type VARCHAR(32) NOT NULL COMMENT '活动类型',
  threshold_amount DECIMAL(10, 2) DEFAULT 0 COMMENT '满减门槛',
  discount_amount DECIMAL(10, 2) DEFAULT 0 COMMENT '固定优惠金额',
  discount_rate DECIMAL(5, 2) DEFAULT 0 COMMENT '折扣率',
  start_time DATETIME NOT NULL COMMENT '开始时间',
  end_time DATETIME NOT NULL COMMENT '结束时间',
  status VARCHAR(16) NOT NULL COMMENT '活动状态',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_promotions_status (status),
  INDEX idx_promotions_time (start_time, end_time)
) COMMENT='营销活动表';

-- 商品表
CREATE TABLE products (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID',
  spu_code VARCHAR(32) NOT NULL UNIQUE COMMENT 'SPU编码',
  sku_code VARCHAR(32) NOT NULL UNIQUE COMMENT 'SKU编码',
  product_name VARCHAR(128) NOT NULL COMMENT '商品名称',
  brand_id INT NOT NULL COMMENT '品牌ID',
  category_id INT NOT NULL COMMENT '叶子类目ID',
  market_price DECIMAL(10, 2) NOT NULL COMMENT '吊牌价',
  sale_price DECIMAL(10, 2) NOT NULL COMMENT '销售价',
  cost_price DECIMAL(10, 2) NOT NULL COMMENT '成本价',
  stock_quantity INT NOT NULL COMMENT '当前库存',
  safety_stock INT NOT NULL COMMENT '安全库存',
  rating DECIMAL(3, 2) DEFAULT 4.50 COMMENT '商品评分',
  status VARCHAR(16) DEFAULT 'active' COMMENT '商品状态',
  launch_date DATE DEFAULT NULL COMMENT '上架日期',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_products_brand_id (brand_id),
  INDEX idx_products_category_id (category_id),
  INDEX idx_products_status (status),
  INDEX idx_products_launch_date (launch_date),
  CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands(id),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id)
) COMMENT='商品表';

-- 订单表
CREATE TABLE orders (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
  order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
  user_id INT NOT NULL COMMENT '下单用户ID',
  order_time DATETIME NOT NULL COMMENT '下单时间',
  order_status VARCHAR(32) NOT NULL COMMENT '订单状态',
  payment_status VARCHAR(32) NOT NULL COMMENT '支付状态',
  fulfillment_status VARCHAR(32) NOT NULL COMMENT '履约状态',
  order_channel VARCHAR(32) NOT NULL COMMENT '下单渠道',
  province VARCHAR(64) DEFAULT NULL COMMENT '收货省份',
  city VARCHAR(64) DEFAULT NULL COMMENT '收货城市',
  gross_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '商品原始金额',
  discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
  shipping_fee DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '运费',
  refund_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '退款金额',
  net_amount DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '订单净收入',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_orders_user_id (user_id),
  INDEX idx_orders_order_time (order_time),
  INDEX idx_orders_order_status (order_status),
  INDEX idx_orders_payment_status (payment_status),
  INDEX idx_orders_fulfillment_status (fulfillment_status),
  INDEX idx_orders_channel (order_channel),
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='订单主表';

-- 订单明细表
CREATE TABLE order_items (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细ID',
  order_id INT NOT NULL COMMENT '订单ID',
  product_id INT NOT NULL COMMENT '商品ID',
  warehouse_id INT NOT NULL COMMENT '履约仓库ID',
  promotion_id INT DEFAULT NULL COMMENT '命中的活动ID',
  quantity INT NOT NULL COMMENT '购买件数',
  unit_price DECIMAL(10, 2) NOT NULL COMMENT '成交单价',
  item_discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '明细优惠金额',
  final_amount DECIMAL(12, 2) NOT NULL COMMENT '明细实付金额',
  is_returned TINYINT NOT NULL DEFAULT 0 COMMENT '是否已退货',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_order_items_order_id (order_id),
  INDEX idx_order_items_product_id (product_id),
  INDEX idx_order_items_warehouse_id (warehouse_id),
  INDEX idx_order_items_promotion_id (promotion_id),
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_order_items_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  CONSTRAINT fk_order_items_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id)
) COMMENT='订单明细表';

-- 支付表
CREATE TABLE payments (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '支付ID',
  order_id INT NOT NULL COMMENT '订单ID',
  payment_no VARCHAR(32) NOT NULL UNIQUE COMMENT '支付流水号',
  payment_method VARCHAR(32) NOT NULL COMMENT '支付方式',
  payment_amount DECIMAL(12, 2) NOT NULL COMMENT '支付金额',
  payment_status VARCHAR(32) NOT NULL COMMENT '支付状态',
  paid_time DATETIME DEFAULT NULL COMMENT '支付完成时间',
  callback_time DATETIME DEFAULT NULL COMMENT '回调确认时间',
  INDEX idx_payments_order_id (order_id),
  INDEX idx_payments_paid_time (paid_time),
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders(id)
) COMMENT='支付流水表';

-- 发货表
CREATE TABLE shipments (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '发货ID',
  order_id INT NOT NULL COMMENT '订单ID',
  warehouse_id INT NOT NULL COMMENT '发货仓库ID',
  carrier_name VARCHAR(64) NOT NULL COMMENT '承运商',
  tracking_no VARCHAR(64) NOT NULL UNIQUE COMMENT '运单号',
  shipped_time DATETIME DEFAULT NULL COMMENT '发货时间',
  delivered_time DATETIME DEFAULT NULL COMMENT '签收时间',
  delivery_status VARCHAR(32) NOT NULL COMMENT '配送状态',
  INDEX idx_shipments_order_id (order_id),
  INDEX idx_shipments_warehouse_id (warehouse_id),
  INDEX idx_shipments_delivery_status (delivery_status),
  CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_shipments_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) COMMENT='履约发货表';

-- 退款表
CREATE TABLE refunds (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '退款ID',
  order_id INT NOT NULL COMMENT '订单ID',
  order_item_id INT NOT NULL COMMENT '退款关联明细ID',
  refund_reason VARCHAR(128) NOT NULL COMMENT '退款原因',
  refund_amount DECIMAL(12, 2) NOT NULL COMMENT '退款金额',
  refund_status VARCHAR(32) NOT NULL COMMENT '退款状态',
  requested_time DATETIME NOT NULL COMMENT '申请退款时间',
  completed_time DATETIME DEFAULT NULL COMMENT '退款完成时间',
  INDEX idx_refunds_order_id (order_id),
  INDEX idx_refunds_order_item_id (order_item_id),
  INDEX idx_refunds_status (refund_status),
  CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_refunds_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id)
) COMMENT='退款表';

-- 商品评价表
CREATE TABLE reviews (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
  order_item_id INT NOT NULL COMMENT '订单明细ID',
  user_id INT NOT NULL COMMENT '评价用户ID',
  rating INT NOT NULL COMMENT '评分',
  review_tag VARCHAR(64) DEFAULT NULL COMMENT '评价标签',
  review_content VARCHAR(512) DEFAULT NULL COMMENT '评价内容',
  review_time DATETIME NOT NULL COMMENT '评价时间',
  INDEX idx_reviews_order_item_id (order_item_id),
  INDEX idx_reviews_user_id (user_id),
  INDEX idx_reviews_rating (rating),
  CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
  CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='商品评价表';

-- 客服工单表
CREATE TABLE customer_service_tickets (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',
  order_id INT NOT NULL COMMENT '订单ID',
  user_id INT NOT NULL COMMENT '用户ID',
  ticket_type VARCHAR(64) NOT NULL COMMENT '工单类型',
  priority VARCHAR(16) NOT NULL COMMENT '优先级',
  status VARCHAR(32) NOT NULL COMMENT '工单状态',
  satisfaction_score INT DEFAULT NULL COMMENT '处理满意度',
  created_time DATETIME NOT NULL COMMENT '创建时间',
  resolved_time DATETIME DEFAULT NULL COMMENT '解决时间',
  INDEX idx_tickets_order_id (order_id),
  INDEX idx_tickets_user_id (user_id),
  INDEX idx_tickets_status (status),
  CONSTRAINT fk_tickets_order FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='客服工单表';

-- 库存快照表
CREATE TABLE inventory_snapshots (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '快照ID',
  snapshot_date DATE NOT NULL COMMENT '快照日期',
  product_id INT NOT NULL COMMENT '商品ID',
  warehouse_id INT NOT NULL COMMENT '仓库ID',
  available_stock INT NOT NULL COMMENT '可售库存',
  locked_stock INT NOT NULL COMMENT '锁定库存',
  inbound_qty INT NOT NULL COMMENT '在途数量',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_inventory_snapshot (snapshot_date, product_id, warehouse_id),
  INDEX idx_inventory_snapshot_date (snapshot_date),
  INDEX idx_inventory_product_id (product_id),
  INDEX idx_inventory_warehouse_id (warehouse_id),
  CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
) COMMENT='库存日快照表';
