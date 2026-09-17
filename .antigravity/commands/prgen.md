# PR Generator (prgen)

Triggered as `/prgen #<numero>`, where `<numero>` is the number of a GitHub issue (e.g. `/prgen #34`). If no `#<numero>` is given, ask for it before doing anything else.

## Instructions

Follow this sequence exactly, in order, without skipping steps:

1. **Read the issue.** Run `gh issue view <numero>` to understand what it asks for. Compare it against the actual pending changes in the working tree (`git status` / `git diff`) to confirm the work matches the issue before proceeding.

2. **Pick the commit/branch type** from the nature of the change already made: `feat`, `fix`, `chore`, `ui`, `refactor`, `test`, `docs`, etc. — whichever the project already uses for this kind of change (see `git log --oneline` for precedent).

3. **Run the validation gates, in this exact order, before any commit:**
   - `make lint` — if it fails, run `make lint-fix` and re-run `make lint` until clean.
   - `make test`
   - `make build`

   Stop and fix the underlying issue if any gate fails. Never commit or push on a red gate, and never use `--no-verify` to bypass a hook.

4. **Create a new branch** off the current HEAD, named:

   ```
   tipo(N-<numero>)/slug-curto-em-ingles
   ```

   e.g. `feat(N-34)/client-rating-crud`. Git does not allow `:` or spaces in ref names, so the branch uses `/` where the commit message uses `: ` — same scope, different separator.

5. **Stage and commit** the pending changes with the message:

   ```
   tipo(N-<numero>): mensagem em pt-BR descrevendo o que mudou
   ```

   Commit messages are written in pt-BR (constitution rule 47). Never use `git commit --amend` unless explicitly asked.

6. **Push the branch:** `git push -u origin <branch>`.

7. **Open the PR** with `gh pr create`:
   - Title: same as the commit message (`tipo(N-<numero>): mensagem`).
   - Body in pt-BR, summarizing what changed and why, stating that lint/test/build passed locally, and ending with the literal line `This PR closes #<numero>` — GitHub's auto-close keywords (`closes`/`fixes`/`resolves`) only work in English, so that line stays in English even though the rest of the body is pt-BR.
   - After creating the PR, verify the link actually took: `gh pr view <pr-number> --json closingIssuesReferences` should list issue `<numero>`.

## Rules

- Never skip step 3, and never reorder it after the commit/push.
- Never force-push or use `--no-verify`.
- Follow `constitution.md` for commit message language (pt-BR), file/identifier language (English), and any other applicable convention.
- If the working tree has nothing to commit, say so instead of creating an empty commit or an empty PR.
