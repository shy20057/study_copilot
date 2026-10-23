-- Historical archived subjects may share a name; only active names are unique.
-- Fail visibly if legacy active rows collide; never silently rename user data.
alter table study_copilot.t_subject drop constraint uk_user_name;
create unique index uk_subject_active_normalized_name
    on study_copilot.t_subject(user_id,lower(btrim(name))) where archived=0;
