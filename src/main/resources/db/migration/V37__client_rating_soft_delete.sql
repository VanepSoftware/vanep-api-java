-- client_rating nasceu sem deleted_at: o delete era físico e destruía a avaliação
-- sem histórico e sem restore, enquanto a gêmea driver_rating (V16) já fazia soft
-- delete. Regra 19 manda soft delete.

alter table client_rating add column deleted_at timestamptz;

-- O índice único precisa virar parcial NO MESMO passo. Com deleted_at a linha
-- passa a permanecer; um índice total sobre o par barraria qualquer avaliação
-- nova daquele par, travando-o para sempre e transformando o restore na única
-- saída de um bloqueio que ele deveria evitar.
drop index idx_client_rating_driver_client_unique;

create unique index idx_client_rating_driver_client_unique
    on client_rating (driver_id, client_id)
    where deleted_at is null;
