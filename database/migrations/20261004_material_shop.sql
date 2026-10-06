-- Apply in a maintenance window after backup. Explicitly USE the intended schema.
-- Repeatable DDL; backfill is guarded by a ledger and a DML transaction.
CREATE TABLE IF NOT EXISTS edu_shop_migration(version varchar(64) PRIMARY KEY, applied_time datetime NOT NULL);
-- Dictionary defaults are additive, including when the shop backfill ledger already exists.
-- Never rewrite operator-managed labels, sorting or enabled/disabled state on replay.
INSERT INTO sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
SELECT '资料类型','edu_material_type','0','admin',NOW(),'资料商城商品类型'
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_type WHERE dict_type='edu_material_type');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT defaults.sort_order,defaults.label,defaults.code,'edu_material_type','N','0','admin',NOW()
FROM (SELECT 1 AS sort_order,'讲义' AS label,'handout' AS code
      UNION ALL SELECT 2,'试卷','exam'
      UNION ALL SELECT 3,'练习册','workbook'
      UNION ALL SELECT 4,'其他','other') defaults
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data existing WHERE existing.dict_type='edu_material_type' AND existing.dict_value=defaults.code);
DELIMITER $$
DROP PROCEDURE IF EXISTS shop_add_column$$
CREATE PROCEDURE shop_add_column(IN t varchar(64), IN c varchar(64), IN definition text)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN
 SET @shop_ddl=CONCAT('ALTER TABLE ',t,' ADD COLUMN ',c,' ',definition);
 PREPARE s FROM @shop_ddl; EXECUTE s; DEALLOCATE PREPARE s;
 END IF;
END$$
DROP PROCEDURE IF EXISTS shop_add_index$$
CREATE PROCEDURE shop_add_index(IN t varchar(64), IN n varchar(64), IN definition text)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=t AND index_name=n) THEN
 SET @shop_ddl=CONCAT('ALTER TABLE ',t,' ADD ',definition);
 PREPARE s FROM @shop_ddl; EXECUTE s; DEALLOCATE PREPARE s;
 END IF;
