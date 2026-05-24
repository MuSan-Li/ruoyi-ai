-- =====================================
-- 代码审查模块菜单和权限配置
-- =====================================

-- 1. 创建代码审查一级菜单 (parent_id=0 表示一级菜单)
INSERT INTO `sys_menu` VALUES (
    2000300000000000001,  -- menu_id (使用雪花ID)
    '代码审查',            -- menu_name
    0,                     -- parent_id (0表示一级菜单)
    5,                     -- order_num (排序)
    'codereview',          -- path
    '',                    -- component
    '',                    -- query
    1,                     -- is_frame (0=外链 1=内链)
    0,                     -- is_cache (0=缓存 1=不缓存)
    'M',                   -- menu_type (M=目录 C=菜单 F=按钮)
    '0',                   -- visible (0=显示 1=隐藏)
    '0',                   -- status (0=正常 1=停用)
    NULL,                  -- perms
    'carbon:code-review',  -- icon
    103,                   -- create_dept
    1,                     -- create_by
    NOW(),                 -- create_time
    NULL,                  -- update_by
    NULL,                  -- update_time
    '代码审查目录'          -- remark
);

-- 2. 项目配置管理菜单
INSERT INTO `sys_menu` VALUES (
    2000300000000000100,  -- menu_id
    '项目配置',            -- menu_name
    2000300000000000001,  -- parent_id (指向代码审查目录)
    1,                    -- order_num
    'projectConfig',      -- path
    'codereview/projectConfig/index', -- component
    '',                   -- query
    1,                    -- is_frame
    0,                    -- is_cache
    'C',                  -- menu_type (C=菜单)
    '0',                  -- visible
    '0',                  -- status
    'codereview:projectConfig:list', -- perms
    'ant-design:setting-outlined', -- icon
    103,                  -- create_dept
    1,                    -- create_by
    NOW(),                -- create_time
    NULL,                 -- update_by
    NULL,                 -- update_time
    '项目配置管理菜单'      -- remark
);

-- 2.1 项目配置 - 查询权限
INSERT INTO `sys_menu` VALUES (
    2000300000000000101, '项目配置查询', 2000300000000000100, 1, '#', '', '', 1, 0, 'F', '0', '0',
    'codereview:projectConfig:query', '#', 103, 1, NOW(), NULL, NULL, ''
);

-- 2.2 项目配置 - 新增权限
INSERT INTO `sys_menu` VALUES (
    2000300000000000102, '项目配置新增', 2000300000000000100, 2, '#', '', '', 1, 0, 'F', '0', '0',
    'codereview:projectConfig:add', '#', 103, 1, NOW(), NULL, NULL, ''
);

-- 2.3 项目配置 - 修改权限
INSERT INTO `sys_menu` VALUES (
    2000300000000000103, '项目配置修改', 2000300000000000100, 3, '#', '', '', 1, 0, 'F', '0', '0',
    'codereview:projectConfig:edit', '#', 103, 1, NOW(), NULL, NULL, ''
);

-- 2.4 项目配置 - 删除权限
INSERT INTO `sys_menu` VALUES (
    2000300000000000104, '项目配置删除', 2000300000000000100, 4, '#', '', '', 1, 0, 'F', '0', '0',
    'codereview:projectConfig:remove', '#', 103, 1, NOW(), NULL, NULL, ''
);

-- 3. 审查日志菜单
INSERT INTO `sys_menu` VALUES (
    2000300000000000200,  -- menu_id
    '审查日志',            -- menu_name
    2000300000000000001,  -- parent_id
    2,                    -- order_num
    'reviewLog',          -- path
    'codereview/reviewLog/index', -- component
    '',                   -- query
    1,                    -- is_frame
    0,                    -- is_cache
    'C',                  -- menu_type
    '0',                  -- visible
    '0',                  -- status
    'codereview:reviewLog:list', -- perms
    'material-symbols:docs-outline', -- icon
    103,                  -- create_dept
    1,                    -- create_by
    NOW(),                -- create_time
    NULL,                 -- update_by
    NULL,                 -- update_time
    '审查日志菜单'          -- remark
);

-- 3.1 审查日志 - 查询权限
INSERT INTO `sys_menu` VALUES (
    2000300000000000201, '审查日志查询', 2000300000000000200, 1, '#', '', '', 1, 0, 'F', '0', '0',
    'codereview:reviewLog:query', '#', 103, 1, NOW(), NULL, NULL, ''
);

-- 3.2 审查日志 - 删除权限
INSERT INTO `sys_menu` VALUES (
    2000300000000000202, '审查日志删除', 2000300000000000200, 2, '#', '', '', 1, 0, 'F', '0', '0',
    'codereview:reviewLog:remove', '#', 103, 1, NOW(), NULL, NULL, ''
);

-- 4. 统计分析菜单
INSERT INTO `sys_menu` VALUES (
    2000300000000000300,  -- menu_id
    '统计分析',            -- menu_name
    2000300000000000001,  -- parent_id
    3,                    -- order_num
    'statistics',         -- path
    'codereview/statistics/index', -- component (需要创建)
    '',                   -- query
    1,                    -- is_frame
    0,                    -- is_cache
    'C',                  -- menu_type
    '0',                  -- visible
    '0',                  -- status
    'codereview:statistics:query', -- perms
    'ant-design:bar-chart-outlined', -- icon
    103,                  -- create_dept
    1,                    -- create_by
    NOW(),                -- create_time
    NULL,                 -- update_by
    NULL,                 -- update_time
    '统计分析菜单'          -- remark
);

-- 4.1 统计分析 - 刷新权限
INSERT INTO `sys_menu` VALUES (
    2000300000000000301, '统计刷新', 2000300000000000300, 1, '#', '', '', 1, 0, 'F', '0', '0',
    'codereview:statistics:edit', '#', 103, 1, NOW(), NULL, NULL, ''
);

-- =====================================
-- 可选：为管理员角色分配权限
-- =====================================
-- 如果需要立即给管理员分配权限，可以执行以下SQL
-- 注意：role_id 需要根据实际情况调整

-- INSERT INTO `sys_role_menu` (role_id, menu_id) VALUES
-- (1, 2000300000000000001),  -- 代码审查目录
-- (1, 2000300000000000100),  -- 项目配置
-- (1, 2000300000000000101),  -- 项目配置查询
-- (1, 2000300000000000102),  -- 项目配置新增
-- (1, 2000300000000000103),  -- 项目配置修改
-- (1, 2000300000000000104),  -- 项目配置删除
-- (1, 2000300000000000200),  -- 审查日志
-- (1, 2000300000000000201),  -- 审查日志查询
-- (1, 2000300000000000202),  -- 审查日志删除
-- (1, 2000300000000000300),  -- 统计分析
-- (1, 2000300000000000301);  -- 统计刷新
