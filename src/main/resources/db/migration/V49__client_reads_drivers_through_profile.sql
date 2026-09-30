-- V30 deu list_drivers e show_driver ao CLIENT para a home e o detalhe do motorista
-- funcionarem. Só que essas rotas devolvem o DriverResponseDTO administrativo, com
-- email, CPF, CNPJ e status de aprovação: qualquer cliente logado lia o cadastro
-- inteiro de todos os motoristas.
--
-- O cliente agora usa rotas próprias, que só expõem o que é público e só de
-- motorista aprovado e ativo: GET /api/drivers/recommended, GET /api/drivers/search
-- e GET /api/drivers/{token}/profile. As duas permissões deixam de ser do CLIENT.
--
-- Idempotente: o operador "-" em jsonb remove o elemento se existir e ignora se não.

update role_permissions
set permissions = permissions - 'list_drivers' - 'show_driver'
where name = 'CLIENT';