END$$
DELIMITER ;
CALL shop_add_column('edu_material','subtitle','varchar(200)');
CALL shop_add_column('edu_material','subject_code','varchar(30)');
CALL shop_add_column('edu_material','grade_code','varchar(30)');
CALL shop_add_column('edu_material','material_type','varchar(30)');
CALL shop_add_column('edu_material','textbook_version','varchar(100)');
CALL shop_add_column('edu_material','delivery_type','varchar(16) NOT NULL DEFAULT ''DIGITAL''');
CALL shop_add_column('edu_material','detail_text','text');
CALL shop_add_column('edu_material','unit','varchar(20) NOT NULL DEFAULT ''份''');
CALL shop_add_column('edu_material','stock_quantity','int NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material','stock_locked','int NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material','purchase_limit','int NOT NULL DEFAULT 99');
CALL shop_add_column('edu_material','shipping_fee','decimal(10,2) NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material','ship_from','varchar(200)');
CALL shop_add_column('edu_material','dispatch_days','int');
CALL shop_add_column('edu_material','sort_order','int NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material','version','int NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material','legacy_free','tinyint NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material_order','order_status','varchar(24)');
CALL shop_add_column('edu_material_order','aftersale_status','varchar(16) NOT NULL DEFAULT ''NONE''');
CALL shop_add_column('edu_material_order','goods_amount','decimal(10,2)');
CALL shop_add_column('edu_material_order','shipping_amount','decimal(10,2) NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material_order','payable_amount','decimal(10,2)');
CALL shop_add_column('edu_material_order','refunded_amount','decimal(10,2) NOT NULL DEFAULT 0');
CALL shop_add_column('edu_material_order','total_quantity','int NOT NULL DEFAULT 1');
CALL shop_add_column('edu_material_order','delivery_type','varchar(16) NOT NULL DEFAULT ''DIGITAL''');
CALL shop_add_column('edu_material_order','address_id','bigint');
CALL shop_add_column('edu_material_order','receiver_name','varchar(50)');
CALL shop_add_column('edu_material_order','receiver_phone','varchar(32)');
CALL shop_add_column('edu_material_order','province_code','varchar(12)');
CALL shop_add_column('edu_material_order','city_code','varchar(12)');
CALL shop_add_column('edu_material_order','district_code','varchar(12)');
CALL shop_add_column('edu_material_order','province_name','varchar(50)');
CALL shop_add_column('edu_material_order','city_name','varchar(50)');
CALL shop_add_column('edu_material_order','district_name','varchar(50)');
CALL shop_add_column('edu_material_order','detail_address','varchar(255)');
CALL shop_add_column('edu_material_order','postal_code','varchar(10)');
CALL shop_add_column('edu_material_order','buyer_remark','varchar(500)');
CALL shop_add_column('edu_material_order','admin_remark','varchar(500)');
CALL shop_add_column('edu_material_order','client_request_id','varchar(64) COLLATE utf8mb4_bin');
CALL shop_add_column('edu_material_order','request_hash','char(64)');
CALL shop_add_column('edu_material_order','expire_time','datetime');
CALL shop_add_column('edu_material_order','cancel_time','datetime');
CALL shop_add_column('edu_material_order','finish_time','datetime');
CALL shop_add_column('edu_material_order','close_reason','varchar(255)');
CALL shop_add_column('edu_material_order','stock_state','varchar(16) NOT NULL DEFAULT ''NOT_APPLICABLE''');
ALTER TABLE edu_material MODIFY file_path varchar(500) NULL, MODIFY file_name varchar(200) NULL, MODIFY shelf_status char(1) NOT NULL DEFAULT '2';
ALTER TABLE edu_material_order MODIFY wx_transaction_id varchar(64) NULL;
CALL shop_add_index('edu_material','idx_shop_sale','INDEX idx_shop_sale(del_flag,status,shelf_status,sort_order,material_id)');
CALL shop_add_index('edu_material','idx_shop_category','INDEX idx_shop_category(subject_code,grade_code,shelf_status)');
CALL shop_add_index('edu_material_order','uk_shop_request','UNIQUE INDEX uk_shop_request(parent_id,client_request_id)');
-- The old unique index is also the only material FK support index in the original schema.
CALL shop_add_index('edu_material_order','idx_shop_material','INDEX idx_shop_material(material_id)');
CALL shop_add_index('edu_material_order','idx_shop_expiry','INDEX idx_shop_expiry(order_status,expire_time)');
CALL shop_add_index('edu_material_order','idx_shop_user_status','INDEX idx_shop_user_status(parent_id,order_status,create_time)');
CALL shop_add_index('edu_material_order','idx_shop_status','INDEX idx_shop_status(order_status,create_time)');
CALL shop_add_index('edu_material_order','idx_shop_paid','INDEX idx_shop_paid(pay_status,pay_time)');
DELIMITER $$
DROP PROCEDURE IF EXISTS shop_remove_once$$
CREATE PROCEDURE shop_remove_once()
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='edu_material_order' AND index_name='uk_edu_material_order_once') THEN
 ALTER TABLE edu_material_order DROP INDEX uk_edu_material_order_once;
 END IF;
