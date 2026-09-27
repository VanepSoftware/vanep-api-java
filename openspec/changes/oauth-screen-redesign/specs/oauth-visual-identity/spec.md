## ADDED Requirements

### Requirement: Paleta de cores alinhada à marca Vanep
As telas de autenticação SHALL usar a paleta oficial da Vanep definida via variáveis CSS semânticas em `auth.css`: fundo branco (`#ffffff`), azul navy (`#1a2d4a`) como cor de destaque e cinza claro (`#f4f4f5`) como fundo externo da página.

#### Scenario: Fundo da página é branco/cinza claro
- **WHEN** o usuário acessa qualquer tela de autenticação (`/login`, `/signup`, `/signup/client`, `/signup/driver`, `/forgot-password`, `/reset-password`, `/verify-email`)
- **THEN** o fundo da página SHALL ser cinza claro (`#f4f4f5`) com o card central branco (`#ffffff`)

#### Scenario: Cor de destaque é azul navy
- **WHEN** o usuário visualiza botões de ação primária (submit) ou o título "Vanep"
- **THEN** esses elementos SHALL usar o azul navy `#1a2d4a`; a cor laranja `#ff4200` NÃO SHALL aparecer em nenhum elemento

#### Scenario: Focus de input usa navy
- **WHEN** o usuário foca em um campo de formulário
- **THEN** a borda do campo SHALL mudar para `--vanep-navy` (`#1a2d4a`)

### Requirement: Tipografia padronizada com o frontend
As telas de autenticação SHALL usar a fonte Geist Sans, carregada via Google Fonts CDN com os pesos 400, 500, 600 e 700, alinhando com o frontend Next.js.

#### Scenario: Fonte Geist Sans aplicada
- **WHEN** o usuário acessa qualquer tela de autenticação
- **THEN** todo o texto SHALL ser renderizado em Geist Sans; a fonte Figtree NÃO SHALL ser carregada ou aplicada

### Requirement: Variáveis CSS semânticas e documentadas
O arquivo `auth.css` SHALL organizar todas as cores em variáveis CSS agrupadas por categoria com comentários descritivos, facilitando reuso em outras telas.

#### Scenario: Variáveis disponíveis para reuso
- **WHEN** um desenvolvedor precisar referenciar uma cor do sistema de auth
- **THEN** SHALL encontrar as variáveis `--vanep-navy`, `--vanep-navy-hover`, `--error-text`, `--success-text` (entre outras) documentadas em `:root` com comentário de categoria

### Requirement: Layout responsivo sem quebras
As telas de autenticação SHALL manter layout funcional e sem overflow horizontal em viewports de 375px (mobile), 768px (tablet) e 1280px (desktop).

#### Scenario: Mobile sem quebra de layout
- **WHEN** o usuário acessa as telas em viewport de 375px de largura
- **THEN** o card SHALL ocupar toda a largura disponível com padding interno, sem overflow horizontal e sem elementos cortados
