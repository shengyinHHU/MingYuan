-- 20261010 one-to-one lessons. MySQL 8.0.16+, additive and rerunnable.
-- Stop services and back up before deployment. Apply 20261007 tuition finance first;
-- do not replay the old migration after this supersedes its enrollment unique index.
-- No historical student merging, balance grants, amount rewrites, users or roles.
SET NAMES utf8mb4;
DELIMITER $$
DROP FUNCTION IF EXISTS lesson_normalize$$
DROP PROCEDURE IF EXISTS lesson_column$$
CREATE PROCEDURE lesson_column(IN t varchar(64),IN c varchar(64),IN typ varchar(100),IN spec text,IN nullable varchar(3),IN def text,IN expression text)
BEGIN
 DECLARE actual varchar(100); DECLARE actualnull varchar(3); DECLARE actualdef text; DECLARE actualexpr text; DECLARE ext text; DECLARE msg varchar(128); DECLARE canonical text; DECLARE probe_created boolean DEFAULT FALSE;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN IF probe_created THEN DROP TABLE __lesson_verify_probe; END IF; RESIGNAL; END;
 SELECT column_type,is_nullable,column_default,generation_expression,extra INTO actual,actualnull,actualdef,actualexpr,ext FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c;
 IF actual IS NULL THEN
  SET @ddl=CONCAT('ALTER TABLE `',t,'` ADD COLUMN `',c,'` ',typ,' ',spec); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
 ELSE
 IF expression IS NOT NULL THEN
  SET @ddl=CONCAT('CREATE TABLE __lesson_verify_probe LIKE `',t,'`'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt; SET probe_created=TRUE;
  SET @ddl=CONCAT('ALTER TABLE __lesson_verify_probe MODIFY `',c,'` ',typ,' ',spec); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  SELECT generation_expression INTO canonical FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='__lesson_verify_probe' AND column_name=c;
  DROP TABLE __lesson_verify_probe; SET probe_created=FALSE;
 END IF;
 -- Both expressions are canonicalized by MySQL; binary equality preserves quoted literals.
 IF LOWER(actual)<>LOWER(typ) OR actualnull<>nullable OR NOT(BINARY actualdef<=>BINARY def) OR (expression IS NULL AND COALESCE(actualexpr,'')<>'') OR (expression IS NOT NULL AND (ext NOT LIKE '%STORED GENERATED%' OR NOT(BINARY actualexpr<=>BINARY canonical))) OR (spec LIKE '%AUTO_INCREMENT%' AND ext NOT LIKE '%auto_increment%') THEN
  SET msg=CONCAT('Lesson column/generated conflict: ',t,'.',c); SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg;
 END IF;
 END IF;
END$$
DROP PROCEDURE IF EXISTS lesson_index$$
CREATE PROCEDURE lesson_index(IN t varchar(64),IN n varchar(64),IN cols text,IN uniq boolean)
BEGIN
 DECLARE actual text; DECLARE nonuniq int; DECLARE msg varchar(128);
 IF NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=t AND engine='InnoDB' AND table_collation LIKE 'utf8mb4%') THEN SET msg=CONCAT('Lesson table engine/charset conflict: ',t); SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg; END IF;
 SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index),MAX(non_unique) INTO actual,nonuniq FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=t AND index_name=n;
 IF actual IS NULL THEN
  SET @ddl=CONCAT('ALTER TABLE `',t,'` ADD ',IF(n='PRIMARY','PRIMARY KEY',CONCAT(IF(uniq,'UNIQUE KEY ','KEY '),'`',n,'`')),' (',cols,')'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
 ELSEIF actual<>cols OR nonuniq<>IF(uniq,0,1) OR EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=t AND index_name=n AND (sub_part IS NOT NULL OR is_visible<>'YES' OR index_type<>'BTREE')) THEN
  SET msg=CONCAT('Lesson index conflict: ',t,'.',n); SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg;
 END IF;
