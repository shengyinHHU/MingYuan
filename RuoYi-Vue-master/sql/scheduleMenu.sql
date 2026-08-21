-- 菜单 SQL
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程排课', '3', '1', 'schedule', 'system/schedule/index', 1, 0, 'C', '0', '0', 'system:schedule:list', '#', 'admin', sysdate(), '', null, '课程排课菜单');

-- 按钮父菜单ID
SELECT @parentId := LAST_INSERT_ID();

-- 按钮 SQL
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程排课查询', @parentId, '1',  '#', '', 1, 0, 'F', '0', '0', 'system:schedule:query',        '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程排课新增', @parentId, '2',  '#', '', 1, 0, 'F', '0', '0', 'system:schedule:add',          '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程排课修改', @parentId, '3',  '#', '', 1, 0, 'F', '0', '0', 'system:schedule:edit',         '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程排课删除', @parentId, '4',  '#', '', 1, 0, 'F', '0', '0', 'system:schedule:remove',       '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程排课导出', @parentId, '5',  '#', '', 1, 0, 'F', '0', '0', 'system:schedule:export',       '#', 'admin', sysdate(), '', null, '');