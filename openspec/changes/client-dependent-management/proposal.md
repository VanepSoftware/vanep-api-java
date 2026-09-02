## Why

A aplicação exige que cada contrato de transporte esteja vinculado a exatamente um dependente (RN01) e o envio de proposta de contratação requer a presença de pelo menos um dependente selecionado. Para viabilizar o fluxo do cliente (Sprint MVP), é necessária a interface completa de **Gerenciamento de Dependentes (Tela S14)** no app/web do cliente, consumindo o backend de dependentes (`/api/dependent` — Issue #25).

Prioridade: **Média** (bloqueante para envio de propostas de contratos).

## What Changes

- **Tela de Listagem de Dependentes (S14)**:
  - Exibição em lista com cards contendo Avatar, Nome, Data de Nascimento (`Nasc.: DD/MM/AAAA`) e Badge "Padrão" em destaque visual para o dependente marcado com `is_default = true`.
  - Botão principal no rodapé da lista: `+ Adicionar dependente`.
  - Barra de navegação inferior (Nav) com suporte ao fluxo principal do cliente.

- **Fluxo de Cadastro / Edição (Modal ou Form)**:
  - Campos: Nome completo (obrigatório), Data de Nascimento, Sexo/Gênero, Endereço residencial (`AddressRequestDTO`: CEP, Logradouro, Número, Bairro, Cidade, Estado), Turno (`shift`) e Marcador "Dependente Padrão".
  - Suporte ao fluxo de criação (`POST /api/dependent`) e edição parcial (`PATCH /api/dependent/{token}`).

- **Regra de Dependente Padrão (RN12)**:
  - Único dependente cadastrado é configurado como padrão automaticamente pelo backend (`is_default = true`).
  - Quando houver 2+ dependentes, a seleção do padrão pode ser feita manualmente via form ou ação no card.

- **Integração Backend (#25)**:
  - Consumo completo dos endpoints `/api/dependent` (`GET`, `POST`, `PATCH`, `DELETE`).
  - Tratamento de mensagens de erro e validações retornadas em `pt-BR`.

- **Layout e Responsividade**:
  - Fidelidade visual ao protótipo do Figma (Tela 14 - `plugin.js`: títulos `32px`/`60px`, subtítulo `13px` cinza, cards de altura `80px` com cantos arredondados, botões azuis e badges contrastantes).
  - Adaptação responsiva para Android/iOS e navegadores mobile/desktop.

**Fora de escopo:**
- Modificações no schema de banco de dados (o backend #25 já suporta todas as colunas necessárias).
- Vínculo com motoristas ou rotas diretamente nesta tela (tratado na tela de Proposta/Contrato).

## Capabilities

### New Capabilities

- `client-dependent-management`: Interface e integração do cliente para gerenciar dependentes (S14), criação, edição, marcação de padrão (RN12) e remoção.

### Modified Capabilities

- `dependent`: Consumo e validação dos contratos expostos pela API `/api/dependent`.

## Impact

- **UI / Frontend**: Criação dos componentes visuais (Frame S14, `DependentCard`, `Badge`, `DependentForm`, Modal de Edição/Criação, Integração com API).
- **Backend API (#25)**: Consumo dos DTOs `DependentCreateDTO`, `DependentUpdateDTO` e `DependentResponseDTO`.
- **Validação & UX**: Feedback visual instantâneo para erros de validação (campos obrigatórios, formato de data, CPF/Documento duplicado) e estados de carregamento (loading/empty states).
