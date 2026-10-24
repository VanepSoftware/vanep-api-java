# PR Generator (prgen)

Triggered as `/prgen <VAN-N>`, where `<VAN-N>` is the key of a Genwork task (board prefix + task number, e.g. `/prgen VAN-34`). If no key is given, ask for it before doing anything else.

## Instructions

Follow this sequence exactly, in order, without skipping steps:

1. **Read the task.** Fetch `<VAN-N>` through the Genwork MCP server (`genwork`, at `https://genwork.vanep.com.br/mcp`) to understand what it asks for. If the server is not connected or not signed in, stop and ask me to connect it (or to paste the task). Compare it against the actual pending changes in the working tree (`git status` / `git diff`) to confirm the work matches the task before proceeding.

2. **Pick the commit/branch type** from the nature of the change already made: `feat`, `fix`, `chore`, `ui`, `refactor`, `test`, `docs`, etc. — whichever the project already uses for this kind of change (see `git log --oneline` for precedent).

3. **Run the validation gates, in this exact order, before any commit:**
   - `make lint` — if it fails, run `make lint-fix` and re-run `make lint` until clean.
   - `make test`
   - `make build`

   Stop and fix the underlying problem if any gate fails. Never commit or push on a red gate, and never use `--no-verify` to bypass a hook.

4. **Create a new branch** off the current HEAD, named:

   ```
   tipo(<VAN-N>)/slug-curto-em-ingles
   ```

   e.g. `feat(VAN-34)/client-rating-crud`. Git does not allow `:` or spaces in ref names, so the branch uses `/` where the commit message uses `: ` — same scope, different separator.

5. **Stage and commit** the pending changes with the message:

   ```
   tipo(<VAN-N>): mensagem em pt-BR descrevendo o que mudou
   ```

   Commit messages are written in pt-BR (constitution rule 47). Never use `git commit --amend` unless explicitly asked.

6. **Push the branch:** `git push -u origin <branch>`.

7. **Open the PR** with `gh pr create`:
   - Title: same as the commit message (`tipo(<VAN-N>): mensagem`). Genwork links the PR to the task and moves it on the board because the title and the branch contain `<VAN-N>`; there is no `closes #N` line.
   - Body in pt-BR, summarizing what changed and why, and stating that lint/test/build passed locally.

## Rules

- Never skip step 3, and never reorder it after the commit/push.
- Never force-push or use `--no-verify`.
- Follow `constitution.md` for commit message language (pt-BR), file/identifier language (English), and any other applicable convention.
- If the working tree has nothing to commit, say so instead of creating an empty commit or an empty PR.
