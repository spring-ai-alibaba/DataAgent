-- 电商示例库 Schema（H2）
CREATE SCHEMA IF NOT EXISTS product_db;

CREATE TABLE product_db.users (
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
  last_active_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间'
) COMMENT='电商用户表';
CREATE INDEX idx_users_created_at ON product_db.users(created_at);
CREATE INDEX idx_users_member_level ON product_db.users(member_level);
CREATE INDEX idx_users_city ON product_db.users(city);
CREATE INDEX idx_users_status ON product_db.users(status);

CREATE TABLE product_db.brands (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '品牌ID',
  brand_name VARCHAR(64) NOT NULL UNIQUE COMMENT '品牌名称',
  brand_tier VARCHAR(32) DEFAULT NULL COMMENT '品牌层级',
  origin_country VARCHAR(64) DEFAULT NULL COMMENT '品牌所属国家',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='商品品牌表';

CREATE TABLE product_db.categories (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '类目ID',
  parent_id INT DEFAULT NULL COMMENT '父类目ID',
  category_name VARCHAR(64) NOT NULL COMMENT '类目名称',
  category_level INT NOT NULL COMMENT '类目层级',
  is_leaf TINYINT DEFAULT 1 COMMENT '是否叶子类目',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES product_db.categories(id)
) COMMENT='商品类目表';
CREATE INDEX idx_categories_parent_id ON product_db.categories(parent_id);

CREATE TABLE product_db.warehouses (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '仓库ID',
  warehouse_code VARCHAR(32) NOT NULL UNIQUE COMMENT '仓库编码',
  warehouse_name VARCHAR(128) NOT NULL COMMENT '仓库名称',
  province VARCHAR(64) NOT NULL COMMENT '所在省份',
  city VARCHAR(64) NOT NULL COMMENT '所在城市',
  warehouse_type VARCHAR(32) NOT NULL COMMENT '仓库类型',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='仓库表';

CREATE TABLE product_db.promotions (
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
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='营销活动表';
CREATE INDEX idx_promotions_status ON product_db.promotions(status);
CREATE INDEX idx_promotions_time ON product_db.promotions(start_time, end_time);

CREATE TABLE product_db.products (
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
  CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES product_db.brands(id),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_db.categories(id)
) COMMENT='商品表';
CREATE INDEX idx_products_brand_id ON product_db.products(brand_id);
CREATE INDEX idx_products_category_id ON product_db.products(category_id);
CREATE INDEX idx_products_status ON product_db.products(status);
CREATE INDEX idx_products_launch_date ON product_db.products(launch_date);

CREATE TABLE product_db.orders (
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
  CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES product_db.users(id)
) COMMENT='订单主表';
CREATE INDEX idx_orders_user_id ON product_db.orders(user_id);
CREATE INDEX idx_orders_order_time ON product_db.orders(order_time);
CREATE INDEX idx_orders_order_status ON product_db.orders(order_status);
CREATE INDEX idx_orders_payment_status ON product_db.orders(payment_status);
CREATE INDEX idx_orders_fulfillment_status ON product_db.orders(fulfillment_status);
CREATE INDEX idx_orders_channel ON product_db.orders(order_channel);

CREATE TABLE product_db.order_items (
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
  CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES product_db.orders(id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES product_db.products(id),
  CONSTRAINT fk_order_items_warehouse FOREIGN KEY (warehouse_id) REFERENCES product_db.warehouses(id),
  CONSTRAINT fk_order_items_promotion FOREIGN KEY (promotion_id) REFERENCES product_db.promotions(id)
) COMMENT='订单明细表';
CREATE INDEX idx_order_items_order_id ON product_db.order_items(order_id);
CREATE INDEX idx_order_items_product_id ON product_db.order_items(product_id);
CREATE INDEX idx_order_items_warehouse_id ON product_db.order_items(warehouse_id);
CREATE INDEX idx_order_items_promotion_id ON product_db.order_items(promotion_id);

CREATE TABLE product_db.payments (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '支付ID',
  order_id INT NOT NULL COMMENT '订单ID',
  payment_no VARCHAR(32) NOT NULL UNIQUE COMMENT '支付流水号',
  payment_method VARCHAR(32) NOT NULL COMMENT '支付方式',
  payment_amount DECIMAL(12, 2) NOT NULL COMMENT '支付金额',
  payment_status VARCHAR(32) NOT NULL COMMENT '支付状态',
  paid_time DATETIME DEFAULT NULL COMMENT '支付完成时间',
  callback_time DATETIME DEFAULT NULL COMMENT '回调确认时间',
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES product_db.orders(id)
) COMMENT='支付流水表';
CREATE INDEX idx_payments_order_id ON product_db.payments(order_id);
CREATE INDEX idx_payments_paid_time ON product_db.payments(paid_time);

CREATE TABLE product_db.shipments (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '发货ID',
  order_id INT NOT NULL COMMENT '订单ID',
  warehouse_id INT NOT NULL COMMENT '发货仓库ID',
  carrier_name VARCHAR(64) NOT NULL COMMENT '承运商',
  tracking_no VARCHAR(64) NOT NULL UNIQUE COMMENT '运单号',
  shipped_time DATETIME DEFAULT NULL COMMENT '发货时间',
  delivered_time DATETIME DEFAULT NULL COMMENT '签收时间',
  delivery_status VARCHAR(32) NOT NULL COMMENT '配送状态',
  CONSTRAINT fk_shipments_order FOREIGN KEY (order_id) REFERENCES product_db.orders(id),
  CONSTRAINT fk_shipments_warehouse FOREIGN KEY (warehouse_id) REFERENCES product_db.warehouses(id)
) COMMENT='履约发货表';
CREATE INDEX idx_shipments_order_id ON product_db.shipments(order_id);
CREATE INDEX idx_shipments_warehouse_id ON product_db.shipments(warehouse_id);
CREATE INDEX idx_shipments_delivery_status ON product_db.shipments(delivery_status);

CREATE TABLE product_db.refunds (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '退款ID',
  order_id INT NOT NULL COMMENT '订单ID',
  order_item_id INT NOT NULL COMMENT '退款关联明细ID',
  refund_reason VARCHAR(128) NOT NULL COMMENT '退款原因',
  refund_amount DECIMAL(12, 2) NOT NULL COMMENT '退款金额',
  refund_status VARCHAR(32) NOT NULL COMMENT '退款状态',
  requested_time DATETIME NOT NULL COMMENT '申请退款时间',
  completed_time DATETIME DEFAULT NULL COMMENT '退款完成时间',
  CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES product_db.orders(id),
  CONSTRAINT fk_refunds_order_item FOREIGN KEY (order_item_id) REFERENCES product_db.order_items(id)
) COMMENT='退款表';
CREATE INDEX idx_refunds_order_id ON product_db.refunds(order_id);
CREATE INDEX idx_refunds_order_item_id ON product_db.refunds(order_item_id);
CREATE INDEX idx_refunds_status ON product_db.refunds(refund_status);

CREATE TABLE product_db.reviews (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
  order_item_id INT NOT NULL COMMENT '订单明细ID',
  user_id INT NOT NULL COMMENT '评价用户ID',
  rating INT NOT NULL COMMENT '评分',
  review_tag VARCHAR(64) DEFAULT NULL COMMENT '评价标签',
  review_content VARCHAR(512) DEFAULT NULL COMMENT '评价内容',
  review_time DATETIME NOT NULL COMMENT '评价时间',
  CONSTRAINT fk_reviews_order_item FOREIGN KEY (order_item_id) REFERENCES product_db.order_items(id),
  CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES product_db.users(id)
) COMMENT='商品评价表';
CREATE INDEX idx_reviews_order_item_id ON product_db.reviews(order_item_id);
CREATE INDEX idx_reviews_user_id ON product_db.reviews(user_id);
CREATE INDEX idx_reviews_rating ON product_db.reviews(rating);

CREATE TABLE product_db.customer_service_tickets (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',
  order_id INT NOT NULL COMMENT '订单ID',
  user_id INT NOT NULL COMMENT '用户ID',
  ticket_type VARCHAR(64) NOT NULL COMMENT '工单类型',
  priority VARCHAR(16) NOT NULL COMMENT '优先级',
  status VARCHAR(32) NOT NULL COMMENT '工单状态',
  satisfaction_score INT DEFAULT NULL COMMENT '处理满意度',
  created_time DATETIME NOT NULL COMMENT '创建时间',
  resolved_time DATETIME DEFAULT NULL COMMENT '解决时间',
  CONSTRAINT fk_tickets_order FOREIGN KEY (order_id) REFERENCES product_db.orders(id),
  CONSTRAINT fk_tickets_user FOREIGN KEY (user_id) REFERENCES product_db.users(id)
) COMMENT='客服工单表';
CREATE INDEX idx_tickets_order_id ON product_db.customer_service_tickets(order_id);
CREATE INDEX idx_tickets_user_id ON product_db.customer_service_tickets(user_id);
CREATE INDEX idx_tickets_status ON product_db.customer_service_tickets(status);

CREATE TABLE product_db.inventory_snapshots (
  id INT PRIMARY KEY AUTO_INCREMENT COMMENT '快照ID',
  snapshot_date DATE NOT NULL COMMENT '快照日期',
  product_id INT NOT NULL COMMENT '商品ID',
  warehouse_id INT NOT NULL COMMENT '仓库ID',
  available_stock INT NOT NULL COMMENT '可售库存',
  locked_stock INT NOT NULL COMMENT '锁定库存',
  inbound_qty INT NOT NULL COMMENT '在途数量',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  CONSTRAINT uk_inventory_snapshot UNIQUE (snapshot_date, product_id, warehouse_id),
  CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES product_db.products(id),
  CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES product_db.warehouses(id)
) COMMENT='库存日快照表';
CREATE INDEX idx_inventory_snapshot_date ON product_db.inventory_snapshots(snapshot_date);
CREATE INDEX idx_inventory_product_id ON product_db.inventory_snapshots(product_id);
CREATE INDEX idx_inventory_warehouse_id ON product_db.inventory_snapshots(warehouse_id);
