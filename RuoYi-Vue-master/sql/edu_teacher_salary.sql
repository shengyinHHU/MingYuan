-- ========================================================
-- 教师课时费功能：排课增加授课形式 + 薪资标准表 + 月度结算表
-- ========================================================

-- 1. 排课表增加授课形式（1班课 2一对一）
ALTER TABLE edu_course_schedule
  ADD COLUMN class_mode CHAR(1) DEFAULT '1' COMMENT '授课形式（1班课 2一对一）' AFTER class_type;

-- 2. 教师薪资标准表（每老师一行）
CREATE TABLE IF NOT EXISTS edu_teacher_salary_config (
  config_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  teacher_id           BIGINT       NOT NULL COMMENT '教师用户ID（sys_user）',
  teacher_name         VARCHAR(30)  DEFAULT NULL COMMENT '教师姓名（冗余）',
  base_salary          DECIMAL(10,2) DEFAULT 0 COMMENT '月底薪（元）',
  rate_primary_class   DECIMAL(10,2) DEFAULT 0 COMMENT '小学班课（元/小时）',
  rate_jh_class        DECIMAL(10,2) DEFAULT 0 COMMENT '初高班课（元/小时）',
  rate_primary_1on1    DECIMAL(10,2) DEFAULT 0 COMMENT '小学一对一（元/小时）',
  rate_junior_1on1     DECIMAL(10,2) DEFAULT 0 COMMENT '初中一对一（元/小时）',
  rate_senior_1on1     DECIMAL(10,2) DEFAULT 0 COMMENT '高中一对一（元/小时）',
  class_hours_per_session DECIMAL(4,1) DEFAULT 2.0 COMMENT '班课每次课时长（小时，默认2）',
  status               CHAR(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  del_flag             CHAR(1)      DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  create_by            VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time          DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by            VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time          DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark               VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (config_id),
  UNIQUE KEY uk_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师薪资标准表';

-- 3. 教师月度薪资结算表（每月每老师一行，快照）
CREATE TABLE IF NOT EXISTS edu_teacher_salary_record (
  record_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '结算ID',
  teacher_id           BIGINT       NOT NULL COMMENT '教师用户ID',
  teacher_name         VARCHAR(30)  DEFAULT NULL COMMENT '教师姓名（冗余）',
  salary_month         CHAR(7)      NOT NULL COMMENT '结算月份（yyyy-MM）',

  base_salary          DECIMAL(10,2) DEFAULT 0 COMMENT '底薪（快照）',

  primary_class_count  INT          DEFAULT 0 COMMENT '小学班课节数',
  primary_class_hours  DECIMAL(8,1) DEFAULT 0 COMMENT '小学班课小时数',
  primary_class_amount DECIMAL(10,2) DEFAULT 0 COMMENT '小学班课金额',
  jh_class_count       INT          DEFAULT 0 COMMENT '初高班课节数',
  jh_class_hours       DECIMAL(8,1) DEFAULT 0 COMMENT '初高班课小时数',
  jh_class_amount      DECIMAL(10,2) DEFAULT 0 COMMENT '初高班课金额',
  primary_1on1_count   INT          DEFAULT 0 COMMENT '小学一对一人次',
  primary_1on1_hours   DECIMAL(8,1) DEFAULT 0 COMMENT '小学一对一小时数',
  primary_1on1_amount  DECIMAL(10,2) DEFAULT 0 COMMENT '小学一对一金额',
  junior_1on1_count    INT          DEFAULT 0 COMMENT '初中一对一人次',
  junior_1on1_hours    DECIMAL(8,1) DEFAULT 0 COMMENT '初中一对一小时数',
  junior_1on1_amount   DECIMAL(10,2) DEFAULT 0 COMMENT '初中一对一金额',
  senior_1on1_count    INT          DEFAULT 0 COMMENT '高中一对一人次',
  senior_1on1_hours    DECIMAL(8,1) DEFAULT 0 COMMENT '高中一对一小时数',
  senior_1on1_amount   DECIMAL(10,2) DEFAULT 0 COMMENT '高中一对一金额',

  total_lessons        INT          DEFAULT 0 COMMENT '总结课节数/人次',
  total_hours          DECIMAL(8,1) DEFAULT 0 COMMENT '总小时数',
  lesson_amount        DECIMAL(10,2) DEFAULT 0 COMMENT '课时费合计',
  total_amount         DECIMAL(10,2) DEFAULT 0 COMMENT '应发合计（底薪+课时费）',

  confirm_status       CHAR(1)      DEFAULT '0' COMMENT '确认状态（0待确认 1已确认）',
  confirm_by           VARCHAR(64)  DEFAULT '' COMMENT '确认人',
  confirm_time         DATETIME     DEFAULT NULL COMMENT '确认时间',
  del_flag             CHAR(1)      DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  create_by            VARCHAR(64)  DEFAULT '' COMMENT '创建者',
  create_time          DATETIME     DEFAULT NULL COMMENT '创建时间',
  update_by            VARCHAR(64)  DEFAULT '' COMMENT '更新者',
  update_time          DATETIME     DEFAULT NULL COMMENT '更新时间',
  remark               VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (record_id),
  UNIQUE KEY uk_teacher_month (teacher_id, salary_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师月度薪资结算表';

-- 4. 菜单（挂在"教务工具" parent_id=3 下）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
    menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (2066, '薪资标准', 3, 12, 'salaryConfig', 'system/salaryConfig/index', 1, 0,
   'C', '0', '0', 'system:salaryConfig:list', 'money', 'admin', NOW(), '教师薪资标准（底薪+课时单价）'),
  (2067, '薪资结算', 3, 13, 'salaryRecord', 'system/salaryRecord/index', 1, 0,
   'C', '0', '0', 'system:salaryRecord:list', 'documentation', 'admin', NOW(), '教师月度课时费结算');

-- 薪资标准按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
    menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (2068, '薪资标准查询', 2066, 1, '', '', 1, 0, 'F', '0', '0', 'system:salaryConfig:query', '#', 'admin', NOW(), ''),
  (2069, '薪资标准新增', 2066, 2, '', '', 1, 0, 'F', '0', '0', 'system:salaryConfig:add', '#', 'admin', NOW(), ''),
  (2070, '薪资标准修改', 2066, 3, '', '', 1, 0, 'F', '0', '0', 'system:salaryConfig:edit', '#', 'admin', NOW(), ''),
  (2071, '薪资标准删除', 2066, 4, '', '', 1, 0, 'F', '0', '0', 'system:salaryConfig:remove', '#', 'admin', NOW(), '');

-- 薪资结算按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
    menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (2072, '薪资结算查询', 2067, 1, '', '', 1, 0, 'F', '0', '0', 'system:salaryRecord:query', '#', 'admin', NOW(), ''),
  (2073, '薪资结算生成', 2067, 2, '', '', 1, 0, 'F', '0', '0', 'system:salaryRecord:add', '#', 'admin', NOW(), ''),
  (2074, '薪资结算确认', 2067, 3, '', '', 1, 0, 'F', '0', '0', 'system:salaryRecord:edit', '#', 'admin', NOW(), ''),
  (2075, '薪资结算删除', 2067, 4, '', '', 1, 0, 'F', '0', '0', 'system:salaryRecord:remove', '#', 'admin', NOW(), '');

-- 绑定管理员角色
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 2066), (1, 2067), (1, 2068), (1, 2069), (1, 2070), (1, 2071),
  (1, 2072), (1, 2073), (1, 2074), (1, 2075);
