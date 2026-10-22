-- V1 is immutable. PostgreSQL remains the source of truth for Phase A.
create extension if not exists btree_gist with schema study_copilot;
set local search_path = study_copilot, public;

alter table study_copilot.t_user
  add column normalized_username varchar(50),
  add column nickname varchar(50),
  add column session_version bigint not null default 0,
  add column deleted smallint not null default 0,
  add column deleted_at timestamptz;
update study_copilot.t_user set normalized_username = lower(btrim(username));
alter table study_copilot.t_user
  alter column normalized_username set not null,
  add constraint uk_user_normalized_username unique (normalized_username),
  add constraint ck_user_normalized_username check
    (normalized_username = lower(btrim(username)) and length(normalized_username) > 0),
  add constraint ck_user_deleted check (deleted in (0,1));

alter table study_copilot.t_user_setting
  add column goal_name varchar(100),
  add column muted boolean not null default false,
  add column notifications_enabled boolean not null default false,
  add column notification_permission varchar(20) not null default 'DEFAULT',
  add column reduce_motion boolean not null default false,
  add column reduce_transparency boolean not null default false,
  add column default_subject_id bigint,
  add column version bigint not null default 0,
  alter column theme set default 'SYSTEM',
  alter column weekly_goal_min drop not null,
  alter column weekly_goal_min drop default,
  add constraint ck_setting_day_start check (day_start_hour between 0 and 6),
  add constraint ck_setting_weekly_goal check (weekly_goal_min is null or weekly_goal_min > 0),
  add constraint fk_setting_user foreign key (user_id) references study_copilot.t_user(id);
update study_copilot.t_user_setting set theme = upper(theme);

alter table study_copilot.t_subject
  add column version bigint not null default 0,
  add column create_time timestamptz not null default now(),
  add column update_time timestamptz not null default now(),
  add constraint uk_subject_owner unique (user_id,id),
  add constraint fk_subject_user foreign key (user_id) references study_copilot.t_user(id);
create index idx_subject_user_active_order on study_copilot.t_subject(user_id,archived,sort_order,id);
alter table study_copilot.t_user_setting add constraint fk_setting_default_subject
  foreign key (user_id,default_subject_id) references study_copilot.t_subject(user_id,id);

create table study_copilot.t_refresh_session (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  token_hash varchar(128) not null unique,
  device_description varchar(255),
  session_version bigint not null,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  create_time timestamptz not null default now(),
  constraint ck_refresh_expiry check (expires_at > create_time)
);
create index idx_refresh_user_active on study_copilot.t_refresh_session(user_id,revoked_at,expires_at);

create table study_copilot.t_learning_day_rule (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  version bigint not null check (version > 0),
  timezone varchar(50) not null,
  day_start_hour smallint not null check (day_start_hour between 0 and 6),
  effective_at timestamptz not null,
  create_time timestamptz not null default now(),
  constraint uk_learning_rule_version unique (user_id,version),
  constraint uk_learning_rule_effective unique (user_id,effective_at)
);
-- Preserve the current setting as the historical starting rule for existing users.
insert into study_copilot.t_learning_day_rule(user_id,version,timezone,day_start_hour,effective_at)
select u.id,1,coalesce(s.timezone,'Asia/Shanghai'),coalesce(s.day_start_hour,0),u.create_time
from study_copilot.t_user u left join study_copilot.t_user_setting s on s.user_id=u.id;

create table study_copilot.t_onboarding_progress (
  id bigserial primary key,
  user_id bigint not null unique references study_copilot.t_user(id),
  current_step varchar(40) not null default 'WELCOME',
  confirmed_data jsonb not null default '{}'::jsonb,
  status varchar(20) not null default 'IN_PROGRESS' check (status in ('IN_PROGRESS','SKIPPED','COMPLETED')),
  version bigint not null default 0,
  completed_at timestamptz,
  update_time timestamptz not null default now()
);

