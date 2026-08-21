-- ============================================================
-- 名远教育：资料商城（教师上传 + 家长付费购买 + 下载）
-- 包含：学科/年级字典、资料商品表、购买订单表、PC 端菜单与按钮权限
-- ============================================================

set names utf8mb4;

-- ----------------------------
-- 1. 字典类型：学科 / 年级
-- ----------------------------
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '学科', 'edu_subject', '0', 'admin', sysdate(), '资料商城-学科'
where not exists (select 1 from sys_dict_type where dict_type = 'edu_subject');

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select '年级', 'edu_grade', '0', 'admin', sysdate(), '资料商城-年级'
where not exists (select 1 from sys_dict_type where dict_type = 'edu_grade');

-- ----------------------------
-- 2. 字典数据：学科
-- ----------------------------
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '语文', 'chinese', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='chinese');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '数学', 'math', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='math');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '英语', 'english', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='english');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 4, '物理', 'physics', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='physics');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 5, '化学', 'chemistry', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='chemistry');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 6, '生物', 'biology', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='biology');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 7, '历史', 'history', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='history');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 8, '地理', 'geography', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='geography');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 9, '政治', 'politics', 'edu_subject', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_subject' and dict_value='politics');

-- ----------------------------
-- 3. 字典数据：年级
-- ----------------------------
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '一年级', 'g1', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g1');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '二年级', 'g2', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g2');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '三年级', 'g3', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g3');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 4, '四年级', 'g4', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g4');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 5, '五年级', 'g5', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g5');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 6, '六年级', 'g6', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g6');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 7, '初一', 'g7', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g7');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 8, '初二', 'g8', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g8');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 9, '初三', 'g9', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g9');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 10, '高一', 'g10', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g10');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 11, '高二', 'g11', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g11');
insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 12, '高三', 'g12', 'edu_grade', '', 'default', 'N', '0', 'admin', sysdate(), ''
where not exists (select 1 from sys_dict_data where dict_type='edu_grade' and dict_value='g12');

