-- Copyright (c) 2026 devlive-community/grantforge
--
-- Licensed under the MIT License. See the LICENSE file in the
-- project root for full license text.

-- An AuthX 1.x database (script/schema/schema.sql) with the cases the import must handle.
CREATE TABLE authx_user (id INT PRIMARY KEY, name VARCHAR(100), password VARCHAR(200), avatar VARCHAR(100), active TINYINT,
  locked TINYINT, is_system TINYINT, email VARCHAR(100));
CREATE TABLE authx_role (id INT PRIMARY KEY, name VARCHAR(100), code VARCHAR(50), description VARCHAR(100), active TINYINT);
CREATE TABLE authx_menu (id INT PRIMARY KEY, name VARCHAR(100), code VARCHAR(100), url VARCHAR(200), sorted INT, parent INT,
  description VARCHAR(100), active TINYINT);
CREATE TABLE authx_method (id INT PRIMARY KEY, name VARCHAR(100), method VARCHAR(200));
CREATE TABLE authx_user_role_relation (user_id INT, role_id INT);
CREATE TABLE authx_role_menu_relation (role_id INT, menu_id INT);
CREATE TABLE authx_menu_method_relation (menu_id INT, method_id INT);

INSERT INTO authx_user VALUES
  (1, '系统用户', NULL, NULL, 0, 1, 1, '0'),
  (2, 'admin', '8e70fdbd0400b7a21539fd15fb4ab86c129f7cbd99261dbb0d95c18df8dec177', NULL, 1, 0, 0, 'admin@example.com'),
  (3, 'user', '0C3AFF135C4EBC4BE35C7657B4B50F08FA1F3F888263CFC54585F4A4ACBEE71B', NULL, 0, 1, 1, 'not an address'),
  (4, 'Admin', 'ae448ac86c4e8e4dec645729708ef41873ae79c6dff84eff73360989487f08e5', NULL, 1, 0, 0, NULL),
  (5, 'nopass', NULL, NULL, 1, 0, 0, NULL),
  (6, 'taken', '99341d56cc28805370b9f449c044a4cf00a1e7eb003362116c814fd96899c55c', NULL, 1, 0, 0, NULL);
INSERT INTO authx_role VALUES
  (1, '管理员', 'GLY', '系统最高权限', 1),
  (2, '普通用户', 'PT YH', NULL, 0),
  (3, '无代码', NULL, NULL, 1),
  (4, '重复', 'gly', NULL, 1),
  (5, '系统', 'tenant-admin', NULL, 1);
INSERT INTO authx_menu VALUES
  (2, '系统菜单', 'XTCD', '#', 2, 0, NULL, 1),
  (3, '菜单管理', 'CDGL', '/admin/menus', 1, 2, '管理菜单', 1),
  (8, '用户管理', 'YHGL', '#', 1, 0, NULL, 1),
  (9, '查询用户个人信息接口', 'CX', '/api/v1/user/info/*', 1, 8, NULL, 0),
  (40, '新增', 'XZ', 'add', 1, 3, NULL, 1),
  (41, '更深', 'GS', 'deeper', 1, 40, NULL, 1),
  (50, '孤儿', 'GE', '/orphan', 1, 99, NULL, 1),
  (60, '环一', 'H1', '#', 1, 61, NULL, 1),
  (61, '环二', 'H2', '#', 1, 60, NULL, 1);
INSERT INTO authx_method VALUES (1, 'GET', 'GET'), (2, 'POST', 'post'), (3, 'TRACE', 'TRACE');
INSERT INTO authx_user_role_relation VALUES (2, 1), (3, 2), (1, 1), (2, NULL), (6, 1);
INSERT INTO authx_role_menu_relation VALUES (1, 2), (1, 3), (1, 9), (2, 3), (1, 61), (5, 3);
INSERT INTO authx_menu_method_relation VALUES (3, 1), (9, 1), (9, 2), (9, 3);
