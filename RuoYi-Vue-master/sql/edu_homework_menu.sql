-- 管理员作业归档管理菜单（系统菜单 parent_id=3）
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES ('作业归档管理', 3, 20, 'homework', 'system/homework/index', 1, 0, 'C', '0', '0', 'system:homework:list', 'documentation', 'admin', sysdate(), '', NULL, '管理员作业归档管理');

SET @homeworkMenuId = LAST_INSERT_ID();

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
('作业归档查询', @homeworkMenuId, 1, '#', '', 1, 0, 'F', '0', '0', 'system:homework:query', '#', 'admin', sysdate(), '', NULL, ''),
('作业归档', @homeworkMenuId, 2, '#', '', 1, 0, 'F', '0', '0', 'system:homework:archive', '#', 'admin', sysdate(), '', NULL, ''),
('作业恢复', @homeworkMenuId, 3, '#', '', 1, 0, 'F', '0', '0', 'system:homework:restore', '#', 'admin', sysdate(), '', NULL, ''),
('作业删除', @homeworkMenuId, 4, '#', '', 1, 0, 'F', '0', '0', 'system:homework:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu
WHERE menu_id = @homeworkMenuId OR parent_id = @homeworkMenuId
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);
