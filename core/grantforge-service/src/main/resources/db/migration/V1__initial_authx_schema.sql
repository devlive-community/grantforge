-- Initial schema snapshot; applied only to an empty database.
-- Existing databases are explicitly baselined and never re-imported.

SET NAMES utf8mb4;

CREATE TABLE `authx_menu` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `url` varchar(200) DEFAULT NULL,
  `icon` varchar(100) DEFAULT NULL,
  `sorted` int DEFAULT NULL,
  `level` int DEFAULT NULL,
  `tips` varchar(200) DEFAULT NULL,
  `newd` tinyint DEFAULT NULL,
  `parent` int DEFAULT NULL,
  `description` varchar(100) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  `is_system` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `authx_menu` VALUES (1,'按钮1','SYSTEMMENUMODEL','systemMenuModel','fa',1,1,'systemMenuModel',0,0,'systemMenuModel',1,'2019-04-26 11:15:58','2019-04-26 11:15:58',0),(2,'系统菜单','XTCD','#','zmdi zmdi-menu',2,1,'系统菜单管理项',0,0,'用于管理系统菜单等信息',1,'2019-04-26 11:21:48','2019-04-26 11:21:48',0),(3,'菜单管理','CDGL','/admin/menus','fa',1,1,'管理菜单等功能',0,2,'用于管理菜单等功能',1,'2019-04-26 11:23:02','2019-04-26 11:23:02',0),(4,'菜单类型管理','CDLXGL','/system/menu-type','fa',1,1,'用于管理菜单类型',0,2,NULL,0,'2019-04-30 08:13:17','2019-04-30 08:13:17',0),(5,'路由管理接口','QXGLJK','/api/v1/role','fa',1,1,'用于管理权限的查询,新增,修改,删除操作',0,6,'用于管理权限的查询,新增,修改,删除操作',1,'2019-04-30 10:24:02','2019-04-30 10:24:02',0),(6,'权限管理','QXGL','#','fa',1,1,'用于管理权限接口信息',0,0,'用于管理权限接口信息',1,'2019-04-30 10:27:11','2019-04-30 10:27:11',0),(7,'分配菜单权限接口','FPCDQXJK','/api/v1/role/menu','fa',1,1,'分配菜单权限接口',0,6,'分配菜单权限接口',1,'2019-04-30 10:43:13','2019-04-30 10:43:13',0),(8,'用户管理','YHGL','#','fa',1,1,'用于管理用户接口信息',0,0,NULL,1,'2019-04-30 11:01:51','2019-04-30 11:01:51',0),(9,'查询用户个人信息接口','CXYHGRXXJK','/api/v1/user/info/*','fa',1,1,'查询用户个人信息接口',0,8,NULL,1,'2019-04-30 11:03:02','2019-04-30 11:03:02',0),(10,'系统管理','XTSZ','#','zmdi zmdi-settings-square',3,1,'系统设置',0,0,NULL,1,'2019-05-02 12:49:30','2019-05-02 12:49:30',0),(11,'路由管理','XTSZ','/admin/roles','fa',1,1,'系统设置',0,10,NULL,1,'2019-05-02 12:49:36','2019-05-02 12:49:36',0),(12,'系统接口','XTJK','/system/settings/interface','fa',1,1,'系统设置',0,10,NULL,0,'2019-05-02 12:49:38','2019-05-02 12:49:38',0),(13,'请求方式','QQFS','/admin/methods','fa',1,1,'请求方式',0,10,'请求方式',1,'2019-05-02 12:54:09','2019-05-02 12:54:09',0),(14,'菜单管理','CDGL','#','fa',1,1,'菜单类型管理',0,0,NULL,1,'2019-05-02 14:58:01','2019-05-02 14:58:01',0),(15,'菜单类型管理接口','CDLXGLJK','/api/v1/system/menu/type','fa',1,1,'菜单类型管理接口',0,14,NULL,1,'2019-05-02 15:38:15','2019-05-02 15:38:15',0),(16,'测试按钮','CSAN','#','fa',1,1,'测试按钮',0,0,NULL,1,'2019-05-02 16:13:45','2019-05-02 16:13:45',0),(17,'请求方式管理','QQFSGL','#','fa',1,1,'请求方式管理',0,0,NULL,1,'2019-05-02 16:27:00','2019-05-02 16:27:00',0),(18,'请求方式管理接口','QQFSGLJK','/api/v1/method','fa',1,1,'请求方式管理接口',0,17,NULL,1,'2019-05-02 16:27:39','2019-05-02 16:27:39',0),(19,'根据路由获取路由菜单树形列表','GJLYHQLYCDJK','/api/v1/role/menus','fa',1,1,'根据路由获取路由菜单树形列表',1,14,NULL,1,'2019-05-02 17:57:36','2019-05-02 17:57:36',0),(20,'菜单管理接口','CDLXGLJK','/api/v1/menu','fa',1,1,'菜单管理接口',0,14,NULL,1,'2019-05-02 15:38:15','2019-05-02 15:38:15',0),(21,'系统管理','XTGL','#','fa',1,1,'系统管理',0,0,'系统管理',1,'2019-05-02 18:09:57','2019-05-02 18:09:57',0),(22,'系统接口白名单接口','XTJKBMDJK','/api/v1/system/interface','fa',1,1,'系统接口白名单接口',0,21,'系统接口白名单接口',1,'2019-05-02 18:10:50','2019-05-02 18:10:50',0),(23,'获取菜单父级接口','HQCDFJJK','/api/v1/system/menu/parent','fa',1,1,'获取菜单父级接口',0,14,'获取菜单父级接口',1,'2019-05-02 18:16:53','2019-05-02 18:16:53',0),(24,'用户管理接口','YHGLJK','/api/v1/user','fa',1,1,'用户信息管理接口',0,8,'获取个人信息接口',1,'2019-05-03 03:45:59','2019-05-03 03:45:59',0),(25,'仪表盘','YBP','/dashboard','zmdi zmdi-view-dashboard',1,1,'仪表盘',0,0,'仪表盘',1,'2019-05-05 08:25:25','2019-05-05 08:25:25',0),(26,'用户管理','YHGL','#','zmdi zmdi-account',1,1,'用户管理',0,0,'用于管理用户相关信息',1,'2019-05-05 18:06:32','2019-05-05 18:06:32',0),(27,'用户管理','YHGL','/admin/users','fa-user',1,1,'管理用户所有信息(列表,新增等操作)',0,26,'用于管理用户相关信息',1,'2019-05-05 18:09:41','2019-05-05 18:09:41',0),(28,'用户组管理','YHZGL','/userManager/users/groups','fa-user-group',1,1,'用于管理用户组(列表,新增等功能)',0,26,'用户组管理',1,'2019-05-05 18:29:29','2019-05-05 18:29:29',0),(29,'用户注册接口','YHZCJK','/api/v1/user/register','fa',1,1,'用户注册接口',0,8,'用户注册接口',1,'2019-05-06 08:34:57','2019-05-06 08:34:57',1),(30,'分配用户权限接口','FPYHQXJK','/api/v1/user/role','fa',1,1,'分配用户权限接口',0,8,'分配用户权限接口',1,'2019-05-06 11:17:35','2019-05-06 11:17:35',0),(31,'图标管理','TBGL','#','zmdi zmdi-circle',1,1,'用于管理图标等功能信息',0,0,'用于管理图标等功能信息',1,'2019-05-07 06:05:23','2019-05-07 06:05:23',0),(32,'图标库','TBK','/iconManager/icons/repository','fa',1,1,'用于管理系统的图标库信息',0,31,'用于管理系统的图标库信息',1,'2019-05-07 06:07:10','2019-05-07 06:07:10',0),(33,'日志管理','RZGL','#','zmdi zmdi-file',100,1,'用于管理系统日志等信息',0,0,'用于管理系统日志等信息',0,'2019-05-07 06:14:34','2019-05-07 06:14:34',0),(34,'日志类型管理','RZLXGL','/logManager/logs/type','a',1,1,'用于管理日志类型等信息',1,33,'用于管理日志类型等信息',0,'2019-05-07 06:16:15','2019-05-07 06:16:15',0),(35,'日志接口管理','RZJKGL','#','a',1,1,'日志接口管理',0,0,'日志接口管理',0,'2019-05-07 06:38:48','2019-05-07 06:38:48',0),(36,'日志类型管理接口','RZLXGLJK','/api/v1/system/log/type','a',1,1,'用于管理日志类型的接口(新增,删除,修改,列表)',0,35,'用于管理日志类型的接口(新增,删除,修改,列表)',0,'2019-05-07 06:40:32','2019-05-07 06:40:32',0),(37,'日志管理','RZGL','/logManager/logs','fa',1,1,'用于管理日志',0,33,'用于管理日志',0,'2019-05-07 10:53:13','2019-05-07 10:53:13',0),(38,'日志管理接口','RZGLJK','/api/v1/system/log','1',1,1,'用于管理日志信息',0,35,'用于管理日志信息',0,'2019-05-07 10:55:43','2019-05-07 10:55:43',0),(39,'查看日志详情接口','CKRZXQJK','/api/v1/system/log/details','fa',1,1,'查看日志详情接口',0,35,'查看日志详情接口',0,'2019-05-08 02:47:21','2019-05-08 02:47:21',0),(40,'图标类型管理','TBLXGL','/iconManager/icon/type','fa',1,1,'图标类型管理',1,31,'图标类型管理',0,'2019-05-08 10:18:49','2019-05-08 10:18:49',0),(41,'图标管理接口','TBGLJK','#','1',1,1,'图标管理接口',0,0,'图标管理接口',1,'2019-05-08 10:27:58','2019-05-08 10:27:58',0),(42,'图标类型管理接口','TBLXGLJK','/api/v1/icon/type','fa',1,1,'图标类型管理接口',0,41,'图标类型管理接口',1,'2019-05-08 10:30:08','2019-05-08 10:30:08',0),(43,'图标管理','TBGL','/iconManager/icon','fa',1,1,'图标管理',1,31,'图标管理',1,'2019-05-08 10:52:47','2019-05-08 10:52:47',0),(44,'图标管理接口','TBGLJK','/api/v1/icon','fa',1,1,'图标管理接口',0,41,'图标管理接口',1,'2019-05-08 11:37:09','2019-05-08 11:37:09',0),(45,'图标用途','TBYT','/iconManager/icon/usage','fa',1,1,'图标用途',0,31,'图标用途',1,'2019-05-08 12:26:25','2019-05-08 12:26:25',0),(46,'图标用途管理接口','TBYTGLJK','/api/v1/icon/usage','aa',1,1,'图标用途管理接口',0,41,'图标用途管理接口',1,'2019-05-08 12:28:19','2019-05-08 12:28:19',0),(47,'测试菜单图标功能','CSCDTBGN','#',NULL,100,1,'测试菜单图标功能',0,0,'测试菜单图标功能',1,'2019-05-09 08:07:36','2019-05-09 08:07:36',0),(48,'常用小工具','CYXGJ','#',NULL,10,1,'常用的小工具集合',0,0,'常用的小工具集合',1,'2019-05-20 17:07:18','2019-05-20 17:07:18',0),(49,'JSON 美化工具','JSON_FORMAT','/json/pretty',NULL,1,1,'JSON格式化工具',0,48,'JSON格式化工具',1,'2019-05-20 17:08:17','2019-05-20 17:08:17',0),(50,'小工具管理接口','XGJGLJK','#',NULL,1,1,'小工具管理接口',0,0,'小工具管理接口',1,'2019-05-21 12:04:43','2019-05-21 12:04:43',0),(51,'JSON小工具格式化接口','JSONXGJGLJK','/api/v1/json/pretty',NULL,1,1,'用于管理JSON小工具的查询,新增,修改,删除操作',0,50,'用于管理JSON小工具的查询,新增,修改,删除操作',1,'2019-05-21 12:05:51','2019-05-21 12:05:51',0),(52,'JSON小工具压缩接口','JSONXGJYSJK','/api/v1/json/compression',NULL,1,1,'JSON小工具压缩接口',0,50,'JSON小工具压缩接口',1,'2019-05-23 11:56:39','2019-05-23 11:56:39',0),(53,'数据概览管理接口','SJGLGLJK','#',NULL,1,1,'数据概览管理接口',0,0,'数据概览管理接口',1,'2019-05-24 07:21:16','2019-05-24 07:21:16',0),(54,'数据概览统计接口','SJGLTJJK','/api/v1/overview',NULL,1,1,'数据概览统计接口',0,53,'数据概览统计接口',1,'2019-05-24 07:21:59','2019-05-24 07:21:59',0),(55,'自定义菜单','ZDYCD','/custom',NULL,1,1,'s',1,0,'s',0,'2019-06-24 08:38:38','2019-06-24 08:38:38',0),(56,'用户登录授权接口','YHDLSQJK','/oauth/token',NULL,NULL,NULL,NULL,NULL,NULL,'用于授权用户登录',1,'2019-05-02 16:40:48','2019-05-02 16:40:48',1),(58,'首页','SY','/',NULL,NULL,NULL,NULL,NULL,NULL,'首页',1,'2023-07-14 08:47:09','2023-07-14 08:47:11',1),(59,'CSS 样式','CSSYS','/css/*',NULL,NULL,NULL,NULL,NULL,NULL,'站点使用的 CSS 静态资源',1,'2023-07-14 08:48:24',NULL,1),(60,'JavaScript','JS','/js/*',NULL,NULL,NULL,NULL,NULL,NULL,'站点使用的 JavaScript 静态资源',1,'2023-07-14 08:50:39',NULL,1),(61,'字体文件','ZTWJ','/fonts/*',NULL,NULL,NULL,NULL,NULL,NULL,'站点使用的字体文件',1,'2023-07-14 08:53:15',NULL,1),(62,'自定义资源','ZDYZY','/static/**',NULL,NULL,NULL,NULL,NULL,NULL,'站点使用自定义资源',1,'2023-07-14 08:59:46',NULL,1);

