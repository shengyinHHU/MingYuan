-- Initialize edu_attendance from existing successful enrollments.
-- This script is idempotent and can be executed repeatedly.

INSERT INTO edu_attendance (
  attendance_code,
  schedule_id,
  enrollment_id,
  parent_id,
  student_name,
  attendance_status,
  status,
  del_flag,
  create_by,
  create_time,
  remark
)
SELECT
  CONCAT('ATT', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), LPAD(e.enrollment_id, 6, '0')) AS attendance_code,
  e.schedule_id,
  e.enrollment_id,
  e.parent_id,
  e.student_name,
  '0' AS attendance_status,
  '0' AS status,
  '0' AS del_flag,
  'init' AS create_by,
  NOW() AS create_time,
  'Initialized from edu_enrollment'
FROM edu_enrollment e
WHERE e.del_flag = '0'
  AND e.enrollment_status = '1'
  AND NOT EXISTS (
    SELECT 1
    FROM edu_attendance a
    WHERE a.enrollment_id = e.enrollment_id
      AND a.del_flag = '0'
  );
