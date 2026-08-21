-- Mingyuan MiniApp enrollment table
-- Business model: parents enroll a student in one edu_course_schedule record.

DROP TABLE IF EXISTS `edu_enrollment`;
CREATE TABLE `edu_enrollment` (
  `enrollment_id` bigint NOT NULL AUTO_INCREMENT COMMENT '报名ID',
  `enrollment_code` varchar(32) NOT NULL COMMENT '报名编码',
  `schedule_id` bigint NOT NULL COMMENT '排课ID',
  `parent_id` bigint NOT NULL COMMENT '家长用户ID',
  `student_name` varchar(30) NOT NULL COMMENT '学生姓名',
  `student_phone` varchar(11) DEFAULT '' COMMENT '学生手机号',
  `contact_phone` varchar(11) DEFAULT '' COMMENT '联系电话',
  `enrollment_status` char(1) DEFAULT '1' COMMENT '报名状态（0待确认 1报名成功 2已取消）',
  `pay_status` char(1) DEFAULT '0' COMMENT '支付状态（0未支付 1已支付 2已退款）',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `cancel_reason` varchar(255) DEFAULT '' COMMENT '取消原因',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`enrollment_id`),
  UNIQUE KEY `uk_edu_enrollment_code` (`enrollment_code`),
  UNIQUE KEY `uk_edu_enrollment_once` (`schedule_id`, `parent_id`, `student_name`),
  KEY `idx_edu_enrollment_schedule` (`schedule_id`),
  KEY `idx_edu_enrollment_parent` (`parent_id`),
  KEY `idx_edu_enrollment_status` (`enrollment_status`, `pay_status`),
  CONSTRAINT `fk_edu_enrollment_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `edu_course_schedule` (`schedule_id`),
  CONSTRAINT `fk_edu_enrollment_parent` FOREIGN KEY (`parent_id`) REFERENCES `sys_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程报名表';