create table study_copilot.t_template_import_batch (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  request_id varchar(100) not null,
  import_batch_id uuid not null default gen_random_uuid(),
  template_id varchar(100) not null,
  template_version varchar(40) not null,
  preview_version varchar(128) not null,
  request_hash varchar(128) not null,
  import_result jsonb not null default '{}'::jsonb,
  create_time timestamptz not null default now(),
  constraint uk_import_request unique (user_id,request_id),
  constraint uk_import_batch unique (user_id,import_batch_id)
);
create index idx_import_user_created on study_copilot.t_template_import_batch(user_id,create_time desc,id desc);

create table study_copilot.t_task_repeat_template (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  title varchar(200) not null,
  subject_id bigint,
  planned_pomodoros smallint,
  priority smallint not null default 1,
  repeat_rule varchar(20) not null check (repeat_rule in ('DAILY','WEEKDAYS','WEEKLY')),
  weekdays smallint[] not null default '{}',
  start_date date not null,
  end_date date,
  status varchar(20) not null default 'ACTIVE' check (status in ('ACTIVE','PAUSED','CANCELLED')),
  version bigint not null default 0,
  deleted smallint not null default 0 check (deleted in (0,1)),
  create_time timestamptz not null default now(),
  update_time timestamptz not null default now(),
  constraint uk_repeat_template_owner unique (user_id,id),
  constraint fk_repeat_subject foreign key (user_id,subject_id) references study_copilot.t_subject(user_id,id),
  constraint ck_repeat_dates check (end_date is null or end_date >= start_date),
  constraint ck_repeat_weekdays check (weekdays <@ array[1,2,3,4,5,6,7]::smallint[])
);
create index idx_repeat_user_active on study_copilot.t_task_repeat_template(user_id,deleted,status,start_date,id);

alter table study_copilot.t_task
  add column version bigint not null default 0,
  add column deleted smallint not null default 0 check (deleted in (0,1)),
  add column deleted_at timestamptz,
  add column original_planned_date date,
  add column learning_date date,
  add column repeat_template_id bigint,
  add column import_batch_id uuid,
  add column subject_name_snapshot varchar(50),
  add column estimated_focus_minutes integer,
  add column cancelled_at timestamptz,
  add column update_time timestamptz not null default now(),
  add constraint uk_task_owner unique (user_id,id),
  add constraint uk_task_repeat_day unique (user_id,repeat_template_id,learning_date),
  add constraint ck_task_status check (status in ('TODO','DONE','CANCELLED')),
  add constraint ck_task_repeat_day check (repeat_template_id is null or learning_date is not null),
  add constraint fk_task_user foreign key (user_id) references study_copilot.t_user(id),
  add constraint fk_task_subject foreign key (user_id,subject_id) references study_copilot.t_subject(user_id,id),
  add constraint fk_task_parent foreign key (user_id,parent_id) references study_copilot.t_task(user_id,id),
  add constraint fk_task_repeat foreign key (user_id,repeat_template_id) references study_copilot.t_task_repeat_template(user_id,id),
  add constraint fk_task_import foreign key (user_id,import_batch_id) references study_copilot.t_template_import_batch(user_id,import_batch_id);
update study_copilot.t_task set original_planned_date=target_date,learning_date=target_date;
create index idx_task_user_list on study_copilot.t_task(user_id,deleted,target_date,status,sort_order,id);
create index idx_task_user_parent on study_copilot.t_task(user_id,parent_id,deleted,sort_order,id);
create index idx_task_user_subject on study_copilot.t_task(user_id,subject_id,status,deleted);

create table study_copilot.t_task_schedule_log (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  task_id bigint not null,
  operation_type varchar(30) not null,
  batch_id uuid,
  request_id varchar(100),
  previous_date date,
  new_date date,
  previous_version bigint not null,
  resulting_version bigint not null,
  previous_state jsonb not null default '{}'::jsonb,
  undo_expires_at timestamptz,
  undone_at timestamptz,
  create_time timestamptz not null default now(),
  constraint fk_schedule_task foreign key (user_id,task_id) references study_copilot.t_task(user_id,id)
);
create index idx_schedule_user_batch on study_copilot.t_task_schedule_log(user_id,batch_id,id);
create index idx_schedule_user_task on study_copilot.t_task_schedule_log(user_id,task_id,create_time desc,id desc);

