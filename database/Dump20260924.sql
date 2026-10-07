-- Migration test fixture: four historical table structures, NO original rows.
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

CREATE TABLE `sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';

CREATE TABLE `edu_material` (
  `material_id` bigint NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `material_code` varchar(32) NOT NULL COMMENT '资料编码',
  `title` varchar(100) NOT NULL COMMENT '资料标题',
  `subject_name` varchar(30) NOT NULL COMMENT '学科',
  `grade_name` varchar(30) NOT NULL COMMENT '年级',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '价格（元）',
  `intro` varchar(1000) DEFAULT '' COMMENT '简介',
  `file_path` varchar(500) NOT NULL COMMENT '资料文件相对路径',
  `file_name` varchar(200) NOT NULL COMMENT '原始文件名',
  `file_size` bigint DEFAULT '0' COMMENT '文件大小（字节）',
  `cover_url` varchar(500) DEFAULT '' COMMENT '封面图URL',
  `shelf_status` char(1) DEFAULT '1' COMMENT '上架状态（0下架 1上架）',
  `sale_count` int DEFAULT '0' COMMENT '销量',
  `upload_user_id` bigint NOT NULL COMMENT '上传者用户ID',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`material_id`),
  UNIQUE KEY `uk_edu_material_code` (`material_code`),
  KEY `idx_edu_material_subject` (`subject_name`,`grade_name`),
  KEY `idx_edu_material_shelf` (`shelf_status`,`status`),
  KEY `idx_edu_material_upload_user` (`upload_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料商品表';

CREATE TABLE `edu_material_order` (
  `order_id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_code` varchar(32) NOT NULL COMMENT '订单编码',
  `material_id` bigint NOT NULL COMMENT '资料ID',
  `parent_id` bigint NOT NULL COMMENT '家长用户ID',
  `amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `pay_status` char(1) DEFAULT '0' COMMENT '支付状态（0未支付 1已支付 2已退款）',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `pay_way` char(1) DEFAULT '0' COMMENT '支付方式（0模拟 1微信）',
  `wx_transaction_id` varchar(64) DEFAULT '' COMMENT '微信交易号',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `refund_reason` varchar(255) DEFAULT '' COMMENT '退款原因',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_edu_material_order_code` (`order_code`),
  UNIQUE KEY `uk_edu_material_order_once` (`material_id`,`parent_id`),
  KEY `idx_edu_material_order_parent` (`parent_id`,`pay_status`),
  CONSTRAINT `fk_edu_material_order_material` FOREIGN KEY (`material_id`) REFERENCES `edu_material` (`material_id`),
  CONSTRAINT `fk_edu_material_order_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料订单表';

