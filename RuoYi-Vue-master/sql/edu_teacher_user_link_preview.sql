-- Mingyuan MiniApp teacher user and schedule link SQL
-- Status: preview only. Do not execute until confirmed.
--
-- Purpose:
-- 1. Create teacher users in sys_user from edu_course_schedule.teacher_name.
-- 2. Bind those users to the RuoYi role whose role_key is 'teacher'.
-- 3. Add edu_course_schedule.teacher_id if it does not exist.
-- 4. Backfill edu_course_schedule.teacher_id by teacher_name.
--
-- Notes:
-- - Generated mock phones are development data. Replace user_name/phonenumber
--   with real teacher phone numbers later in RuoYi user management.
-- - Default password hash below is only for optional RuoYi web login during
--   development. MiniApp phone login does not need this password.
-- - Keep teacher_name in edu_course_schedule as a display/history snapshot.

START TRANSACTION;

SET @teacher_role_id := (
    SELECT role_id
    FROM sys_role
    WHERE role_key = 'teacher'
      AND del_flag = '0'
    LIMIT 1
);

-- Check this first after execution in a SQL client. It must not be NULL.
SELECT @teacher_role_id AS teacher_role_id;

DROP TEMPORARY TABLE IF EXISTS tmp_edu_teacher_user_map;
CREATE TEMPORARY TABLE tmp_edu_teacher_user_map (
    teacher_name varchar(30) NOT NULL PRIMARY KEY,
    mock_phone varchar(11) NOT NULL UNIQUE
) ENGINE=Memory DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_edu_teacher_user_map (teacher_name, mock_phone) VALUES
('陈老师', '13910000001'),
('杜老师', '13910000002'),
('葛老师', '13910000003'),
('管老师', '13910000004'),
('李老师', '13910000005'),
('刘老师', '13910000006'),
('陆老师', '13910000007'),
('马老师', '13910000008'),
('米老师', '13910000009'),
('孙老师', '13910000010'),
('陶老师', '13910000011'),
('王老师', '13910000012'),
('吴老师', '13910000013'),
('徐老师', '13910000014'),
('苑老师', '13910000015'),
('张老师', '13910000016');

-- Add teacher_id to the schedule table once.
SET @add_teacher_id_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE edu_course_schedule ADD COLUMN teacher_id bigint NULL COMMENT ''教师用户ID'' AFTER teacher_name',
        'SELECT ''edu_course_schedule.teacher_id already exists'' AS info'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'edu_course_schedule'
      AND COLUMN_NAME = 'teacher_id'
);
PREPARE add_teacher_id_stmt FROM @add_teacher_id_sql;
EXECUTE add_teacher_id_stmt;
DEALLOCATE PREPARE add_teacher_id_stmt;

-- Add an index for teacher-side schedule queries once.
SET @add_teacher_idx_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_edu_schedule_teacher_id ON edu_course_schedule(teacher_id)',
        'SELECT ''idx_edu_schedule_teacher_id already exists'' AS info'
    )
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'edu_course_schedule'
      AND INDEX_NAME = 'idx_edu_schedule_teacher_id'
);
PREPARE add_teacher_idx_stmt FROM @add_teacher_idx_sql;
EXECUTE add_teacher_idx_stmt;
DEALLOCATE PREPARE add_teacher_idx_stmt;

-- Insert teacher users that do not yet exist.
-- BCrypt password sample is for '123456'.
INSERT INTO sys_user (
    user_name,
    nick_name,
    user_type,
    phonenumber,
    sex,
    password,
    status,
    del_flag,
    create_by,
    create_time,
    remark
)
SELECT
    m.mock_phone,
    m.teacher_name,
    '00',
    m.mock_phone,
    '2',
    '$2a$10$7Q5sV3k7v7Nn2wP4L9D1peEzy6PAc3ubxCwJXNzNyIDGgXdBCJhdG',
    '0',
    '0',
    'admin',
    NOW(),
    CONCAT('Created from edu_course_schedule.teacher_name for MiniApp teacher login: ', m.teacher_name)
FROM tmp_edu_teacher_user_map m
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_user u
    WHERE u.user_name = m.mock_phone
      AND u.del_flag = '0'
);

-- Bind teacher role. The primary key prevents duplicate user-role rows.
INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.user_id, @teacher_role_id
FROM tmp_edu_teacher_user_map m
JOIN sys_user u
  ON u.user_name = m.mock_phone
 AND u.del_flag = '0'
WHERE @teacher_role_id IS NOT NULL;

-- Backfill schedule.teacher_id by teacher_name.
UPDATE edu_course_schedule s
JOIN tmp_edu_teacher_user_map m
  ON s.teacher_name = m.teacher_name
JOIN sys_user u
  ON u.user_name = m.mock_phone
 AND u.del_flag = '0'
SET s.teacher_id = u.user_id,
    s.update_by = 'admin',
    s.update_time = NOW()
WHERE s.del_flag = '0';

-- Preview execution result.
SELECT
    m.teacher_name,
    m.mock_phone,
    u.user_id,
    u.nick_name,
    r.role_key,
    COUNT(s.schedule_id) AS linked_schedule_count
FROM tmp_edu_teacher_user_map m
LEFT JOIN sys_user u
  ON u.user_name = m.mock_phone
 AND u.del_flag = '0'
LEFT JOIN sys_user_role ur
  ON ur.user_id = u.user_id
LEFT JOIN sys_role r
  ON r.role_id = ur.role_id
 AND r.role_key = 'teacher'
LEFT JOIN edu_course_schedule s
  ON s.teacher_id = u.user_id
 AND s.del_flag = '0'
GROUP BY m.teacher_name, m.mock_phone, u.user_id, u.nick_name, r.role_key
ORDER BY m.mock_phone;

COMMIT;