create table study_copilot.t_focus_session (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  mode varchar(20) not null default 'FOCUS' check (mode in ('FOCUS','SHORT','LONG')),
  source varchar(10) not null default 'TIMER' check (source in ('TIMER','MANUAL')),
  status varchar(20) not null default 'RUNNING' check (status in ('RUNNING','COMPLETED','ENDED_EARLY','CANCELLED')),
  request_id varchar(100) not null,
  subject_id bigint,
  subject_name_snapshot varchar(50),
  subject_color_snapshot varchar(20),
  task_id bigint,
  task_title_snapshot varchar(200),
  config_snapshot jsonb not null default '{}'::jsonb,
  planned_seconds integer not null check (planned_seconds > 0),
  actual_seconds integer not null default 0 check (actual_seconds >= 0),
  valid_seconds integer not null default 0 check (valid_seconds >= 0 and valid_seconds <= actual_seconds),
  start_at timestamptz not null,
  scheduled_end_at timestamptz not null,
  end_at timestamptz,
  end_reason varchar(40),
  rule_version bigint not null,
  settlement_version bigint not null default 0,
  settled_at timestamptz,
  version bigint not null default 0,
  deleted smallint not null default 0 check (deleted in (0,1)),
  deleted_at timestamptz,
  create_time timestamptz not null default now(),
  update_time timestamptz not null default now(),
  effective_range tstzrange generated always as (
    case when mode='FOCUS' and deleted=0 and
      (status='RUNNING' or (status in ('COMPLETED','ENDED_EARLY') and valid_seconds >= 60))
    then tstzrange(start_at,coalesce(end_at,scheduled_end_at),'[)') else null end
  ) stored,
  constraint uk_focus_owner unique (user_id,id),
  constraint uk_focus_start_request unique (user_id,request_id),
  constraint fk_focus_subject foreign key (user_id,subject_id) references study_copilot.t_subject(user_id,id),
  constraint fk_focus_task foreign key (user_id,task_id) references study_copilot.t_task(user_id,id),
  constraint fk_focus_rule foreign key (user_id,rule_version) references study_copilot.t_learning_day_rule(user_id,version),
  constraint ck_focus_times check (scheduled_end_at > start_at and (end_at is null or end_at > start_at)),
  constraint ck_focus_state_time check ((status='RUNNING' and end_at is null) or (status<>'RUNNING' and end_at is not null)),
  constraint ck_focus_manual check (source<>'MANUAL' or (mode='FOCUS' and status='COMPLETED')),
  constraint ck_focus_break_seconds check (mode='FOCUS' or valid_seconds=0),
  constraint ex_focus_effective_range exclude using gist (user_id with =, effective_range with &&)
);
create unique index uk_focus_user_running
on study_copilot.t_focus_session (user_id)
where status = 'RUNNING' and deleted = 0;
create index idx_focus_user_records on study_copilot.t_focus_session(user_id,deleted,end_at desc,id desc);
create index idx_focus_user_subject on study_copilot.t_focus_session(user_id,subject_id,start_at desc,id desc);
create index idx_focus_due on study_copilot.t_focus_session(scheduled_end_at,id) where status='RUNNING' and deleted=0;

