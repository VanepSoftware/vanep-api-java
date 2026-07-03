## 1. Paleta de cores

- [x] 1.1 Definir variáveis CSS semânticas em `:root` no `auth.css` (brand, superfície, tipografia, feedback)
- [x] 1.2 Substituir `--bg` de `#000000` para `#f4f4f5` e `--card` para `#ffffff`
- [x] 1.3 Substituir `--accent` (laranja `#ff4200`) por `--vanep-navy` (`#1a2d4a`)
- [x] 1.4 Atualizar `--text` para `#0a0a0a` e `--muted` para `#71717a`
- [x] 1.5 Atualizar cores de feedback (erro/sucesso) para paleta clara

## 2. Tipografia

- [x] 2.1 Trocar import do Google Fonts de Figtree para Geist Sans em `login.html`
- [x] 2.2 Trocar import em `signup-choose.html`
- [x] 2.3 Trocar import em `signup-client.html`
- [x] 2.4 Trocar import em `signup-driver.html`
- [x] 2.5 Trocar import em `signup-complete.html`
- [x] 2.6 Trocar import em `forgot-password.html`
- [x] 2.7 Trocar import em `reset-password.html`
- [x] 2.8 Trocar import em `verify-email.html`
- [x] 2.9 Atualizar `font-family` no `body` do `auth.css` para `"Geist"`

## 3. Componentes visuais

- [x] 3.1 Adicionar `box-shadow` sutil ao `.login-card` para separar do fundo
- [x] 3.2 Aplicar `color: var(--vanep-navy)` ao `.brand` (título "Vanep")
- [x] 3.3 Estilizar `select` com as mesmas regras do `input` (borda, border-radius, fonte)
- [x] 3.4 Atualizar `.btn-submit` para usar `display: block` (compatibilidade com `<a>` e `<button>`)
- [x] 3.5 Ajustar `.btn-google` para usar `var(--card)` e `var(--bg)` no hover

## 4. Validação

- [x] 4.1 Verificar responsividade em 375px (mobile) — sem overflow
- [x] 4.2 Verificar responsividade em 768px (tablet)
- [x] 4.3 Verificar responsividade em 1280px (desktop)
- [ ] 4.4 Validar visual no Chrome, Firefox e Edge
- [ ] 4.5 Confirmar que CI passa (zero Java alterado — Spotless e JaCoCo não impactados)