-- ----------------------------
-- 4. 资料商品表 edu_material
-- ----------------------------
DROP TABLE IF EXISTS `edu_material`;
CREATE TABLE `edu_material` (
  `material_id`    bigint        NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `material_code`  varchar(32)   NOT NULL                COMMENT '资料编码',
  `title`          varchar(100)  NOT NULL                COMMENT '资料标题',
  `subject_name`   varchar(30)   NOT NULL                COMMENT '学科',
  `grade_name`     varchar(30)   NOT NULL                COMMENT '年级',
  `price`          decimal(10,2) NOT NULL DEFAULT 0.00   COMMENT '价格（元）',
  `intro`          varchar(1000) DEFAULT ''              COMMENT '简介',
  `file_path`      varchar(500)  NOT NULL                COMMENT '资料文件相对路径',
  `file_name`      varchar(200)  NOT NULL                COMMENT '原始文件名',
  `file_size`      bigint        DEFAULT 0               COMMENT '文件大小（字节）',
  `cover_url`      varchar(500)  DEFAULT ''              COMMENT '封面图URL',
  `shelf_status`   char(1)       DEFAULT '1'             COMMENT '上架状态（0下架 1上架）',
  `sale_count`     int           DEFAULT 0               COMMENT '销量',
  `upload_user_id` bigint        NOT NULL                COMMENT '上传者用户ID',
  `status`         char(1)       DEFAULT '0'             COMMENT '状态（0正常 1停用）',
  `del_flag`       char(1)       DEFAULT '0'             COMMENT '删除标志（0存在 2删除）',
  `create_by`      varchar(64)   DEFAULT ''              COMMENT '创建者',
  `create_time`    datetime      DEFAULT NULL            COMMENT '创建时间',
  `update_by`      varchar(64)   DEFAULT ''              COMMENT '更新者',
  `update_time`    datetime      DEFAULT NULL            COMMENT '更新时间',
  `remark`         varchar(500)  DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (`material_id`),
  UNIQUE KEY `uk_edu_material_code` (`material_code`),
  KEY `idx_edu_material_subject` (`subject_name`, `grade_name`),
  KEY `idx_edu_material_shelf` (`shelf_status`, `status`),
  KEY `idx_edu_material_upload_user` (`upload_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料商品表';

-- ----------------------------
-- 5. 购买订单表 edu_material_order
-- ----------------------------
DROP TABLE IF EXISTS `edu_material_order`;
CREATE TABLE `edu_material_order` (
  `order_id`        bigint        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_code`      varchar(32)   NOT NULL                COMMENT '订单编码',
  `material_id`     bigint        NOT NULL                COMMENT '资料ID',
  `parent_id`       bigint        NOT NULL                COMMENT '家长用户ID',
  `amount`          decimal(10,2) NOT NULL                COMMENT '实付金额',
  `pay_status`      char(1)       DEFAULT '0'             COMMENT '支付状态（0未支付 1已支付 2已退款）',
  `pay_time`        datetime      DEFAULT NULL            COMMENT '支付时间',
  `pay_way`         char(1)       DEFAULT '0'             COMMENT '支付方式（0模拟 1微信）',
  `wx_transaction_id` varchar(64) DEFAULT ''              COMMENT '微信交易号',
  `refund_time`     datetime      DEFAULT NULL            COMMENT '退款时间',
  `refund_reason`   varchar(255)  DEFAULT ''               COMMENT '退款原因',
  `status`          char(1)       DEFAULT '0'             COMMENT '状态（0正常 1停用）',
  `del_flag`        char(1)       DEFAULT '0'             COMMENT '删除标志（0存在 2删除）',
  `create_by`       varchar(64)   DEFAULT ''              COMMENT '创建者',
  `create_time`     datetime      DEFAULT NULL            COMMENT '创建时间',
  `update_by`       varchar(64)   DEFAULT ''              COMMENT '更新者',
  `update_time`     datetime      DEFAULT NULL            COMMENT '更新时间',
  `remark`          varchar(500)  DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_edu_material_order_code` (`order_code`),
  UNIQUE KEY `uk_edu_material_order_once` (`material_id`, `parent_id`),
  KEY `idx_edu_material_order_parent` (`parent_id`, `pay_status`),
  CONSTRAINT `fk_edu_material_order_material` FOREIGN KEY (`material_id`) REFERENCES `edu_material` (`material_id`),
  CONSTRAINT `fk_edu_material_order_parent`   FOREIGN KEY (`parent_id`)   REFERENCES `sys_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资料订单表';

-- ----------------------------
-- 6. PC 端菜单：资料管理（挂在"系统工具"下，与 enrollment/schedule 风格一致）
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('资料管理', '3', '10', 'material', 'system/material/index', 1, 0, 'C', '0', '0', 'system:material:list', '#', 'admin', sysdate(), '', null, '资料商品菜单');

SELECT @materialParentId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('资料查询', @materialParentId, '1',  '#', '', 1, 0, 'F', '0', '0', 'system:material:query',  '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('资料新增', @materialParentId, '2',  '#', '', 1, 0, 'F', '0', '0', 'system:material:add',    '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('资料修改', @materialParentId, '3',  '#', '', 1, 0, 'F', '0', '0', 'system:material:edit',   '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('资料删除', @materialParentId, '4',  '#', '', 1, 0, 'F', '0', '0', 'system:material:remove','#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('资料导出', @materialParentId, '5',  '#', '', 1, 0, 'F', '0', '0', 'system:material:export','#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 7. PC 端菜单：资料订单
-- ----------------------------
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('资料订单', '3', '11', 'materialOrder', 'system/materialOrder/index', 1, 0, 'C', '0', '0', 'system:materialOrder:list', '#', 'admin', sysdate(), '', null, '资料购买订单菜单');

SELECT @orderParentId := LAST_INSERT_ID();

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('订单查询', @orderParentId, '1', '#', '', 1, 0, 'F', '0', '0', 'system:materialOrder:query', '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('订单导出', @orderParentId, '2', '#', '', 1, 0, 'F', '0', '0', 'system:materialOrder:export','#', 'admin', sysdate(), '', null, '');
