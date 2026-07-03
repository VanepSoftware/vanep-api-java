## Why

As telas de autenticação do backend (login, cadastro, recuperação de senha) estão com tema escuro e cor laranja, inconsistentes com a identidade visual da Vanep — fundo branco, azul navy e fonte Geist Sans. A inconsistência é visível para qualquer usuário que passa pelo fluxo OAuth, impactando a percepção de qualidade do produto.

## What Changes

- Substituir fundo preto (`#000000`) por branco (`#ffffff`) com fundo externo cinza claro (`#f4f4f5`)
- Substituir cor de destaque laranja (`#ff4200`) pelo azul navy da marca (`#1a2d4a`)
- Trocar fonte Figtree por Geist Sans (mesma do frontend Next.js)
- Reorganizar variáveis CSS com nomes semânticos documentados para facilitar reuso
- Ajustar cores de estados (erro, sucesso, foco) para paleta clara

## Capabilities

### New Capabilities

- `oauth-visual-identity`: Paleta e tipografia das telas de autenticação alinhadas ao design system da Vanep (fundo branco, navy `#1a2d4a`, Geist Sans), com variáveis CSS semânticas reutilizáveis.

### Modified Capabilities

(nenhuma — sem mudança de requisitos funcionais, apenas visuais)

## Impact

- `src/main/resources/static/css/auth.css` — único arquivo CSS das telas de auth
- `src/main/resources/templates/*.html` — 8 templates Thymeleaf (troca de fonte via Google Fonts)
- Zero impacto em código Java, migrations, APIs ou testes