CREATE TABLE `authx_menu_method_relation` (
  `menu_id` int DEFAULT NULL,
  `method_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `authx_menu_method_relation` VALUES (7,2),(7,1),(13,2),(13,3),(14,2),(14,3),(14,4),(16,2),(1,1),(1,2),(1,3),(3,1),(19,3),(4,1),(5,1),(5,2),(5,3),(5,4),(6,1),(7,3),(8,1),(9,1),(10,1),(11,1),(12,1),(13,1),(14,1),(15,1),(15,2),(15,3),(15,4),(16,1),(17,1),(18,1),(18,2),(18,3),(18,4),(19,1),(20,1),(20,2),(21,1),(22,1),(22,2),(22,3),(23,1),(24,1),(25,1),(26,1),(27,1),(28,1),(29,2),(30,3),(27,3),(31,1),(32,1),(33,1),(34,1),(35,1),(36,1),(36,2),(36,3),(36,4),(37,1),(38,1),(39,1),(2,1),(40,1),(41,1),(42,1),(42,2),(42,3),(43,1),(44,1),(44,2),(44,3),(45,1),(46,1),(46,2),(46,3),(47,1),(48,1),(49,1),(50,1),(51,1),(51,2),(52,2),(53,1),(54,1),(55,1),(24,4),(6,4),(56,2),(58,1),(59,1),(60,1),(61,1),(62,1);

CREATE TABLE `authx_method` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL,
  `method` varchar(200) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `is_system` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `authx_method` VALUES (1,'GET','GET','GET 请求是一种用于从服务器获取数据的请求方式。它通过在请求 URL 中添加查询参数来向服务器传递数据，并从服务器返回响应数据。','GET',1,0,'2019-04-24 07:08:48','2023-06-26 14:05:45'),(2,'POST','POST','POST 请求是一种向服务器提交数据并创建新资源的请求方式。它通常被用于在服务器上创建新的资源，或者向服务器发送需要处理的数据。','POST',1,0,'2019-04-24 08:03:51','2023-06-26 14:06:46'),(3,'PUT','PUT','PUT 请求是一种用于向服务器发送数据并更新资源的请求方式。它通常用于更新现有资源的内容，或者在服务器上创建新的资源。','PUT',1,0,'2019-04-24 08:04:51','2023-06-26 14:06:56'),(4,'DELETE','DELETE','DELETE 请求是一种用于从服务器删除资源的请求方式。它通常用于删除指定的资源，无论是文件、记录还是其他类型的数据。','DELETE',1,0,'2019-04-24 08:05:19','2023-06-26 14:07:32'),(5,'PATCH','PATCH','PATCH 请求方法是一种用于部分更新资源的请求方式。与 PUT 请求方法不同，PUT 请求用于完全替换资源，而 PATCH 请求用于对资源进行局部修改。使用 PATCH 请求方法时，只需将要更新的字段或属性包含在请求中，而不需要发送完整的资源。','PATH',1,0,'2019-05-07 10:00:25','2023-06-26 14:08:32');

CREATE TABLE `authx_role` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(50) DEFAULT NULL,
  `description` varchar(100) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `authx_role` VALUES (1,'管理员','GLY','系统最高权限',1,'2019-05-02 16:39:18','2023-06-27 02:19:18'),(2,'普通用户','PTYH','普通用户权限,拥有基本查看功能',1,'2019-05-06 10:07:45','2023-06-27 02:17:34');

CREATE TABLE `authx_role_menu_relation` (
  `role_id` int DEFAULT NULL,
  `menu_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `authx_role_menu_relation` VALUES (2,7),(2,8),(2,9),(2,24),(2,29),(2,30),(2,19),(2,25),(1,33),(1,34),(1,37),(1,2),(1,3),(1,4),(1,35),(1,36),(1,38),(1,39),(1,6),(1,5),(1,7),(1,8),(1,9),(1,24),(1,29),(1,30),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,19),(1,20),(1,23),(1,48),(1,49),(1,17),(1,18),(1,50),(1,51),(1,52),(1,21),(1,22),(1,53),(1,54),(1,55),(1,25),(1,26),(1,27),(1,28);

