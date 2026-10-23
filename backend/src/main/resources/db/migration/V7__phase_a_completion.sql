-- Task provenance and retryable authentication throttling. Prior migrations remain immutable.
alter table study_copilot.t_task
  add column cancel_batch_id uuid,
  add column deletion_batch_id uuid,
  add column repeat_overridden boolean not null default false;
alter table study_copilot.t_task alter column priority set default 2;
alter table study_copilot.t_task_repeat_template alter column priority set default 2;
alter table study_copilot.t_task add column backlog_skipped boolean not null default false;
alter table study_copilot.t_task_repeat_template
  add column last_generated_day date,
  add column next_check_at timestamptz not null default now();
create index idx_repeat_next_check on study_copilot.t_task_repeat_template(next_check_at,user_id)
  where deleted=0 and status='ACTIVE';
alter table study_copilot.t_user add column learning_rule_sequence bigint not null default 1;
alter table study_copilot.t_user add column last_seen_at timestamptz default now();
update study_copilot.t_user u set learning_rule_sequence=coalesce(
  (select max(version) from study_copilot.t_learning_day_rule r where r.user_id=u.id),1);
create index idx_task_delete_batch on study_copilot.t_task(user_id,deletion_batch_id)
  where deleted=1;
create index idx_task_repeat_future on study_copilot.t_task(user_id,repeat_template_id,target_date)
  where deleted=0 and status='TODO';

-- Integer slices allocate the floor of the complete interval globally, without losing
-- a second when fractional endpoints straddle a learning-day boundary.
alter table study_copilot.t_focus_slice drop constraint ck_slice_range;
alter table study_copilot.t_focus_slice add constraint ck_slice_range check (
  slice_end_at > slice_start_at
  and valid_seconds <= ceil(extract(epoch from slice_end_at-slice_start_at)));
alter table study_copilot.t_focus_session add column note varchar(500);
alter table study_copilot.t_focus_session add column completed_learning_date date;
update study_copilot.t_focus_session set completed_learning_date=
  (((end_at-interval '1 microsecond') at time zone coalesce(config_snapshot->>'timezone','Asia/Shanghai'))
    -make_interval(hours => coalesce((config_snapshot->>'dayStartHour')::integer,0)))::date
  where source='TIMER' and mode='FOCUS' and status='COMPLETED';

create table study_copilot.t_auth_throttle (
  scope varchar(12) not null check (scope in ('ACCOUNT','SOURCE')),
  identity_hash varchar(64) not null,
  failures integer not null default 0 check (failures>=0),
  window_start timestamptz not null default now(),
  blocked_until timestamptz,
  update_time timestamptz not null default now(),
  primary key(scope,identity_hash)
);
create index idx_auth_throttle_expiry on study_copilot.t_auth_throttle(update_time);

alter table study_copilot.t_user_setting
  add column partial_rewards_enabled boolean not null default true,
  add column sound_enabled boolean not null default true,
  add column animations_enabled boolean not null default true;
