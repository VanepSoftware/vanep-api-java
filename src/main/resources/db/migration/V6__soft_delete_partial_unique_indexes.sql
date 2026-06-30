-- Soft delete (@SoftDelete) mantém as linhas removidas na tabela, apenas
-- preenchendo deleted_at. As constraints UNIQUE simples passariam a contar essas
-- linhas e bloqueariam o re-cadastro de um email/documento/token já "deletado".
--
-- Trocamos por índices únicos PARCIAIS: a unicidade só vale entre linhas ATIVAS
-- (deleted_at IS NULL), liberando os valores quando a conta é desativada.

-- users
alter table users drop constraint users_token_key;
alter table users drop constraint users_email_key;
alter table users drop constraint users_username_key;
alter table users drop constraint users_document_key;

create unique index users_token_active_key on users (token) where deleted_at is null;
create unique index users_email_active_key on users (email) where deleted_at is null;
create unique index users_username_active_key on users (username) where deleted_at is null;
create unique index users_document_active_key on users (document) where deleted_at is null;

-- client
alter table client drop constraint client_token_key;
alter table client drop constraint client_user_id_key;

create unique index client_token_active_key on client (token) where deleted_at is null;
create unique index client_user_id_active_key on client (user_id) where deleted_at is null;

-- driver
alter table driver drop constraint driver_token_key;
alter table driver drop constraint driver_user_id_key;

create unique index driver_token_active_key on driver (token) where deleted_at is null;
create unique index driver_user_id_active_key on driver (user_id) where deleted_at is null;
