-- AI 代码审查模块 SQL

-- MR审查日志表
CREATE TABLE IF NOT EXISTS `mr_review_log` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`     VARCHAR(20) DEFAULT '000000' COMMENT '租户编号',
    `project_name`  VARCHAR(255) COMMENT '项目名称',
    `author`        VARCHAR(128) COMMENT '提交者',
    `source_branch` VARCHAR(255) COMMENT '源分支',
    `target_branch` VARCHAR(255) COMMENT '目标分支',
    `url`           VARCHAR(512) COMMENT 'MR链接',
    `commit_messages` TEXT COMMENT '提交信息',
    `score`         INT COMMENT 'AI评分(1-100)',
    `review_result` TEXT COMMENT '审查结果',
    `additions`     INT DEFAULT 0 COMMENT '新增行数',
    `deletions`     INT DEFAULT 0 COMMENT '删除行数',
    `last_commit_id` VARCHAR(64) DEFAULT '' COMMENT '最后commit ID',
    `platform`      VARCHAR(32) COMMENT '平台(gitlab/github/gitea)',
    `project_id`    VARCHAR(64) COMMENT '平台项目ID',
    `create_dept`   BIGINT COMMENT '创建部门',
    `create_by`     BIGINT COMMENT '创建者',
    `create_time`   DATETIME COMMENT '创建时间',
    `update_by`     BIGINT COMMENT '更新者',
    `update_time`   DATETIME COMMENT '更新时间',
    INDEX `idx_tenant_id` (`tenant_id`),
    INDEX `idx_update_time` (`update_time`),
    INDEX `idx_author` (`author`),
    INDEX `idx_project_name` (`project_name`)
) COMMENT '合并请求审查日志';

-- Push审查日志表
CREATE TABLE IF NOT EXISTS `push_review_log` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     VARCHAR(20) DEFAULT '000000' COMMENT '租户编号',
    `project_name`  VARCHAR(255) COMMENT '项目名称',
    `author`        VARCHAR(128) COMMENT '提交者',
    `branch`        VARCHAR(255) COMMENT '分支名',
    `commit_messages` TEXT COMMENT '提交信息',
    `score`         INT COMMENT 'AI评分',
    `review_result` TEXT COMMENT '审查结果',
    `additions`     INT DEFAULT 0 COMMENT '新增行数',
    `deletions`     INT DEFAULT 0 COMMENT '删除行数',
    `platform`      VARCHAR(32) COMMENT '平台',
    `project_id`    VARCHAR(64) COMMENT '平台项目ID',
    `create_dept`   BIGINT COMMENT '创建部门',
    `create_by`     BIGINT COMMENT '创建者',
    `create_time`   DATETIME COMMENT '创建时间',
    `update_by`     BIGINT COMMENT '更新者',
    `update_time`   DATETIME COMMENT '更新时间',
    INDEX `idx_tenant_id` (`tenant_id`),
    INDEX `idx_update_time` (`update_time`),
    INDEX `idx_author` (`author`),
    INDEX `idx_project_name` (`project_name`)
) COMMENT '推送审查日志';

-- 配置表
CREATE TABLE IF NOT EXISTS `codereview_config` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `tenant_id`     VARCHAR(20) DEFAULT '000000' COMMENT '租户编号',
    `config_key`    VARCHAR(128) NOT NULL COMMENT '配置键',
    `config_value`  TEXT COMMENT '配置值',
    `config_type`   VARCHAR(32) COMMENT '配置类型(llm/platform/notify/review)',
    `description`   VARCHAR(255) COMMENT '配置描述',
    `create_dept`   BIGINT COMMENT '创建部门',
    `create_by`     BIGINT COMMENT '创建者',
    `create_time`   DATETIME COMMENT '创建时间',
    `update_by`     BIGINT COMMENT '更新者',
    `update_time`   DATETIME COMMENT '更新时间',
    UNIQUE INDEX `uk_config_key` (`config_key`),
    INDEX `idx_tenant_id` (`tenant_id`)
) COMMENT '代码审查配置表';
