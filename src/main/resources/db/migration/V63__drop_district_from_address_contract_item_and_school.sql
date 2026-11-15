-- The neighborhood shown on screens is address.neighborhood: the postal form writes it, and a
-- Google place writes its shallowest sublocality. District nodes stay only for the driver search
-- and the service areas, so no address, contract item or school points to them any more. The name
-- of the linked node is kept as the neighborhood where none was stored; a school's node goes to
-- the school's address.

update address
set neighborhood = district.name
from district
where address.district_id = district.id
  and address.neighborhood is null;

update address
set neighborhood = district.name
from school
join district on district.id = school.district_id
where school.address_id = address.id
  and address.neighborhood is null;

update contract_item
set pickup_neighborhood = district.name
from district
where contract_item.pickup_district_id = district.id
  and contract_item.pickup_neighborhood is null;

alter table address drop column district_id;

alter table contract_item drop column pickup_district_id;

alter table school drop column district_id;
