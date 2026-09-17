## Context

O backend expõe um Authorization Server OAuth2/OIDC (Spring Authorization Server). As telas de autenticação são renderizadas pelo servidor via Thymeleaf e estilizadas por um único arquivo CSS estático (`auth.css`). Atualmente, esse CSS usa tema escuro (fundo preto, laranja) sem relação com a identidade visual definida no frontend Next.js (`globals.css`: branco, navy, Geist Sans).

A mudança é puramente visual — zero alteração de comportamento, rotas, segurança ou dados.

## Goals / Non-Goals

**Goals:**
- Alinhar paleta das telas de auth ao design system da Vanep (fundo branco, navy `#1a2d4a`, Geist Sans)
- Centralizar todas as cores em variáveis CSS semânticas e documentadas em `:root`
- Garantir responsividade em mobile (375px), tablet (768px) e desktop (1280px+)

**Non-Goals:**
- Mudanças em fluxo de autenticação, rotas ou lógica de segurança
- Suporte a dark mode (fora do escopo desta task)
- Alterações no frontend Next.js

## Decisions

**D1 — Um único arquivo CSS para todas as telas**
Mantemos `auth.css` como fonte única de estilos. Alternativa seria um CSS por tela, mas geraria duplicação sem benefício real dado o escopo pequeno.

**D2 — Variáveis CSS semânticas agrupadas por categoria**
As variáveis são organizadas em seções comentadas (`Brand`, `Superfície`, `Tipografia`, `Feedback`) em vez de nomes genéricos como `--accent`. Isso facilita reuso por outros desenvolvedores sem precisar inspecionar o código.

```css
/* Brand — azul navy extraído do logo Vanep */
--vanep-navy:        #1a2d4a;
--vanep-navy-hover:  #243b5e;
--vanep-navy-subtle: #eef1f6;

/* Superfície */
--bg:    #f4f4f5;   /* fundo da página */
--card:  #ffffff;   /* card branco com sombra */
--border: #e4e4e7;

/* Tipografia */
--text:  #0a0a0a;
--muted: #71717a;

/* Feedback — erro / sucesso (backgrounds claros) */
--error-bg: #fef2f2;  --error-border: #fca5a5;  --error-text: #dc2626;
--success-bg: #f0fdf4; --success-border: #86efac; --success-text: #16a34a;
```

**D3 — Geist Sans via Google Fonts**
O frontend usa Geist Sans via `next/font`. Para o backend (HTML estático), carregamos via Google Fonts CDN com os mesmos pesos (400, 500, 600, 700). Alternativa de self-hosting seria mais robusta offline mas adiciona complexidade desnecessária para este escopo.

**D4 — Fundo externo cinza claro (`#f4f4f5`), card branco**
Separar fundo de página do card cria profundidade visual sutil sem usar sombra pesada. Alinha com o padrão comum em telas de auth (ex: GitHub, Vercel).

## Risks / Trade-offs

- **Geist Sans via CDN** → Se o usuário estiver offline ou o Google Fonts estiver bloqueado, o fallback é `system-ui, sans-serif`. Risco baixo para o contexto de uso (OAuth web).
- **Cor extraída do logo** → O `#1a2d4a` é uma estimativa visual do navy do logo. Se o time definir um hex oficial diferente, basta atualizar `--vanep-navy` em um único lugar.
