-- state e city entram na árvore criada pela V22: normalized_name para o match,
-- google_place_id para rastrear a origem.

alter table state add column normalized_name varchar(64);
alter table state add column google_place_id varchar(255);

alter table city add column normalized_name varchar(128);
alter table city add column google_place_id varchar(255);

-- Backfill das linhas já semeadas. translate() em vez de unaccent() para não
-- depender de extensão instalada no banco.
update state
set normalized_name = lower(translate(name,
    'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇç',
    'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'))
where normalized_name is null;

update city
set normalized_name = lower(translate(name,
    'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇç',
    'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'))
where normalized_name is null;

alter table state alter column normalized_name set not null;
alter table city alter column normalized_name set not null;

create unique index state_country_normalized_name_active_key
    on state (country_id, normalized_name) where deleted_at is null;
create unique index city_state_normalized_name_active_key
    on city (state_id, normalized_name) where deleted_at is null;

create unique index state_google_place_id_active_key
    on state (google_place_id) where deleted_at is null;
create unique index city_google_place_id_active_key
    on city (google_place_id) where deleted_at is null;
