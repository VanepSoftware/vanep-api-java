alter table email_verification_token add column code_hash varchar(64);
alter table email_verification_token add column failed_attempts integer not null default 0;

create index idx_email_verification_user_created on email_verification_token (user_id, created_at);

alter table password_reset_token add column code_hash varchar(64);
alter table password_reset_token add column failed_attempts integer not null default 0;

create index idx_password_reset_user_created on password_reset_token (user_id, created_at);
