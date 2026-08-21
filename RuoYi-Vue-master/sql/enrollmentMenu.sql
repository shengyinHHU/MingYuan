-- 菜单 SQL
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程报名', '3', '1', 'enrollment', 'system/enrollment/index', 1, 0, 'C', '0', '0', 'system:enrollment:list', '#', 'admin', sysdate(), '', null, '课程报名菜单');

-- 按钮父菜单ID
SELECT @parentId := LAST_INSERT_ID();

-- 按钮 SQL
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程报名查询', @parentId, '1',  '#', '', 1, 0, 'F', '0', '0', 'system:enrollment:query',        '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程报名新增', @parentId, '2',  '#', '', 1, 0, 'F', '0', '0', 'system:enrollment:add',          '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程报名修改', @parentId, '3',  '#', '', 1, 0, 'F', '0', '0', 'system:enrollment:edit',         '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程报名删除', @parentId, '4',  '#', '', 1, 0, 'F', '0', '0', 'system:enrollment:remove',       '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('课程报名导出', @parentId, '5',  '#', '', 1, 0, 'F', '0', '0', 'system:enrollment:export',       '#', 'admin', sysdate(), '', null, '');