-- Keep V1–V5 immutable; old attachments are not exposed by the private avatar API.
alter table study_copilot.t_user add column profile_version bigint not null default 0;

alter table study_copilot.t_attachment
  add column object_key varchar(255),
  add column media_type varchar(50),
  add constraint ck_attachment_avatar_metadata check (
    (object_key is null and media_type is null)
    or (object_key is not null and media_type is not null
        and media_type in ('image/jpeg','image/png','image/webp')
        and size_bytes is not null and size_bytes between 1 and 2097152
        and object_key like 'users/' || user_id::text || '/avatars/%'));
create unique index uk_attachment_object_key on study_copilot.t_attachment(object_key)
  where object_key is not null;
