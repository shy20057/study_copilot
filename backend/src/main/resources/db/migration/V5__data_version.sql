-- Monotonic per-owner invalidation token used by the polling dashboard.
alter table study_copilot.t_user add column data_version bigint not null default 0;
create function study_copilot.bump_owner_data_version() returns trigger language plpgsql as $$
declare owner_id bigint;
begin
 if TG_OP='DELETE' then owner_id:=OLD.user_id; else owner_id:=NEW.user_id; end if;
 update study_copilot.t_user set data_version=data_version+1 where id=owner_id;
 if TG_OP='DELETE' then return OLD; else return NEW; end if;
end $$;
do $$
declare table_name text;
begin
 foreach table_name in array array['t_task','t_subject','t_user_setting','t_learning_day_rule','t_onboarding_progress','t_task_repeat_template','t_focus_session','t_focus_slice','t_reward_ledger','t_pet_status','t_result_presentation','t_attachment'] loop
 execute format('create trigger owner_data_version after insert or update or delete on study_copilot.%I for each row execute function study_copilot.bump_owner_data_version()', table_name);
 end loop;
end $$;

