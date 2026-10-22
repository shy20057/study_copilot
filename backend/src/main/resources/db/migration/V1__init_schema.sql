-- ============================================================
-- StudyCopilot 核心库初始化
-- 模块：用户/设置/科目/阶段/任务/番茄/统计/日志/萌宠/AI对话/附件/知识点/错题/提醒/通知
-- ============================================================

create schema if not exists study_copilot;

-- 1. 用户表
create table study_copilot.t_user (
  id            bigserial primary key,
  username      varchar(50)  not null unique,
  password      varchar(100) not null,
  email         varchar(100) unique,
  avatar        varchar(255),
  status        smallint     not null default 1,  -- 1正常 0禁用
  create_time   timestamptz  not null default now(),
  update_time   timestamptz  not null default now()
);
comment on table  study_copilot.t_user is '用户表';
comment on column study_copilot.t_user.password is 'BCrypt 加密密码';

-- 2. 用户设置表
create table study_copilot.t_user_setting (
  id                bigserial primary key,
  user_id           bigint      not null unique,
  exam_date         date,
  timezone          varchar(50) not null default 'Asia/Shanghai',
  day_start_hour    smallint    not null default 0,
  weekly_goal_min   integer     not null default 2100,
  focus_minutes     integer     not null default 25,
  theme             varchar(20) not null default 'light',
  ai_consent        smallint    not null default 0,
  onboarding_done   smallint    not null default 0,
  daily_avail_min   integer,
  update_time       timestamptz not null default now()
);
comment on table study_copilot.t_user_setting is '用户设置表';

-- 3. 科目表
create table study_copilot.t_subject (
  id         bigserial primary key,
  user_id    bigint      not null,
  name       varchar(50) not null,
  color      varchar(20),
  sort_order integer     not null default 0,
  archived   smallint    not null default 0,
  constraint uk_user_name unique (user_id, name)
);
comment on table study_copilot.t_subject is '科目表';

-- 4. 宏观阶段时间轴表
create table study_copilot.t_milestone (
  id          bigserial primary key,
  user_id     bigint      not null,
  title       varchar(100) not null,
  month_tag   varchar(7)  not null,
  start_date  date,
  end_date    date,
  status      varchar(20) not null default 'TODO',  -- TODO/IN_PROGRESS/DONE/REVIEW
  content     text,
  sort_order  integer     not null default 0,
  create_time timestamptz not null default now(),
  update_time timestamptz not null default now()
);
create index idx_milestone_user_month on study_copilot.t_milestone (user_id, month_tag);
comment on table study_copilot.t_milestone is '阶段时间轴表';

-- 5. 待办任务表
create table study_copilot.t_task (
  id                bigserial primary key,
  user_id           bigint       not null,
  milestone_id      bigint,
  parent_id         bigint,
  repeat_rule       varchar(100),
  repeat_src_id     bigint,
  planned_pomodoros smallint,
  sort_order        integer      not null default 0,
  title             varchar(200) not null,
  subject_id        bigint,
  status            varchar(20)  not null default 'TODO',  -- TODO/DONE
  priority          smallint     not null default 1,        -- 1低 2中 3高
  target_date       date,
  done_time         timestamptz,
  create_time       timestamptz  not null default now()
);
create index idx_task_user_date on study_copilot.t_task (user_id, target_date);
create index idx_task_milestone on study_copilot.t_task (milestone_id);
comment on table study_copilot.t_task is '待办任务表';

-- 6. 番茄钟流水表
create table study_copilot.t_pomodoro (
  id               bigserial primary key,
  user_id          bigint      not null,
  task_id          bigint,
  task_name        varchar(100),
  subject_id       bigint      not null,
  mode             varchar(10) not null default 'FOCUS',   -- FOCUS/SHORT/LONG
  status           varchar(15) not null default 'RUNNING', -- RUNNING/COMPLETED/ABANDONED
  source           varchar(10) not null default 'TIMER',   -- TIMER/MANUAL
  start_time       timestamptz not null,
  end_time         timestamptz,
  duration_minutes integer     not null default 25,
  exp_earned       integer     not null default 0,
  stat_date        date,
  create_time      timestamptz not null default now()
);
create index idx_pomodoro_user_time on study_copilot.t_pomodoro (user_id, start_time);
create index idx_pomodoro_user_statdate on study_copilot.t_pomodoro (user_id, stat_date);
comment on table study_copilot.t_pomodoro is '番茄钟记录表';

