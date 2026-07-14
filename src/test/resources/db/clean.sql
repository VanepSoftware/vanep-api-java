-- Limpeza física entre testes. `repository.deleteAll()` agora é soft delete
-- (@SoftDelete), então as linhas permaneceriam e violariam os índices únicos.
-- DELETE nativo ignora o soft delete e zera as tabelas de verdade.
-- Ordem respeita as FKs (filhos antes de users).
delete from oauth_account;
delete from password_reset_token;
delete from email_verification_token;
delete from dependent;
delete from client;
delete from vehicle;
delete from driver;
delete from users;
delete from roles;
delete from role_permissions;
delete from school;