END$$
DROP PROCEDURE IF EXISTS lesson_fk$$
CREATE PROCEDURE lesson_fk(IN t varchar(64),IN n varchar(64),IN cols text,IN target varchar(64),IN targetcols text)
BEGIN
 DECLARE actual text; DECLARE refs text; DECLARE targets text; DECLARE rules text; DECLARE msg varchar(128);
 SELECT GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position),GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position),GROUP_CONCAT(DISTINCT k.referenced_table_name),GROUP_CONCAT(DISTINCT CONCAT(r.update_rule,':',r.delete_rule))
 INTO actual,refs,targets,rules FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name WHERE k.constraint_schema=DATABASE() AND k.table_name=t AND k.constraint_name=n;
 IF actual IS NULL THEN
  IF EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name=t AND constraint_name=n) THEN SET msg=CONCAT('Lesson foreign key kind conflict: ',t,'.',n); SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg; END IF;
  SET @ddl=CONCAT('ALTER TABLE `',t,'` ADD CONSTRAINT `',n,'` FOREIGN KEY (',cols,') REFERENCES `',target,'` (',targetcols,')'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
 ELSEIF actual<>cols OR refs<>targetcols OR targets<>target OR rules NOT IN ('NO ACTION:NO ACTION','RESTRICT:RESTRICT') OR EXISTS(SELECT 1 FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND table_name=t AND constraint_name=n AND referenced_table_schema<>DATABASE()) THEN
  SET msg=CONCAT('Lesson foreign key conflict: ',t,'.',n); SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg;
 END IF;
END$$
DROP PROCEDURE IF EXISTS lesson_check$$
CREATE PROCEDURE lesson_check(IN t varchar(64),IN n varchar(64),IN expression text)
BEGIN
 DECLARE actual text; DECLARE msg varchar(128); DECLARE canonical text; DECLARE probe_created boolean DEFAULT FALSE;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN IF probe_created THEN DROP TABLE __lesson_verify_probe; END IF; RESIGNAL; END;
 SELECT cc.check_clause INTO actual FROM information_schema.check_constraints cc JOIN information_schema.table_constraints tc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name WHERE tc.constraint_schema=DATABASE() AND tc.table_name=t AND tc.constraint_name=n AND tc.enforced='YES';
 IF actual IS NULL AND NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name=t AND constraint_name=n) THEN
  SET @ddl=CONCAT('ALTER TABLE `',t,'` ADD CONSTRAINT `',n,'` CHECK (',expression,')'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
 ELSE
  IF actual IS NOT NULL THEN
   SET @ddl=CONCAT('CREATE TABLE __lesson_verify_probe LIKE `',t,'`'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt; SET probe_created=TRUE;
   SET @ddl=CONCAT('ALTER TABLE __lesson_verify_probe ADD CONSTRAINT ck_lesson_verify_probe CHECK (',expression,')'); PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
   SELECT check_clause INTO canonical FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() AND constraint_name='ck_lesson_verify_probe';
   DROP TABLE __lesson_verify_probe; SET probe_created=FALSE;
  END IF;
  IF actual IS NULL OR NOT(BINARY actual<=>BINARY canonical) THEN SET msg=CONCAT('Lesson check conflict: ',t,'.',n); SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=msg; END IF;
 END IF;
END$$
DROP PROCEDURE IF EXISTS lesson_preflight$$
CREATE PROCEDURE lesson_preflight()
BEGIN
 IF EXISTS(SELECT teacher_id FROM edu_teacher_salary_config WHERE status='0' AND del_flag='0' GROUP BY teacher_id HAVING COUNT(*)>1) THEN
  SELECT teacher_id,COUNT(*) AS effective_configs FROM edu_teacher_salary_config WHERE status='0' AND del_flag='0' GROUP BY teacher_id HAVING COUNT(*)>1;
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson duplicate effective teacher configuration; resolve explicitly';
 END IF;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_enrollment' AND column_name='schedule_id' AND (column_type<>'bigint' OR column_default IS NOT NULL OR generation_expression<>'')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson column conflict: edu_enrollment.schedule_id'; END IF;
 IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='__lesson_verify_probe') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson schema probe already exists; inspect interrupted migration'; END IF;
END$$
DELIMITER ;
CALL lesson_preflight();
DROP PROCEDURE lesson_preflight;

CREATE TABLE IF NOT EXISTS edu_student (student_id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CALL lesson_column('edu_student','student_id','bigint','NOT NULL AUTO_INCREMENT','NO',NULL,NULL);
CALL lesson_index('edu_student','PRIMARY','student_id',TRUE);
CALL lesson_column('edu_student','parent_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student','student_name','varchar(30)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student','student_phone','varchar(11)','NULL','YES',NULL,NULL);
CALL lesson_column('edu_student','grade_code','varchar(30)','NULL','YES',NULL,NULL);
CALL lesson_column('edu_student','status','char(1)','NOT NULL DEFAULT ''0''','NO','0',NULL);
CALL lesson_column('edu_student','del_flag','char(1)','NOT NULL DEFAULT ''0''','NO','0',NULL);
CALL lesson_column('edu_student','create_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_student','create_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_student','update_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_student','update_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_student','remark','varchar(500)','NULL','YES',NULL,NULL);

CREATE TABLE IF NOT EXISTS edu_lesson_package (package_id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CALL lesson_column('edu_lesson_package','package_id','bigint','NOT NULL AUTO_INCREMENT','NO',NULL,NULL);
CALL lesson_index('edu_lesson_package','PRIMARY','package_id',TRUE);
CALL lesson_column('edu_lesson_package','package_name','varchar(100)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_package','subject_code','varchar(30)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_package','subject_name','varchar(50)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_package','total_units','int','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_package','price','decimal(10,2)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_package','status','char(1)','NOT NULL DEFAULT ''0''','NO','0',NULL);
CALL lesson_column('edu_lesson_package','del_flag','char(1)','NOT NULL DEFAULT ''0''','NO','0',NULL);
CALL lesson_column('edu_lesson_package','create_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_lesson_package','create_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_lesson_package','update_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_lesson_package','update_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_lesson_package','remark','varchar(500)','NULL','YES',NULL,NULL);

CREATE TABLE IF NOT EXISTS edu_student_package (student_package_id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CALL lesson_column('edu_student_package','student_package_id','bigint','NOT NULL AUTO_INCREMENT','NO',NULL,NULL);
CALL lesson_index('edu_student_package','PRIMARY','student_package_id',TRUE);
CALL lesson_column('edu_student_package','student_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','parent_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','package_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','enrollment_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','package_name_snapshot','varchar(100)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','subject_code','varchar(30)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','subject_name_snapshot','varchar(50)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','total_units','int','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','available_units','int','NOT NULL DEFAULT 0','NO','0',NULL);
CALL lesson_column('edu_student_package','purchased_price','decimal(10,2)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','status','varchar(20)','NOT NULL DEFAULT ''PENDING_PAYMENT''','NO','PENDING_PAYMENT',NULL);
CALL lesson_column('edu_student_package','activated_time','datetime(3)','NULL','YES',NULL,NULL);
CALL lesson_column('edu_student_package','closed_time','datetime(3)','NULL','YES',NULL,NULL);
CALL lesson_column('edu_student_package','request_key','varchar(64)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','request_hash','char(64)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_student_package','create_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_student_package','create_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_student_package','update_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_student_package','update_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_student_package','remark','varchar(500)','NULL','YES',NULL,NULL);

CREATE TABLE IF NOT EXISTS edu_one_to_one_slot (slot_id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CALL lesson_column('edu_one_to_one_slot','slot_id','bigint','NOT NULL AUTO_INCREMENT','NO',NULL,NULL);
CALL lesson_index('edu_one_to_one_slot','PRIMARY','slot_id',TRUE);
CALL lesson_column('edu_one_to_one_slot','teacher_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_slot','subject_code','varchar(30)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_slot','subject_name_snapshot','varchar(50)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_slot','classroom_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_slot','start_time','datetime(3)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_slot','end_time','datetime(3)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_slot','status','varchar(6)','NOT NULL DEFAULT ''OPEN''','NO','OPEN',NULL);
CALL lesson_column('edu_one_to_one_slot','version','int','NOT NULL DEFAULT 0','NO','0',NULL);
CALL lesson_column('edu_one_to_one_slot','create_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_one_to_one_slot','create_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_one_to_one_slot','update_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_one_to_one_slot','update_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_one_to_one_slot','remark','varchar(500)','NULL','YES',NULL,NULL);

CREATE TABLE IF NOT EXISTS edu_one_to_one_booking (booking_id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CALL lesson_column('edu_one_to_one_booking','booking_id','bigint','NOT NULL AUTO_INCREMENT','NO',NULL,NULL);
CALL lesson_index('edu_one_to_one_booking','PRIMARY','booking_id',TRUE);
CALL lesson_column('edu_one_to_one_booking','booking_code','varchar(40)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','slot_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','student_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','parent_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','student_package_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','teacher_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','teacher_level_snapshot','varchar(6)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','subject_code','varchar(30)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','classroom_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','start_time_snapshot','datetime(3)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','end_time_snapshot','datetime(3)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','deducted_units','int','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','returned_units','int','NOT NULL DEFAULT 0','NO','0',NULL);
CALL lesson_column('edu_one_to_one_booking','status','varchar(9)','NOT NULL DEFAULT ''BOOKED''','NO','BOOKED',NULL);
CALL lesson_column('edu_one_to_one_booking','cancelled_time','datetime(3)','NULL','YES',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','cancelled_by','bigint','NULL','YES',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','cancel_reason','varchar(500)','NULL','YES',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','request_key','varchar(64)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','request_hash','char(64)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_one_to_one_booking','active_slot_guard','tinyint','GENERATED ALWAYS AS (CASE WHEN status IN (''BOOKED'',''COMPLETED'') THEN 1 END) STORED','YES',NULL,'CASE WHEN status IN (''BOOKED'',''COMPLETED'') THEN 1 END');
CALL lesson_column('edu_one_to_one_booking','create_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_one_to_one_booking','create_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_one_to_one_booking','update_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_one_to_one_booking','update_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_one_to_one_booking','remark','varchar(500)','NULL','YES',NULL,NULL);

CREATE TABLE IF NOT EXISTS edu_lesson_unit_log (log_id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CALL lesson_column('edu_lesson_unit_log','log_id','bigint','NOT NULL AUTO_INCREMENT','NO',NULL,NULL);
CALL lesson_index('edu_lesson_unit_log','PRIMARY','log_id',TRUE);
CALL lesson_column('edu_lesson_unit_log','student_package_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','booking_id','bigint','NULL','YES',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','refund_id','bigint','NULL','YES',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','event_type','varchar(16)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','delta_units','int','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','before_units','int','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','after_units','int','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','event_key','varchar(100)','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','actor_id','bigint','NOT NULL','NO',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','reason','varchar(500)','NULL','YES',NULL,NULL);
CALL lesson_column('edu_lesson_unit_log','create_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_lesson_unit_log','create_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_lesson_unit_log','update_by','varchar(64)','NOT NULL DEFAULT ''SYSTEM''','NO','SYSTEM',NULL);
CALL lesson_column('edu_lesson_unit_log','update_time','datetime(3)','NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','NO','CURRENT_TIMESTAMP(3)',NULL);
CALL lesson_column('edu_lesson_unit_log','remark','varchar(500)','NULL','YES',NULL,NULL);

-- Existing COURSE rows keep their identity and all financial snapshots.
CALL lesson_column('edu_enrollment','student_id','bigint','NULL','YES',NULL,NULL);
CALL lesson_column('edu_enrollment','bill_type','varchar(14)','NOT NULL DEFAULT ''COURSE''','NO','COURSE',NULL);
CALL lesson_column('edu_enrollment','lesson_package_id','bigint','NULL','YES',NULL,NULL);
ALTER TABLE edu_enrollment MODIFY schedule_id bigint NULL COMMENT '排课ID；课包账单为NULL';
CALL lesson_column('edu_teacher_salary_config','one_to_one_level','varchar(6)','NOT NULL DEFAULT ''NORMAL''','NO','NORMAL',NULL);
CALL lesson_column('edu_teacher_salary_config','active_teacher_guard','bigint','GENERATED ALWAYS AS (CASE WHEN status=''0'' AND del_flag=''0'' THEN teacher_id END) STORED','YES',NULL,'CASE WHEN status=''0'' AND del_flag=''0'' THEN teacher_id END');
CALL lesson_index('edu_teacher_salary_config','uk_lteacher_active','active_teacher_guard',TRUE);
-- Stable child identity: distinct same-name siblings are distinct; legacy rows remain name-based.
CALL lesson_column('edu_enrollment','student_identity_guard','varchar(64)','GENERATED ALWAYS AS (CASE WHEN student_id IS NULL THEN CONCAT(''LEGACY:'',student_name) ELSE CONCAT(''STUDENT:'',student_id) END) STORED','YES',NULL,'CASE WHEN student_id IS NULL THEN CONCAT(''LEGACY:'',student_name) ELSE CONCAT(''STUDENT:'',student_id) END');
DELIMITER $$
DROP PROCEDURE IF EXISTS lesson_enrollment_index$$
CREATE PROCEDURE lesson_enrollment_index()
BEGIN
 DECLARE actual text;
 SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO actual FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='edu_enrollment' AND index_name='uk_edu_enrollment_once';
 IF actual='schedule_id,parent_id,student_name,active_enrollment_guard' THEN ALTER TABLE edu_enrollment DROP INDEX uk_edu_enrollment_once;
 ELSEIF actual IS NOT NULL AND actual<>'schedule_id,parent_id,student_identity_guard,active_enrollment_guard' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson index conflict: edu_enrollment.uk_edu_enrollment_once';
 END IF;
END$$
DELIMITER ;
CALL lesson_enrollment_index();
DROP PROCEDURE lesson_enrollment_index;
CALL lesson_index('edu_student','uk_lstudent_owner','student_id,parent_id',TRUE);
CALL lesson_index('edu_student','idx_lstudent_parent','parent_id,del_flag,status,student_id',FALSE);
CALL lesson_index('edu_lesson_package','idx_lpackage_subject','subject_code,status,del_flag,package_id',FALSE);
CALL lesson_index('edu_enrollment','uk_edu_enrollment_once','schedule_id,parent_id,student_identity_guard,active_enrollment_guard',TRUE);
CALL lesson_index('edu_enrollment','uk_lenrollment_owner','enrollment_id,student_id,parent_id,lesson_package_id',TRUE);
CALL lesson_index('edu_enrollment','idx_lenrollment_student','student_id,bill_type,enrollment_id',FALSE);
CALL lesson_index('edu_student_package','uk_lsp_enrollment','enrollment_id',TRUE);
CALL lesson_index('edu_student_package','uk_lsp_request','parent_id,request_key',TRUE);
CALL lesson_index('edu_student_package','uk_lsp_owner','student_package_id,student_id,parent_id',TRUE);
CALL lesson_index('edu_student_package','idx_lsp_student','student_id,status,subject_code,student_package_id',FALSE);
CALL lesson_index('edu_student_package','idx_lsp_parent','parent_id,status,student_package_id',FALSE);
CALL lesson_index('edu_one_to_one_slot','idx_lslot_teacher','teacher_id,status,start_time,slot_id',FALSE);
CALL lesson_index('edu_one_to_one_slot','idx_lslot_subject','subject_code,status,start_time,slot_id',FALSE);
CALL lesson_index('edu_one_to_one_slot','idx_lslot_classroom','classroom_id,start_time,end_time',FALSE);
CALL lesson_index('edu_one_to_one_booking','uk_lbooking_code','booking_code',TRUE);
CALL lesson_index('edu_one_to_one_booking','uk_lbooking_request','parent_id,request_key',TRUE);
CALL lesson_index('edu_one_to_one_booking','uk_lbooking_active','slot_id,active_slot_guard',TRUE);
CALL lesson_index('edu_one_to_one_booking','uk_lbooking_package','booking_id,student_package_id',TRUE);
CALL lesson_index('edu_one_to_one_booking','idx_lbooking_student','student_id,status,start_time_snapshot,booking_id',FALSE);
CALL lesson_index('edu_one_to_one_booking','idx_lbooking_parent','parent_id,status,start_time_snapshot,booking_id',FALSE);
CALL lesson_index('edu_one_to_one_booking','idx_lbooking_teacher','teacher_id,status,start_time_snapshot,booking_id',FALSE);
CALL lesson_index('edu_lesson_unit_log','uk_llog_event','event_key',TRUE);
CALL lesson_index('edu_lesson_unit_log','idx_llog_package','student_package_id,create_time,log_id',FALSE);
CALL lesson_fk('edu_student','fk_lstudent_parent','parent_id','sys_user','user_id');
CALL lesson_fk('edu_enrollment','fk_lenrollment_student','student_id,parent_id','edu_student','student_id,parent_id');
CALL lesson_fk('edu_enrollment','fk_lenrollment_package','lesson_package_id','edu_lesson_package','package_id');
CALL lesson_fk('edu_student_package','fk_lsp_student','student_id,parent_id','edu_student','student_id,parent_id');
CALL lesson_fk('edu_student_package','fk_lsp_parent','parent_id','sys_user','user_id');
CALL lesson_fk('edu_student_package','fk_lsp_package','package_id','edu_lesson_package','package_id');
CALL lesson_fk('edu_student_package','fk_lsp_enrollment','enrollment_id,student_id,parent_id,package_id','edu_enrollment','enrollment_id,student_id,parent_id,lesson_package_id');
CALL lesson_fk('edu_one_to_one_slot','fk_lslot_teacher','teacher_id','sys_user','user_id');
CALL lesson_fk('edu_one_to_one_slot','fk_lslot_classroom','classroom_id','edu_classroom','classroom_id');
CALL lesson_fk('edu_one_to_one_booking','fk_lbooking_slot','slot_id','edu_one_to_one_slot','slot_id');
CALL lesson_fk('edu_one_to_one_booking','fk_lbooking_student','student_id,parent_id','edu_student','student_id,parent_id');
CALL lesson_fk('edu_one_to_one_booking','fk_lbooking_package','student_package_id,student_id,parent_id','edu_student_package','student_package_id,student_id,parent_id');
CALL lesson_fk('edu_one_to_one_booking','fk_lbooking_parent','parent_id','sys_user','user_id');
CALL lesson_fk('edu_one_to_one_booking','fk_lbooking_teacher','teacher_id','sys_user','user_id');
CALL lesson_fk('edu_one_to_one_booking','fk_lbooking_classroom','classroom_id','edu_classroom','classroom_id');
CALL lesson_fk('edu_one_to_one_booking','fk_lbooking_cancel','cancelled_by','sys_user','user_id');
CALL lesson_fk('edu_lesson_unit_log','fk_llog_package','student_package_id','edu_student_package','student_package_id');
CALL lesson_fk('edu_lesson_unit_log','fk_llog_booking','booking_id,student_package_id','edu_one_to_one_booking','booking_id,student_package_id');
CALL lesson_fk('edu_lesson_unit_log','fk_llog_refund','refund_id','edu_tuition_refund','refund_id');
CALL lesson_fk('edu_lesson_unit_log','fk_llog_actor','actor_id','sys_user','user_id');
CALL lesson_check('edu_student','ck_lstudent_state','status IN (''0'',''1'') AND del_flag IN (''0'',''2'')');
CALL lesson_check('edu_lesson_package','ck_lpackage_rules','total_units>0 AND price>=0 AND status IN (''0'',''1'') AND del_flag IN (''0'',''2'')');
CALL lesson_check('edu_student_package','ck_lsp_rules','total_units>0 AND available_units>=0 AND available_units<=total_units AND purchased_price>=0 AND status IN (''PENDING_PAYMENT'',''ACTIVE'',''REFUND_FROZEN'',''CLOSED'') AND (status NOT IN (''PENDING_PAYMENT'',''CLOSED'') OR available_units=0)');
CALL lesson_check('edu_one_to_one_slot','ck_lslot_rules','end_time=start_time+INTERVAL 2 HOUR AND status IN (''OPEN'',''CLOSED'') AND version>=0');
CALL lesson_check('edu_one_to_one_booking','ck_lbooking_rules','status IN (''BOOKED'',''COMPLETED'',''CANCELLED'') AND teacher_level_snapshot IN (''NORMAL'',''LEAD'') AND deducted_units IN (1,2) AND returned_units>=0 AND returned_units<=deducted_units AND ((teacher_level_snapshot=''NORMAL'' AND deducted_units=1) OR (teacher_level_snapshot=''LEAD'' AND deducted_units=2)) AND end_time_snapshot=start_time_snapshot+INTERVAL 2 HOUR');
CALL lesson_check('edu_lesson_unit_log','ck_llog_rules','before_units>=0 AND after_units>=0 AND after_units=before_units+delta_units AND event_type IN (''ACTIVATE'',''BOOK'',''CANCEL_RETURN'',''ADMIN_RETURN'',''REFUND_CLOSE'') AND ((event_type=''ACTIVATE'' AND delta_units>0 AND booking_id IS NULL AND refund_id IS NULL) OR (event_type=''BOOK'' AND delta_units IN (-1,-2) AND booking_id IS NOT NULL AND refund_id IS NULL) OR (event_type IN (''CANCEL_RETURN'',''ADMIN_RETURN'') AND delta_units IN (1,2) AND booking_id IS NOT NULL AND refund_id IS NULL) OR (event_type=''REFUND_CLOSE'' AND delta_units<=0 AND after_units=0 AND refund_id IS NOT NULL AND booking_id IS NULL))');
CALL lesson_check('edu_enrollment','ck_lenrollment_type','(bill_type=''COURSE'' AND schedule_id IS NOT NULL AND lesson_package_id IS NULL) OR (bill_type=''LESSON_PACKAGE'' AND schedule_id IS NULL AND student_id IS NOT NULL AND lesson_package_id IS NOT NULL AND user_coupon_id IS NULL)');
CALL lesson_check('edu_teacher_salary_config','ck_lteacher_level','one_to_one_level IN (''NORMAL'',''LEAD'')');

DELIMITER $$
DROP TRIGGER IF EXISTS lesson_bill_identity$$
CREATE TRIGGER lesson_bill_identity BEFORE UPDATE ON edu_enrollment FOR EACH ROW
BEGIN
 IF NOT(OLD.bill_type<=>NEW.bill_type) OR NOT(OLD.lesson_package_id<=>NEW.lesson_package_id) OR (OLD.student_id IS NOT NULL AND NOT(OLD.student_id<=>NEW.student_id)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson bill type, package and linked student are immutable'; END IF;
END$$
DROP TRIGGER IF EXISTS lesson_unit_log_immutable$$
CREATE TRIGGER lesson_unit_log_immutable BEFORE UPDATE ON edu_lesson_unit_log FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson unit events are append-only'; END$$
DROP TRIGGER IF EXISTS lesson_unit_log_preserve$$
CREATE TRIGGER lesson_unit_log_preserve BEFORE DELETE ON edu_lesson_unit_log FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson unit events cannot be deleted'; END$$
DROP TRIGGER IF EXISTS lesson_booking_preserve$$
CREATE TRIGGER lesson_booking_preserve BEFORE DELETE ON edu_one_to_one_booking FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lesson booking history cannot be deleted'; END$$
DELIMITER ;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,route_name,menu_type,perms,icon,create_by,create_time) SELECT '一对一约课',0,9,'oneToOne','system/oneToOne/index','OneToOne','C','system:oneToOne:list','date','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:list');
SET @lesson_menu=(SELECT menu_id FROM sys_menu WHERE perms='system:oneToOne:list' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-查询',@lesson_menu,'#','F','system:oneToOne:query','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:query');
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-学员',@lesson_menu,'#','F','system:oneToOne:student','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:student');
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-课包',@lesson_menu,'#','F','system:oneToOne:package','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:package');
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-时段',@lesson_menu,'#','F','system:oneToOne:slot','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:slot');
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-预约',@lesson_menu,'#','F','system:oneToOne:book','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:book');
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-取消',@lesson_menu,'#','F','system:oneToOne:cancel','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:cancel');
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-退次',@lesson_menu,'#','F','system:oneToOne:return','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:return');
INSERT INTO sys_menu(menu_name,parent_id,path,menu_type,perms,create_by,create_time) SELECT '一对一-教师等级',@lesson_menu,'#','F','system:oneToOne:teacherLevel','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='system:oneToOne:teacherLevel');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms LIKE 'system:oneToOne:%' WHERE r.role_key='admin' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
DROP PROCEDURE lesson_column;
DROP PROCEDURE lesson_index;
DROP PROCEDURE lesson_fk;
DROP PROCEDURE lesson_check;