-- 7. 每日统计汇总表
create table study_copilot.t_daily_stat (
  id             bigserial primary key,
  user_id        bigint   not null,
  stat_date      date     not null,
  focus_minutes  integer  not null default 0,
  pomodoro_count integer  not null default 0,
  subject_minutes jsonb,
  hour_minutes   jsonb,
  constraint uk_user_date unique (user_id, stat_date)
);
comment on table study_copilot.t_daily_stat is '每日学习统计汇总表';

-- 8. 每日学习日志表
create table study_copilot.t_daily_journal (
  id               bigserial primary key,
  user_id          bigint       not null,
  log_date         date         not null,
  mood             varchar(20),  -- PUMPED/STEADY/TIRED/ANXIOUS
  content_markdown text,
  breakthrough     varchar(500),
  subject_hours    jsonb,
  ai_review_report text,
  ai_review_time   timestamptz,
  ai_feedback      smallint,
  create_time      timestamptz  not null default now(),
  update_time      timestamptz  not null default now(),
  constraint uk_journal_user_date unique (user_id, log_date)
);
comment on table study_copilot.t_daily_journal is '每日日志表';

-- 9. 萌宠养成状态表
create table study_copilot.t_pet_status (
  id          bigserial primary key,
  user_id     bigint     not null unique,
  pet_type    smallint   not null default 1,  -- 1粉粉喵 2汪汪柴 3软软兔 4熊熊帕 5啾啾鸡
  level       smallint   not null default 1,
  total_exp   integer    not null default 0,
  update_time timestamptz not null default now()
);
comment on table study_copilot.t_pet_status is '萌宠状态表';

-- 10. AI 对话消息表
create table study_copilot.t_chat_message (
  id          bigserial primary key,
  user_id     bigint      not null,
  session_id  varchar(40) not null,
  role        varchar(10) not null,  -- user/assistant
  content     text        not null,
  create_time timestamptz not null default now()
);
create index idx_chat_user_session on study_copilot.t_chat_message (user_id, session_id, create_time);
comment on table study_copilot.t_chat_message is 'AI对话消息表';

-- 11. 附件表
create table study_copilot.t_attachment (
  id          bigserial primary key,
  user_id     bigint       not null,
  url         varchar(500) not null,
  size_bytes  integer,
  create_time timestamptz  not null default now()
);
create index idx_attachment_user on study_copilot.t_attachment (user_id);
comment on table study_copilot.t_attachment is '附件表';

-- 12. 知识点表
create table study_copilot.t_knowledge_point (
  id          bigserial primary key,
  user_id     bigint       not null,
  subject_id  bigint       not null,
  parent_id   bigint,
  name        varchar(100) not null,
  mastery     smallint     not null default 0,  -- 0未学 1学习中 2已掌握
  sort_order  integer      not null default 0,
  update_time timestamptz  not null default now()
);
create index idx_kp_user_subject on study_copilot.t_knowledge_point (user_id, subject_id);
comment on table study_copilot.t_knowledge_point is '知识点表';

-- 13. 错题表
create table study_copilot.t_mistake (
  id                 bigserial primary key,
  user_id            bigint       not null,
  subject_id         bigint       not null,
  knowledge_point_id bigint,
  title              varchar(200),
  content_markdown   text,
  image_urls         jsonb,
  error_reason       varchar(20),  -- CARELESS/CONCEPT/UNKNOWN/CALC
  analysis           text,
  mastery            smallint     not null default 0,
  review_stage       smallint     not null default 0,
  next_review_date   date,
  last_review_time   timestamptz,
  source_date        date,
  create_time        timestamptz  not null default now(),
  update_time        timestamptz  not null default now()
);
create index idx_mistake_user_review on study_copilot.t_mistake (user_id, next_review_date);
create index idx_mistake_user_subject on study_copilot.t_mistake (user_id, subject_id);
comment on table study_copilot.t_mistake is '错题表';

-- 14. 提醒设置表
create table study_copilot.t_reminder_setting (
  id          bigserial primary key,
  user_id     bigint      not null,
  type        varchar(20) not null,  -- STUDY_START/MISTAKE_REVIEW/JOURNAL
  enabled     smallint    not null default 1,
  remind_time time        not null,
  channel     varchar(20) not null default 'IN_APP',
  constraint uk_user_type unique (user_id, type)
);
comment on table study_copilot.t_reminder_setting is '提醒设置表';

-- 15. 站内消息表
create table study_copilot.t_notification (
  id          bigserial primary key,
  user_id     bigint       not null,
  type        varchar(20)  not null,
  content     varchar(255) not null,
  is_read     smallint     not null default 0,
  create_time timestamptz  not null default now()
);
create index idx_notification_user_read on study_copilot.t_notification (user_id, is_read);
comment on table study_copilot.t_notification is '站内消息表';
