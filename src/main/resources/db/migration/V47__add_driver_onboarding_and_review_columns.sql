alter table driver
    add column submitted_at timestamptz,
    add column rejection_reason varchar(255),
    add column reviewed_at timestamptz,
    add column reviewed_by bigint references users (id);

comment on column driver.submitted_at is 'Data e hora da submissão do onboarding para análise.';
comment on column driver.rejection_reason is 'Motivo da rejeição pelo administrador durante análise.';
comment on column driver.reviewed_at is 'Data e hora em que a análise (aprovação/rejeição) foi concluída.';
comment on column driver.reviewed_by is 'Identificador do usuário administrador que realizou a revisão.';

create index idx_driver_approval_status on driver (approval_status) where deleted_at is null;
