-- 菜单 SQL
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('教室信息', '3', '1', 'classroom', 'system/classroom/index', 1, 0, 'C', '0', '0', 'system:classroom:list', '#', 'admin', sysdate(), '', null, '教室信息菜单');

-- 按钮父菜单ID
SELECT @parentId := LAST_INSERT_ID();

-- 按钮 SQL
insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('教室信息查询', @parentId, '1',  '#', '', 1, 0, 'F', '0', '0', 'system:classroom:query',        '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('教室信息新增', @parentId, '2',  '#', '', 1, 0, 'F', '0', '0', 'system:classroom:add',          '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('教室信息修改', @parentId, '3',  '#', '', 1, 0, 'F', '0', '0', 'system:classroom:edit',         '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('教室信息删除', @parentId, '4',  '#', '', 1, 0, 'F', '0', '0', 'system:classroom:remove',       '#', 'admin', sysdate(), '', null, '');

insert into sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
values('教室信息导出', @parentId, '5',  '#', '', 1, 0, 'F', '0', '0', 'system:classroom:export',       '#', 'admin', sysdate(), '', null, '');