CREATE TABLE `authx_user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  `password` varchar(200) DEFAULT NULL,
  `avatar` varchar(100) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  `locked` tinyint DEFAULT NULL,
  `is_system` tinyint DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `authx_user` VALUES (1,'系统用户',NULL,NULL,0,'2019-05-15 10:27:34','2019-05-15 10:27:34',1,1,'0'),(2,'admin','8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918','https://avatar.gitee.com/uploads/99/416299_qianmoQ.png!avatar100?1542964857',1,'2019-01-25 17:05:36','2023-06-20 01:55:56',0,0,'0'),(3,'user','04f8996da763b7a969b1028ee3007569eaf3a635486ddab211d512c85b9df8fb',NULL,1,'2019-03-11 03:50:34','2023-06-26 13:29:53',0,0,'0');

CREATE TABLE `authx_user_role_relation` (
  `user_id` int DEFAULT NULL,
  `role_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `authx_user_role_relation` VALUES (1,2),(1,1),(2,1),(2,2),(3,2);

CREATE TABLE `icon` (
  `id` int DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `zh_name` varchar(100) DEFAULT NULL,
  `icon` varchar(100) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `icon` VALUES (1,'3d-rotation','zmdi zmdi-3d-rotation',NULL,NULL,'3d-rotation',NULL,'2019-05-08 14:35:37','2019-05-08 14:35:37'),(2,'airplane-off','zmdi zmdi-airplane-off',NULL,NULL,'airplane-off',NULL,'2019-05-08 14:36:43','2019-05-08 14:36:43'),(3,'airplane','zmdi zmdi-airplane',NULL,NULL,'airplane',NULL,'2019-05-08 14:39:25','2019-05-08 14:39:25'),(4,'album','zmdi zmdi-album',NULL,NULL,'album',NULL,'2019-05-08 14:53:44','2019-05-08 14:53:44'),(5,'archive','zmdi zmdi-archive',NULL,NULL,'archive',NULL,'2019-05-09 05:16:55','2019-05-09 05:16:55'),(6,'assignment-account','zmdi zmdi-assignment-account',NULL,NULL,'assignment-account',NULL,'2019-05-09 05:17:19','2019-05-09 05:17:19'),(7,'assignment-alert','zmdi zmdi-assignment-alert',NULL,NULL,'assignment-alert',NULL,'2019-05-09 05:17:45','2019-05-09 05:17:45'),(8,'assignment-check','zmdi zmdi-assignment-check',NULL,NULL,'assignment-check',NULL,'2019-05-09 05:18:00','2019-05-09 05:18:00'),(9,'assignment-o','zmdi zmdi-assignment-o',NULL,NULL,'assignment-o',NULL,'2019-05-09 05:18:18','2019-05-09 05:18:18'),(10,'assignment-return','zmdi zmdi-assignment-return',NULL,NULL,'assignment-return',NULL,'2019-05-09 05:18:46','2019-05-09 05:18:46'),(11,'assignment-returned','zmdi zmdi-assignment-returned',NULL,NULL,'assignment-returned',NULL,'2019-05-09 05:19:15','2019-05-09 05:19:15'),(12,'assignment','zmdi zmdi-assignment',NULL,NULL,'zmdi zmdi-assignment',NULL,'2019-05-09 05:19:38','2019-05-09 05:19:38'),(13,'attachment-alt','zmdi zmdi-attachment-alt',NULL,NULL,'attachment-alt',NULL,'2019-05-09 05:20:05','2019-05-09 05:20:05'),(14,'attachment','zmdi zmdi-attachment',NULL,NULL,'attachment',NULL,'2019-05-09 05:20:20','2019-05-09 05:20:20'),(15,'audio','zmdi zmdi-audio',NULL,NULL,'audio',NULL,'2019-05-09 05:20:36','2019-05-09 05:20:36'),(16,'badge-check','zmdi zmdi-badge-check',NULL,NULL,'badge-check',NULL,'2019-05-09 05:20:51','2019-05-09 05:20:51'),(17,'balance-wallet','zmdi zmdi-balance-wallet',NULL,NULL,'balance-wallet',NULL,'2019-05-09 05:21:09','2019-05-09 05:21:09'),(18,'balance','zmdi zmdi-balance',NULL,NULL,'balance',NULL,'2019-05-09 05:21:28','2019-05-09 05:21:28'),(19,'battery-alert','zmdi zmdi-battery-alert',NULL,NULL,'battery-alert',NULL,'2019-05-09 05:21:49','2019-05-09 05:21:49'),(20,'battery-flash','zmdi zmdi-battery-flash',NULL,NULL,'battery-flash',NULL,'2019-05-09 05:22:05','2019-05-09 05:22:05'),(21,'battery-unknown','zmdi zmdi-battery-unknown',NULL,NULL,'battery-unknown',NULL,'2019-05-09 05:22:21','2019-05-09 05:22:21');

CREATE TABLE `icon_type` (
  `id` int DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `icon_type` VALUES (1,'Font Awesome','FONTAWESOME','Font Awesome Iconic',NULL,'2019-05-08 14:16:27','2019-05-08 14:16:27'),(2,'Material Design Iconic Font','MATERIALDESIGNICONICFONT','Material Design Iconic Font',NULL,'2019-05-08 14:16:39','2019-05-08 14:16:39');

CREATE TABLE `icon_type_icon_relation` (
  `icon_id` int DEFAULT NULL,
  `icon_type_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `icon_type_icon_relation` VALUES (1,2),(2,2),(3,2),(4,2),(5,2),(6,2),(7,2),(8,2),(9,2),(10,2),(11,2),(12,2),(13,2),(14,2),(15,2),(16,2),(17,2),(18,2),(19,2),(20,2),(21,2);

CREATE TABLE `icon_usage` (
  `id` int DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `icon_usage` VALUES (1,'网站','WZ','用于显示在网站上的图标',NULL,'2019-05-08 12:31:55','2019-05-08 12:31:55'),(2,'通知','TZ','用于显示在通知上的图标',NULL,'2019-05-08 12:32:09','2019-05-08 12:32:09');

CREATE TABLE `icon_usage_icon_relation` (
  `icon_id` int DEFAULT NULL,
  `icon_usage_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `icon_usage_icon_relation` VALUES (2,1),(3,1),(5,1),(6,1),(7,1),(8,1),(9,1),(10,1),(11,1),(12,1),(13,1),(14,1),(15,1),(16,1),(17,1),(18,1),(19,1),(20,1),(21,1);

CREATE TABLE `system_log` (
  `id` int DEFAULT NULL,
  `remote_ip` varchar(100) DEFAULT NULL,
  `url` varchar(200) DEFAULT NULL,
  `method` varchar(100) DEFAULT NULL,
  `class` varchar(100) DEFAULT NULL,
  `class_method` varchar(100) DEFAULT NULL,
  `args` text,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `system_log_type` (
  `id` int DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `system_log_type` VALUES (1,'用户登录日志','YHDLRZ','用户登录系统日志',1,'2019-03-11 03:24:26','2019-03-11 03:24:26'),(2,'修改内容日志','XGNRRZ','用户修改内容日志,一般请求为PUT的数据',1,'2019-05-07 07:00:24','2019-05-07 07:00:24'),(3,'数据校验日志','SJXYRZ','用于记录数据校验的日志(一般为使用@Validated的数据校验过程)',NULL,'2019-05-07 07:20:05','2019-05-07 07:20:05'),(4,'默认全部日志','MRQBRZ','默认全部日志',NULL,'2019-05-07 10:40:51','2019-05-07 10:40:51');

CREATE TABLE `system_log_type_relation` (
  `system_log_id` int DEFAULT NULL,
  `system_log_type_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `system_log_users_relation` (
  `system_log_id` int DEFAULT NULL,
  `users_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `system_menu_icon_relation` (
  `system_menu_id` int DEFAULT NULL,
  `icon_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `system_menu_icon_relation` VALUES (47,16),(48,14),(49,17),(50,2),(51,1),(52,5),(53,1),(54,1),(55,2);

CREATE TABLE `system_menu_type` (
  `id` int DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `code` varchar(100) DEFAULT NULL,
  `description` varchar(200) DEFAULT NULL,
  `active` tinyint DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `system_menu_type` VALUES (1,'按钮','AN','Button',1,'2019-05-02 16:23:26','2019-05-02 16:23:26'),(2,'接口','JK','api',1,'2019-05-02 18:04:08','2019-05-02 18:04:08'),(3,'菜单','CD','menu',0,'2019-05-02 16:23:47','2019-05-02 16:23:47'),(12,'输入框','SRK','input',1,'2019-05-02 16:23:56','2019-05-02 16:23:56'),(13,'超链接','CLJ','超链接',1,'2019-05-03 09:32:57','2019-05-03 09:32:57');

CREATE TABLE `system_menu_type_relation` (
  `system_menu_id` int DEFAULT NULL,
  `system_menu_type_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `system_menu_type_relation` VALUES (1,1),(2,3),(3,3),(4,3),(5,2),(6,2),(7,2),(8,2),(9,2),(10,3),(11,3),(12,3),(13,3),(14,2),(15,2),(16,1),(17,2),(18,2),(19,2),(20,2),(21,2),(22,2),(23,2),(24,2),(25,3),(26,3),(27,3),(28,3),(29,2),(30,2),(31,3),(32,3),(33,3),(34,3),(35,2),(36,2),(37,3),(38,2),(39,2),(40,3),(41,2),(42,2),(43,3),(44,2),(45,3),(46,2),(47,3),(48,3),(49,3),(50,2),(51,2),(52,2),(53,2),(54,2),(55,3);
