-- client_driver.status is now derived from the link's contracts (ContractService recomputes
-- it on every contract write). Links set ACTIVE by hand, by V38/V39 or by the seeder have no
-- contract behind them, so they go back to PENDING, the status the policy gives a link without
-- contracts.

update client_driver link
set status = 'PENDING',
    updated_at = now()
where link.status = 'ACTIVE'
  and link.deleted_at is null
  and not exists (
      select 1
      from contract
      where contract.client_driver_id = link.id
        and contract.deleted_at is null);

-- Nothing writes the status directly anymore, so the permission that allowed it goes away.
update role_permissions
set permissions = permissions - 'update_client_driver'
where permissions ? 'update_client_driver';

comment on column client_driver.status is
    'Derived from the link''s contracts: ACTIVE with a SIGNED, ACTIVE or SUSPENDED contract, INACTIVE with only other contracts, PENDING without contracts. BLOCKED is the only manual value. deleted_at is an administrative removal.';
