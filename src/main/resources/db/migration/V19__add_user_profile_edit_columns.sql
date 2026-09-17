alter table users
    add column pending_email varchar(255),
    add column last_phone_change_at timestamptz,
    add column last_email_change_at timestamptz;

comment on column users.pending_email is 'E-mail aguardando confirmação de troca; sem unique (vários users podem pending o mesmo).';
comment on column users.last_phone_change_at is 'Última mudança efetiva de telefone; base do cooldown de perfil.';
comment on column users.last_email_change_at is 'Última promoção de pending_email para email; base do cooldown de e-mail.';
