-- 用户表
CREATE TABLE users (
                       id INT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID，主键自增',
                       username VARCHAR(50) NOT NULL COMMENT '用户名',
                       email VARCHAR(100) NOT NULL COMMENT '用户邮箱',
                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '用户注册时间'
) COMMENT='用户表';

-- 商品表
CREATE TABLE products (
                          id INT PRIMARY KEY AUTO_INCREMENT COMMENT '商品ID，主键自增',
                          name VARCHAR(100) NOT NULL COMMENT '商品名称',
                          price DECIMAL(10,2) NOT NULL COMMENT '商品单价',
                          stock INT NOT NULL COMMENT '商品库存数量',
                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '商品上架时间'
) COMMENT='商品表';

-- 订单表
CREATE TABLE orders (
                        id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID，主键自增',
                        user_id INT NOT NULL COMMENT '下单用户ID',
                        order_date DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
                        total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
                        status VARCHAR(20) DEFAULT 'pending' COMMENT '订单状态（pending/completed/cancelled等）',
                        FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='订单表';

-- 订单明细表
CREATE TABLE order_items (
                             id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细ID，主键自增',
                             order_id INT NOT NULL COMMENT '订单ID',
                             product_id INT NOT NULL COMMENT '商品ID',
                             quantity INT NOT NULL COMMENT '购买数量',
                             unit_price DECIMAL(10,2) NOT NULL COMMENT '下单时商品单价',
                             FOREIGN KEY (order_id) REFERENCES orders(id),
                             FOREIGN KEY (product_id) REFERENCES products(id)
) COMMENT='订单明细表';

-- 商品分类表
CREATE TABLE categories (
                            id INT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID，主键自增',
                            name VARCHAR(50) NOT NULL COMMENT '分类名称'
) COMMENT='商品分类表';

-- 商品-分类关联表（多对多）
CREATE TABLE product_categories (
                                    product_id INT NOT NULL COMMENT '商品ID',
                                    category_id INT NOT NULL COMMENT '分类ID',
                                    PRIMARY KEY (product_id, category_id),
                                    FOREIGN KEY (product_id) REFERENCES products(id),
                                    FOREIGN KEY (category_id) REFERENCES categories(id)
) COMMENT='商品与分类关联表';