END$$
CALL shop_remove_once()$$
DROP PROCEDURE shop_remove_once$$
DELIMITER ;
CREATE TABLE IF NOT EXISTS edu_material_image(
 image_id bigint PRIMARY KEY AUTO_INCREMENT, material_id bigint NOT NULL, image_type varchar(16) NOT NULL,
 image_url varchar(500) NOT NULL, sort_order int NOT NULL DEFAULT 0, create_time datetime NOT NULL,
 FOREIGN KEY(material_id) REFERENCES edu_material(material_id), INDEX(material_id,image_type,sort_order),
 CHECK(image_type IN ('GALLERY','DETAIL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS edu_user_address(
 address_id bigint PRIMARY KEY AUTO_INCREMENT, user_id bigint NOT NULL, receiver_name varchar(50) NOT NULL,
 receiver_phone varchar(32) NOT NULL, province_code varchar(12), city_code varchar(12), district_code varchar(12),
 province_name varchar(50) NOT NULL, city_name varchar(50) NOT NULL, district_name varchar(50) NOT NULL,
 detail_address varchar(255) NOT NULL, postal_code varchar(10), is_default tinyint NOT NULL DEFAULT 0,
 del_flag char(1) NOT NULL DEFAULT '0', create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime,
 FOREIGN KEY(user_id) REFERENCES sys_user(user_id), INDEX(user_id,del_flag,is_default), CHECK(is_default IN(0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS edu_material_order_item(
 item_id bigint PRIMARY KEY AUTO_INCREMENT, order_id bigint NOT NULL, material_id bigint NOT NULL,
 material_code varchar(32) NOT NULL, material_title varchar(100) NOT NULL, cover_url varchar(500),
 subject_code varchar(30), grade_code varchar(30), subject_label varchar(50), grade_label varchar(50),
 unit varchar(20) NOT NULL, delivery_type varchar(16) NOT NULL, unit_price decimal(10,2) NOT NULL,
 quantity int NOT NULL, line_amount decimal(10,2) NOT NULL, asset_path varchar(500), asset_name varchar(200), asset_size bigint,
 create_time datetime NOT NULL, FOREIGN KEY(order_id) REFERENCES edu_material_order(order_id),
 FOREIGN KEY(material_id) REFERENCES edu_material(material_id), UNIQUE(order_id,material_id), INDEX(material_id,order_id),
 CHECK(quantity>0 AND unit_price>=0 AND line_amount>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS edu_material_payment(
 payment_id bigint PRIMARY KEY AUTO_INCREMENT, order_id bigint NOT NULL UNIQUE, out_trade_no varchar(32) NOT NULL UNIQUE,
 channel varchar(16) NOT NULL, payment_status varchar(16) NOT NULL, amount_cent int NOT NULL, paid_amount_cent int NOT NULL DEFAULT 0,
 currency char(3) NOT NULL DEFAULT 'CNY', appid varchar(32), mchid varchar(32), payer_openid varchar(128), prepay_id varchar(128),
 wx_transaction_id varchar(64) NULL UNIQUE, mock_transaction_id varchar(64) NULL UNIQUE, notify_id varchar(64) NULL UNIQUE,
 error_code varchar(64), error_message varchar(255), success_time datetime, close_time datetime, create_time datetime, update_time datetime,
 FOREIGN KEY(order_id) REFERENCES edu_material_order(order_id), CHECK(amount_cent>=0 AND paid_amount_cent>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS edu_material_shipment(
 shipment_id bigint PRIMARY KEY AUTO_INCREMENT, order_id bigint NOT NULL UNIQUE, carrier_code varchar(32) NOT NULL,
 carrier_name varchar(50) NOT NULL, tracking_no varchar(64) NOT NULL, shipment_status varchar(16) NOT NULL,
 ship_time datetime, receive_time datetime, ship_by bigint NOT NULL, logistics_sync_status varchar(16) DEFAULT 'NOT_REQUIRED',
 remark varchar(500), create_by varchar(64), create_time datetime, update_by varchar(64), update_time datetime,
 FOREIGN KEY(order_id) REFERENCES edu_material_order(order_id), FOREIGN KEY(ship_by) REFERENCES sys_user(user_id), INDEX(carrier_code,tracking_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS edu_material_order_log(
 log_id bigint PRIMARY KEY AUTO_INCREMENT, order_id bigint NOT NULL, event_type varchar(32) NOT NULL,
 from_order_status varchar(24), to_order_status varchar(24), from_pay_status char(1), to_pay_status char(1),
 operator_type varchar(16) NOT NULL, operator_id bigint, request_id varchar(64), remark varchar(500), create_time datetime NOT NULL,
 FOREIGN KEY(order_id) REFERENCES edu_material_order(order_id), FOREIGN KEY(operator_id) REFERENCES sys_user(user_id), INDEX(order_id,create_time,log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
DELIMITER $$
DROP PROCEDURE IF EXISTS shop_backfill$$
CREATE PROCEDURE shop_backfill()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF NOT EXISTS(SELECT 1 FROM edu_shop_migration WHERE version='20261004_material_shop') THEN
 -- Refuse suspicious money/channel data rather than inventing payment facts.
 IF EXISTS(SELECT 1 FROM edu_material_order WHERE amount<0 OR amount>21474836.47 OR pay_status NOT IN('0','1','2') OR pay_way NOT IN('0','1') OR (pay_way='1' AND pay_status IN('1','2') AND COALESCE(wx_transaction_id,'')='')) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconcile anomalous legacy payment records before migration';
 END IF;
 -- No historical unpaid order is closed without an operator's reviewed expiry decision.
 IF EXISTS(SELECT 1 FROM edu_material_order WHERE pay_status='0') AND (
    COALESCE(@shop_legacy_unpaid_close_approval,'')<>'REVIEWED_AND_APPROVED'
    OR COALESCE(@shop_legacy_unpaid_reviewed_count,-1)<>(SELECT COUNT(*) FROM edu_material_order WHERE pay_status='0')
    OR CHAR_LENGTH(TRIM(COALESCE(@shop_legacy_unpaid_review_note,''))) NOT BETWEEN 1 AND 200) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Review and approve historical unpaid closure with exact count and operator note';
 END IF;
 START TRANSACTION;
 UPDATE edu_material m LEFT JOIN sys_dict_data s ON s.dict_type='edu_subject' AND (s.dict_value=m.subject_name OR s.dict_label=m.subject_name)
 LEFT JOIN sys_dict_data g ON g.dict_type='edu_grade' AND (g.dict_value=m.grade_name OR g.dict_label=m.grade_name)
 SET m.subject_code=s.dict_value,m.grade_code=g.dict_value,m.delivery_type='DIGITAL',m.purchase_limit=1,m.legacy_free=(m.price=0);
 UPDATE edu_material_order SET goods_amount=amount,payable_amount=amount,refunded_amount=IF(pay_status='2',amount,0),
 order_status=IF(pay_status='0','CLOSED','COMPLETED'),close_reason=IF(pay_status='0',CONCAT('LEGACY_UNPAID_CLOSED: ',TRIM(@shop_legacy_unpaid_review_note)),NULL),
 aftersale_status=IF(pay_status='2','REFUNDED','NONE'),wx_transaction_id=NULLIF(wx_transaction_id,''),
 finish_time=IF(pay_status IN('1','2'),pay_time,NULL);
 INSERT INTO edu_material_order_item(order_id,material_id,material_code,material_title,cover_url,subject_code,grade_code,
 subject_label,grade_label,unit,delivery_type,unit_price,quantity,line_amount,asset_path,asset_name,asset_size,create_time)
 SELECT o.order_id,m.material_id,m.material_code,m.title,m.cover_url,m.subject_code,m.grade_code,m.subject_name,m.grade_name,
 m.unit,'DIGITAL',o.payable_amount,1,o.payable_amount,m.file_path,m.file_name,m.file_size,COALESCE(o.create_time,NOW())
 FROM edu_material_order o JOIN edu_material m ON m.material_id=o.material_id;
 INSERT INTO edu_material_payment(order_id,out_trade_no,channel,payment_status,amount_cent,paid_amount_cent,wx_transaction_id,
 success_time,close_time,create_time,update_time)
 SELECT order_id,CONCAT('L',order_id),IF(pay_way='0','MOCK','WECHAT'),IF(pay_status='0','CLOSED','SUCCESS'),
 ROUND(payable_amount*100),IF(pay_status='0',0,ROUND(amount*100)),IF(pay_way='1',wx_transaction_id,NULL),pay_time,
 IF(pay_status='0',NOW(),NULL),COALESCE(create_time,NOW()),NOW() FROM edu_material_order;
 UPDATE edu_material_order SET amount=0 WHERE pay_status='0';
 INSERT INTO edu_material_order_log(order_id,event_type,to_order_status,to_pay_status,operator_type,remark,create_time)
 SELECT order_id,'MIGRATION',order_status,pay_status,'SYSTEM',
 CONCAT('LEGACY_SNAPSHOT: title and file are migration-time values',IF(pay_status='0',CONCAT('; REVIEWED_UNPAID_CLOSURE: ',TRIM(@shop_legacy_unpaid_review_note)),'')),NOW() FROM edu_material_order;
 INSERT INTO edu_shop_migration VALUES('20261004_material_shop',NOW());
 COMMIT;
 END IF;
END$$
CALL shop_backfill()$$
DROP PROCEDURE shop_backfill$$
DROP PROCEDURE shop_add_column$$
DROP PROCEDURE shop_add_index$$
DELIMITER ;
ALTER TABLE edu_material_order MODIFY order_status varchar(24) NOT NULL, MODIFY goods_amount decimal(10,2) NOT NULL, MODIFY payable_amount decimal(10,2) NOT NULL;
DELIMITER $$
DROP PROCEDURE IF EXISTS shop_add_check$$
CREATE PROCEDURE shop_add_check(IN t varchar(64),IN n varchar(64),IN expression text)
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name=t AND constraint_name=n) THEN
 SET @shop_ddl=CONCAT('ALTER TABLE ',t,' ADD CONSTRAINT ',n,' CHECK (',expression,')');
 PREPARE s FROM @shop_ddl; EXECUTE s; DEALLOCATE PREPARE s;
 END IF;
END$$
DELIMITER ;
CALL shop_add_check('edu_material','ck_shop_product_money','price>=0 AND shipping_fee>=0');
CALL shop_add_check('edu_material','ck_shop_stock','stock_quantity>=0 AND stock_locked>=0 AND stock_locked<=stock_quantity AND purchase_limit>=1');
CALL shop_add_check('edu_material','ck_shop_delivery','delivery_type IN (''PHYSICAL'',''DIGITAL'')');
CALL shop_add_check('edu_material_order','ck_shop_order_money','goods_amount>=0 AND shipping_amount>=0 AND payable_amount>=0 AND amount>=0 AND refunded_amount>=0 AND refunded_amount<=amount');
CALL shop_add_check('edu_material_order','ck_shop_order_quantity','total_quantity>0');
DROP PROCEDURE shop_add_check;
