-- driver.city era texto livre sem FK e driver.service_areas era jsonb de
-- strings. Nenhum dos dois era consultável, e é essa a lacuna que a change
-- inteira existe para fechar: agora a origem é driver_service_area, ligada à
-- árvore geográfica por FK.
--
-- Corte destrutivo aceito (R5): não há dados reais em produção — confirmado
-- com o time antes do merge.

alter table driver drop column city;
alter table driver drop column service_areas;
