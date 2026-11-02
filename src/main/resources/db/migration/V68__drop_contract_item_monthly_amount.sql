-- A contract is priced as a whole: the total and its installments, with no price per dependent.

alter table contract_item drop constraint contract_item_monthly_amount_check;
alter table contract_item drop column monthly_amount;

comment on column contract.total_amount is
    'Total amount of the contract, split into installments; this is what is billed.';
