## Why
O aplicativo precisa permitir que o cliente comunique que o aluno não irá usar a van ("Avisar Ausência"), seja no dia todo, apenas na ida ou apenas na volta, e que o motorista registre o não comparecimento após esgotada a tolerância na parada. Hoje isso não existe, causando recálculos incorretos, onde o motorista se desloca até paradas vazias. Precisamos desse CRUD de Absence para refletir a realidade das viagens do dia (RN-15).

## What Changes
- Nova entidade/tabela **`absence`**: registra a ausência com `dependent_id`, `absence_date`, `trip_id`, `source` (CLIENT, DRIVER, ASSISTANT), e notificação.
- Novo Enum **`AbsenceSource`**: para diferenciar a origem.
- O campo `trip_id` passa a ser obrigatório (NOT NULL), tornando a ausência um evento atrelado à viagem específica.
- O índice único passa a ser `(dependent_id, trip_id)` para garantir a idempotência por viagem.
- Endpoints para criação de Absence por parte do Cliente. Se ele selecionar "o dia todo", o backend busca todas as trips do dia para aquele dependente e insere os múltiplos registros.
- Endpoints para criação de Absence pelo Motorista/Assistente (atrelado à trip atual).
- Atualização do estado diário: Se a trip já foi gerada ou está em andamento, as entradas `PENDING` do checklist correspondentes ao aluno serão removidas.
- Disparo de evento para envio de Push Notification via FCM para a contraparte (cliente avisa motorista, motorista avisa cliente).

## Capabilities
### New Capabilities
- `absence-crud`: Gestão de ausências de dependentes. Criação por Cliente ou Motorista, afetando as rotas/checklists e notificando a parte envolvida.
