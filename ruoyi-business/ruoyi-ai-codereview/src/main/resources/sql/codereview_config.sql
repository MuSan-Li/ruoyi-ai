-- 代码审查配置表
DROP TABLE IF EXISTS cr_review_config;
CREATE TABLE cr_review_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_type VARCHAR(50) DEFAULT 'STRING' COMMENT '配置类型: STRING, NUMBER, BOOLEAN, JSON',
    description VARCHAR(500) COMMENT '配置说明',
    category VARCHAR(50) DEFAULT 'general' COMMENT '配置分类: general, scoring, notification, platform',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    tenant_id VARCHAR(20) DEFAULT '000000' COMMENT '租户ID',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码审查配置表';

-- 审查规则表
DROP TABLE IF EXISTS cr_review_rule;
CREATE TABLE cr_review_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    rule_code VARCHAR(50) NOT NULL COMMENT '规则编码',
    dimension VARCHAR(50) NOT NULL COMMENT '审查维度: functionality, security, best_practice, performance, commit_message',
    weight INT DEFAULT 0 COMMENT '权重(满分)',
    description VARCHAR(500) COMMENT '规则说明',
    check_pattern TEXT COMMENT '检查正则表达式(JSON数组)',
    severity VARCHAR(20) DEFAULT 'warning' COMMENT '严重级别: info, warning, error, critical',
    language_pattern VARCHAR(200) COMMENT '适用语言(逗号分隔或正则)',
    file_pattern VARCHAR(200) COMMENT '适用文件路径模式',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序',
    tenant_id VARCHAR(20) DEFAULT '000000' COMMENT '租户ID',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_code (rule_code, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查规则表';

-- 项目审查配置表
DROP TABLE IF EXISTS cr_project_config;
CREATE TABLE cr_project_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    platform VARCHAR(20) NOT NULL COMMENT '平台: github, gitlab, gitea',
    project_id VARCHAR(100) COMMENT '平台项目ID',
    model_id BIGINT COMMENT '使用的模型ID',
    review_enabled TINYINT DEFAULT 1 COMMENT '是否启用审查',
    push_review_enabled TINYINT DEFAULT 1 COMMENT '是否启用Push审查',
    protected_branches_only TINYINT DEFAULT 0 COMMENT '仅审查受保护分支',
    file_extensions VARCHAR(500) COMMENT '审查文件扩展名(逗号分隔)',
    exclude_patterns VARCHAR(500) COMMENT '排除文件模式(逗号分隔)',
    max_files_per_review INT DEFAULT 50 COMMENT '单次最大审查文件数',
    max_tokens INT DEFAULT 10000 COMMENT '最大Token数',
    review_style VARCHAR(50) DEFAULT 'professional' COMMENT '审查风格',
    pass_score INT DEFAULT 60 COMMENT '通过分数',
    notification_channels VARCHAR(200) COMMENT '通知渠道(逗号分隔)',
    custom_rules TEXT COMMENT '自定义规则(JSON)',
    tenant_id VARCHAR(20) DEFAULT '000000' COMMENT '租户ID',
    create_dept BIGINT COMMENT '创建部门',
    create_by BIGINT COMMENT '创建者',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT COMMENT '更新者',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project (project_name, platform, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目审查配置表';

-- 审查统计表（用于趋势分析）
DROP TABLE IF EXISTS cr_review_statistics;
CREATE TABLE cr_review_statistics (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    platform VARCHAR(20) NOT NULL COMMENT '平台',
    stat_date DATE NOT NULL COMMENT '统计日期',
    total_reviews INT DEFAULT 0 COMMENT '总审查次数',
    total_files INT DEFAULT 0 COMMENT '总文件数',
    total_additions INT DEFAULT 0 COMMENT '总新增行数',
    total_deletions INT DEFAULT 0 COMMENT '总删除行数',
    avg_score DECIMAL(5,2) DEFAULT 0 COMMENT '平均分数',
    max_score INT DEFAULT 0 COMMENT '最高分',
    min_score INT DEFAULT 0 COMMENT '最低分',
    pass_count INT DEFAULT 0 COMMENT '通过次数',
    fail_count INT DEFAULT 0 COMMENT '不通过次数',
    functionality_avg DECIMAL(5,2) COMMENT '功能性平均分',
    security_avg DECIMAL(5,2) COMMENT '安全性平均分',
    best_practice_avg DECIMAL(5,2) COMMENT '最佳实践平均分',
    performance_avg DECIMAL(5,2) COMMENT '性能平均分',
    commit_message_avg DECIMAL(5,2) COMMENT '提交信息平均分',
    tenant_id VARCHAR(20) DEFAULT '000000' COMMENT '租户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stat (project_name, platform, stat_date, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查统计表';

-- 初始化默认配置
INSERT INTO cr_review_config (config_key, config_value, config_type, description, category, sort_order) VALUES
-- 通用配置
('review.enabled', 'true', 'BOOLEAN', '是否启用代码审查', 'general', 1),
('review.max_tokens', '10000', 'NUMBER', '单次审查最大Token数', 'general', 2),
('review.max_files', '50', 'NUMBER', '单次审查最大文件数', 'general', 3),
('review.style', 'professional', 'STRING', '审查风格: professional, friendly, strict', 'general', 4),
('review.parallel.enabled', 'true', 'BOOLEAN', '是否启用并行审查', 'general', 5),
('review.parallel.threads', '4', 'NUMBER', '并行审查线程数', 'general', 6),
('review.incremental.enabled', 'true', 'BOOLEAN', '是否启用增量审查(只审查变更行)', 'general', 7),

-- 评分配置
('scoring.pass_score', '60', 'NUMBER', '审查通过分数线', 'scoring', 1),
('scoring.excellent_score', '90', 'NUMBER', '优秀分数线', 'scoring', 2),
('scoring.good_score', '80', 'NUMBER', '良好分数线', 'scoring', 3),
('scoring.pass_score', '70', 'NUMBER', '合格分数线', 'scoring', 4),
('scoring.functionality.weight', '40', 'NUMBER', '功能性权重', 'scoring', 10),
('scoring.security.weight', '30', 'NUMBER', '安全性权重', 'scoring', 11),
('scoring.best_practice.weight', '20', 'NUMBER', '最佳实践权重', 'scoring', 12),
('scoring.performance.weight', '5', 'NUMBER', '性能权重', 'scoring', 13),
('scoring.commit_message.weight', '5', 'NUMBER', '提交信息权重', 'scoring', 14),

-- 通知配置
('notification.enabled', 'true', 'BOOLEAN', '是否启用通知', 'notification', 1),
('notification.include_full_report', 'false', 'BOOLEAN', '是否包含完整报告', 'notification', 2),
('notification.summary_max_length', '500', 'NUMBER', '摘要最大长度', 'notification', 3),

-- 平台配置
('platform.github.enabled', 'true', 'BOOLEAN', '是否启用GitHub', 'platform', 1),
('platform.gitlab.enabled', 'true', 'BOOLEAN', '是否启用GitLab', 'platform', 2),
('platform.gitea.enabled', 'true', 'BOOLEAN', '是否启用Gitea', 'platform', 3);

-- 初始化默认审查规则
INSERT INTO cr_review_rule (rule_name, rule_code, dimension, weight, description, severity, language_pattern, enabled, sort_order) VALUES
-- 功能性规则
('空指针检查', 'NULL_POINTER_CHECK', 'functionality', 10, '检查可能的空指针异常', 'error', '*', 1, 1),
('异常处理完整性', 'EXCEPTION_HANDLING', 'functionality', 8, '检查异常处理是否完整', 'warning', '*', 1, 2),
('边界条件检查', 'BOUNDARY_CHECK', 'functionality', 6, '检查边界条件处理', 'warning', '*', 1, 3),
('返回值检查', 'RETURN_VALUE_CHECK', 'functionality', 5, '检查返回值是否正确处理', 'warning', '*', 1, 4),

-- 安全性规则
('SQL注入检查', 'SQL_INJECTION', 'security', 15, '检查SQL注入风险', 'critical', '*', 1, 10),
('XSS漏洞检查', 'XSS_CHECK', 'security', 12, '检查XSS跨站脚本风险', 'critical', '*', 1, 11),
('敏感信息暴露', 'SENSITIVE_DATA', 'security', 15, '检查敏感信息是否暴露', 'critical', '*', 1, 12),
('权限验证', 'AUTHORIZATION', 'security', 10, '检查权限验证是否完整', 'error', '*', 1, 13),

-- 最佳实践规则
('命名规范', 'NAMING_CONVENTION', 'best_practice', 5, '检查命名是否符合规范', 'info', '*', 1, 20),
('代码重复', 'CODE_DUPLICATION', 'best_practice', 8, '检查代码重复', 'warning', '*', 1, 21),
('注释完整性', 'COMMENT_COMPLETENESS', 'best_practice', 5, '检查注释是否完整', 'info', '*', 1, 22),
('代码复杂度', 'CODE_COMPLEXITY', 'best_practice', 7, '检查代码复杂度', 'warning', '*', 1, 23),

-- 性能规则
('循环优化', 'LOOP_OPTIMIZATION', 'performance', 5, '检查循环是否有优化空间', 'warning', '*', 1, 30),
('资源释放', 'RESOURCE_RELEASE', 'performance', 5, '检查资源是否正确释放', 'error', '*', 1, 31),
('内存使用', 'MEMORY_USAGE', 'performance', 5, '检查内存使用是否合理', 'warning', '*', 1, 32),

-- 提交信息规则
('提交格式', 'COMMIT_FORMAT', 'commit_message', 3, '检查提交信息格式', 'info', '*', 1, 40),
('提交内容描述', 'COMMIT_DESCRIPTION', 'commit_message', 2, '检查提交内容描述是否完整', 'info', '*', 1, 41);
