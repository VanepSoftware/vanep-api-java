-- O endereço residencial passou a morar em users.address_id na fase 5. Manter
-- client.address_id em paralelo eram duas fontes de verdade para o mesmo
-- endereço do mesmo ser humano — e quem é cliente e motorista ao mesmo tempo
-- acabaria com dois endereços diferentes.
--
-- A leitura do endereço do cliente passa a atravessar client -> users.

drop index if exists client_address_id_active_key;
alter table client drop constraint if exists client_address_id_fkey;
alter table client drop column address_id;