create table study_copilot.t_focus_slice (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  session_id bigint not null,
  learning_date date not null,
  rule_version bigint not null,
  slice_start_at timestamptz not null,
  slice_end_at timestamptz not null,
  valid_seconds integer not null check (valid_seconds > 0),
  subject_id bigint not null,
  create_time timestamptz not null default now(),
  constraint uk_slice_session_day unique (session_id,learning_date),
  constraint fk_slice_session foreign key (user_id,session_id) references study_copilot.t_focus_session(user_id,id),
  constraint fk_slice_rule foreign key (user_id,rule_version) references study_copilot.t_learning_day_rule(user_id,version),
  constraint fk_slice_subject foreign key (user_id,subject_id) references study_copilot.t_subject(user_id,id),
  constraint ck_slice_range check (slice_end_at > slice_start_at and valid_seconds <= extract(epoch from slice_end_at-slice_start_at))
);
create index idx_slice_user_day_subject on study_copilot.t_focus_slice(user_id,learning_date,subject_id,session_id);

create table study_copilot.t_idempotency_record (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  operation_type varchar(50) not null,
  request_id varchar(100) not null,
  request_hash varchar(128) not null,
  resource_type varchar(50),
  resource_id varchar(100),
  response_status integer,
  result jsonb,
  create_time timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours'),
  constraint uk_idempotency_request unique (user_id,operation_type,request_id),
  constraint ck_idempotency_retention check (expires_at >= create_time + interval '24 hours')
);
create index idx_idempotency_expiry on study_copilot.t_idempotency_record(expires_at,id);

create table study_copilot.t_reward_ledger (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  session_id bigint not null,
  learning_date date not null,
  rule_version bigint not null,
  reward_factor numeric(3,2) not null check (reward_factor in (0,0.5,1)),
  base_exp integer not null check (base_exp >= 0),
  awarded_exp integer not null check (awarded_exp >= 0),
  daily_cap integer not null default 9600 check (daily_cap=9600),
  capped boolean not null default false,
  create_time timestamptz not null default now(),
  constraint uk_reward_session_day unique (session_id,learning_date),
  constraint fk_reward_session foreign key (user_id,session_id) references study_copilot.t_focus_session(user_id,id),
  constraint fk_reward_rule foreign key (user_id,rule_version) references study_copilot.t_learning_day_rule(user_id,version),
  constraint ck_reward_award check (awarded_exp <= daily_cap and awarded_exp <= floor(base_exp * reward_factor))
);
create index idx_reward_user_day on study_copilot.t_reward_ledger(user_id,learning_date,id);

alter table study_copilot.t_pet_status
  alter column total_exp type bigint,
  add column version bigint not null default 0,
  add constraint fk_pet_user foreign key (user_id) references study_copilot.t_user(id),
  add constraint ck_pet_type check (pet_type between 1 and 5),
  add constraint ck_pet_exp check (total_exp >= 0);
comment on column study_copilot.t_pet_status.level is 'Legacy V1 field; Phase A derives level from total_exp and must not use this as a balance.';

create table study_copilot.t_result_presentation (
  id bigserial primary key,
  user_id bigint not null references study_copilot.t_user(id),
  session_id bigint not null unique,
  result jsonb not null default '{}'::jsonb,
  claimed_by_device varchar(100),
  claimed_at timestamptz,
  acknowledged_at timestamptz,
  version bigint not null default 0,
  create_time timestamptz not null default now(),
  constraint fk_presentation_session foreign key (user_id,session_id) references study_copilot.t_focus_session(user_id,id)
);
create index idx_presentation_user_claim on study_copilot.t_result_presentation(user_id,acknowledged_at,create_time,id);

create table study_copilot.t_account_deletion_job (
  id bigserial primary key,
  user_id bigint not null unique references study_copilot.t_user(id),
  status varchar(20) not null default 'PENDING' check (status in ('PENDING','RUNNING','RETRY','COMPLETED')),
  retry_count integer not null default 0 check (retry_count >= 0),
  next_attempt_at timestamptz not null default now(),
  last_error_code varchar(100),
  final_result jsonb,
  completed_at timestamptz,
  version bigint not null default 0,
  create_time timestamptz not null default now(),
  update_time timestamptz not null default now()
);
create index idx_deletion_jobs_due on study_copilot.t_account_deletion_job(status,next_attempt_at,id);
