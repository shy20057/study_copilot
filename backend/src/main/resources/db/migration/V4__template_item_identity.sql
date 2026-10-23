-- Identity survives rescheduling, resource-version changes and logical deletion.
alter table study_copilot.t_task
  add column template_id varchar(100),
  add column template_version varchar(40),
  add column template_item_id varchar(100),
  add column template_learning_date date,
  add constraint ck_task_template_identity check (
    (template_id is null and template_version is null and template_item_id is null and template_learning_date is null)
    or (template_id is not null and template_version is not null and template_item_id is not null
        and template_learning_date is not null and import_batch_id is not null));
create unique index uk_task_template_item_day
  on study_copilot.t_task(user_id,template_id,template_item_id,template_learning_date)
  where template_item_id is not null;
