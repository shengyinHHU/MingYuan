-- 课次签到表：行 = 课次 × 学员
-- 教师/课程/教室信息通过 schedule_id JOIN edu_course_schedule 获得
-- 家长信息通过 enrollment_id JOIN edu_enrollment 获得；试听/调课学员 enrollment_id 为空
CREATE TABLE IF NOT EXISTS edu_class_sign_in (
  sign_in_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '签到记录ID',
  schedule_id      BIGINT       NOT NULL COMMENT '排课ID（关联edu_course_schedule）',
  class_date       DATE         NOT NULL COMMENT '课次日期',
  enrollment_id    BIGINT       DEFAULT NULL COMMENT '报名记录ID（报名学员关联；试听/调课学员为空）',
  student_name     VARCHAR(30)  NOT NULL COMMENT '学生姓名（报名学员冗余快照；试听/调课手输）',
  source_type      CHAR(1)      NOT NULL DEFAULT '1' COMMENT '学员来源（1报名 2试听 3调课）',
  sign_status      CHAR(1)      NOT NULL DEFAULT '1' COMMENT '签到结果（1到课 2录播 3请假 4试听到课 5调课到课）',
  review_status    CHAR(1)      DEFAULT '0' COMMENT '复核状态（0待复核 1复核完成）',
  review1_admin_id BIGINT       DEFAULT NULL COMMENT '第1位确认管理员用户ID',
  review1_admin_name VARCHAR(30) DEFAULT NULL COMMENT '第1位确认管理员姓名',
  review1_images   VARCHAR(2000) DEFAULT NULL COMMENT '第1位管理员课堂图片路径，逗号分隔',
  review1_time     DATETIME     DEFAULT NULL COMMENT '第1位确认时间',
  review2_admin_id BIGINT       DEFAULT NULL COMMENT '第2位确认管理员用户ID',
  review2_admin_name VARCHAR(30) DEFAULT NULL COMMENT '第2位确认管理员姓名',
  review2_images   VARCHAR(2000) DEFAULT NULL COMMENT '第2位管理员课堂图片路径，逗号分隔',
  review2_time     DATETIME     DEFAULT NULL COMMENT '第2位确认时间',
  del_flag         CHAR(1)      DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  create_by        VARCHAR(64)  DEFAULT '' COMMENT '创建者（提交签到的教师）',
  create_time      DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by        VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time      DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL COMMENT '备注（试听/调课学员联系方式等）',
  PRIMARY KEY (sign_in_id),
  UNIQUE KEY uk_schedule_date_student (schedule_id, class_date, student_name),
  KEY idx_enrollment (enrollment_id),
  KEY idx_class_date (class_date)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='课次签到表';
