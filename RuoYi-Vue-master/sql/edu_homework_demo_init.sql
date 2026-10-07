-- 作业闭环演示数据：可重复执行，不删除或覆盖现有业务数据。
-- 默认密码为 123456（仅开发环境演示）。
START TRANSACTION;

SET @teacher_role_id := (SELECT role_id FROM sys_role WHERE role_key = 'teacher' AND del_flag = '0' LIMIT 1);
SET @parent_role_id := (SELECT role_id FROM sys_role WHERE role_key = 'parent' AND del_flag = '0' LIMIT 1);

SET @add_teacher_id_sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE edu_course_schedule ADD COLUMN teacher_id bigint NULL COMMENT ''教师用户ID'' AFTER teacher_name',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'edu_course_schedule' AND COLUMN_NAME = 'teacher_id');
PREPARE add_teacher_id_stmt FROM @add_teacher_id_sql;
EXECUTE add_teacher_id_stmt;
DEALLOCATE PREPARE add_teacher_id_stmt;

INSERT INTO sys_user (user_name,nick_name,user_type,phonenumber,sex,password,status,del_flag,create_by,create_time,remark)
SELECT '13919990001','少白','00','13919990001','2','$2a$10$7Q5sV3k7v7Nn2wP4L9D1peEzy6PAc3ubxCwJXNzNyIDGgXdBCJhdG','0','0','admin',NOW(),'作业闭环演示教师'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE user_name='13919990001' AND del_flag='0');
SET @teacher_id := (SELECT user_id FROM sys_user WHERE user_name='13919990001' AND del_flag='0' LIMIT 1);
INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES (@teacher_id,@teacher_role_id);

INSERT INTO sys_user (user_name,nick_name,user_type,phonenumber,sex,password,status,del_flag,create_by,create_time,remark)
SELECT CONCAT('1391888',LPAD(n,4,'0')),CONCAT('少白家长',n),'00',CONCAT('1391888',LPAD(n,4,'0')),'2','$2a$10$7Q5sV3k7v7Nn2wP4L9D1peEzy6PAc3ubxCwJXNzNyIDGgXdBCJhdG','0','0','admin',NOW(),'少白家长账号'
FROM (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10) x
WHERE NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.user_name=CONCAT('1391888',LPAD(x.n,4,'0')) AND u.del_flag='0');
INSERT IGNORE INTO sys_user_role(user_id,role_id)
SELECT u.user_id,@parent_role_id FROM sys_user u WHERE u.user_name LIKE '1391888%' AND u.del_flag='0';

INSERT INTO edu_course_schedule (schedule_code,classroom_id,course_year,term_name,period_name,time_slot,start_time,end_time,grade_name,subject_name,teacher_name,class_type,enrolled_count,recruit_status,course_class_name,status,del_flag,create_by,create_time)
SELECT 'DEMO-SH01',(SELECT classroom_id FROM edu_classroom WHERE status='0' ORDER BY classroom_id LIMIT 1),2026,'演示学期','一班','09:00-11:00','09:00:00','11:00:00','初一','数学','少白','演示班',5,'0','少白1班','0','0','admin',NOW()
FROM edu_classroom WHERE status='0'
  AND NOT EXISTS (SELECT 1 FROM edu_course_schedule WHERE schedule_code='DEMO-SH01');
INSERT INTO edu_course_schedule (schedule_code,classroom_id,course_year,term_name,period_name,time_slot,start_time,end_time,grade_name,subject_name,teacher_name,class_type,enrolled_count,recruit_status,course_class_name,status,del_flag,create_by,create_time)
SELECT 'DEMO-SH02',(SELECT classroom_id FROM edu_classroom WHERE status='0' ORDER BY classroom_id LIMIT 1 OFFSET 1),2026,'演示学期','二班','14:00-16:00','14:00:00','16:00:00','初一','数学','少白','演示班',5,'0','少白2班','0','0','admin',NOW()
FROM edu_classroom WHERE status='0'
  AND NOT EXISTS (SELECT 1 FROM edu_course_schedule WHERE schedule_code='DEMO-SH02');
UPDATE edu_course_schedule SET teacher_id=@teacher_id WHERE schedule_code IN ('DEMO-SH01','DEMO-SH02') AND (teacher_id IS NULL OR teacher_id<>@teacher_id);

INSERT INTO edu_enrollment (enrollment_code,schedule_id,parent_id,student_name,student_phone,contact_phone,enrollment_status,pay_status,status,del_flag,create_by,create_time)
SELECT CONCAT('DEMO-E',LPAD(n,2,'0')),s.schedule_id,u.user_id,CONCAT('少白',LPAD(n,3,'0')),'','', '1','1','0','0','admin',NOW()
FROM (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) x
JOIN edu_course_schedule s ON s.schedule_code='DEMO-SH01'
JOIN sys_user u ON u.user_name=CONCAT('1391888',LPAD(x.n,4,'0'))
WHERE NOT EXISTS (SELECT 1 FROM edu_enrollment e WHERE e.enrollment_code=CONCAT('DEMO-E',LPAD(x.n,2,'0')));
INSERT INTO edu_enrollment (enrollment_code,schedule_id,parent_id,student_name,student_phone,contact_phone,enrollment_status,pay_status,status,del_flag,create_by,create_time)
SELECT CONCAT('DEMO-E',LPAD(n+5,2,'0')),s.schedule_id,u.user_id,CONCAT('少白',LPAD(n+5,3,'0')),'','', '1','1','0','0','admin',NOW()
FROM (SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) x
JOIN edu_course_schedule s ON s.schedule_code='DEMO-SH02'
JOIN sys_user u ON u.user_name=CONCAT('1391888',LPAD(x.n+5,4,'0'))
WHERE NOT EXISTS (SELECT 1 FROM edu_enrollment e WHERE e.enrollment_code=CONCAT('DEMO-E',LPAD(x.n+5,2,'0')));
COMMIT;
