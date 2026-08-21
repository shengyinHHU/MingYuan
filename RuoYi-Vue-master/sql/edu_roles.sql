-- ----------------------------
-- 名远教育：业务角色初始化
-- 只使用三类业务角色：家长(parent)、教师(teacher)、管理员(admin)
-- 说明：若依内置超级管理员 role_key 已经是 admin，因此管理员角色不重复创建。
-- ----------------------------

set names utf8mb4;

-- 开启若依自助注册功能，小程序家长注册依赖 /register 接口。
update sys_config
set config_value = 'true',
    update_by = 'admin',
    update_time = sysdate(),
    remark = '已开启：小程序家长注册依赖该开关'
where config_key = 'sys.account.registerUser';

-- 家长角色
update sys_role
set role_name = '家长',
    role_sort = 3,
    data_scope = '5',
    status = '0',
    del_flag = '0',
    update_by = 'admin',
    update_time = sysdate(),
    remark = '小程序家长端角色'
where role_key = 'parent';

insert into sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
select '家长', 'parent', 3, '5', 1, 1, '0', '0', 'admin', sysdate(), '小程序家长端角色'
where not exists (select 1 from sys_role where role_key = 'parent');

-- 教师角色
update sys_role
set role_name = '教师',
    role_sort = 2,
    data_scope = '5',
    status = '0',
    del_flag = '0',
    update_by = 'admin',
    update_time = sysdate(),
    remark = '小程序教师端角色'
where role_key = 'teacher';

insert into sys_role
    (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, create_time, remark)
select '教师', 'teacher', 2, '5', 1, 1, '0', '0', 'admin', sysdate(), '小程序教师端角色'
where not exists (select 1 from sys_role where role_key = 'teacher');

-- 管理员角色：沿用若依内置超级管理员 admin。
update sys_role
set role_name = '管理员',
    role_sort = 1,
    status = '0',
    del_flag = '0',
    update_by = 'admin',
    update_time = sysdate(),
    remark = '系统管理员角色，映射小程序管理员端'
where role_key = 'admin';

-- 如需严格隐藏若依默认普通角色，可手动执行下面语句。
-- update sys_role set status = '1', update_by = 'admin', update_time = sysdate(), remark = '已停用：当前业务仅保留家长、教师、管理员三类角色' where role_key = 'common';
