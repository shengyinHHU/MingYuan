-- Mingyuan MiniApp attendance/lesson completion table
-- Business model: parents track how many enrolled schedule sessions have been completed.
-- Status: generated only. Do not execute until confirmed.

DROP TABLE IF EXISTS `edu_attendance`;
CREATE TABLE `edu_attendance` (
  `attendance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '上课记录ID',
  `attendance_code` varchar(32) NOT NULL COMMENT '上课记录编码',
  `schedule_id` bigint NOT NULL COMMENT '排课ID',
  `enrollment_id` bigint NOT NULL COMMENT '报名ID',
  `parent_id` bigint NOT NULL COMMENT '家长用户ID',
  `student_name` varchar(30) NOT NULL COMMENT '学生姓名快照',
  `attendance_status` char(1) DEFAULT '0' COMMENT '上课状态（0未上课 1已上课 2已取消）',
  `attended_time` datetime DEFAULT NULL COMMENT '确认上课时间',
  `confirm_by` bigint DEFAULT NULL COMMENT '确认人用户ID',
  `confirm_name` varchar(30) DEFAULT '' COMMENT '确认人姓名',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`attendance_id`),
  UNIQUE KEY `uk_edu_attendance_code` (`attendance_code`),
  UNIQUE KEY `uk_edu_attendance_enrollment` (`enrollment_id`),
  KEY `idx_edu_attendance_schedule` (`schedule_id`),
  KEY `idx_edu_attendance_parent` (`parent_id`),
  KEY `idx_edu_attendance_status` (`attendance_status`),
  CONSTRAINT `fk_edu_attendance_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `edu_course_schedule` (`schedule_id`),
  CONSTRAINT `fk_edu_attendance_enrollment` FOREIGN KEY (`enrollment_id`) REFERENCES `edu_enrollment` (`enrollment_id`),
  CONSTRAINT `fk_edu_attendance_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程上课记录表';
