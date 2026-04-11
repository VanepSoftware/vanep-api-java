# PR Description Generator

Please generate a comprehensive Pull Request description that follows the structure defined in `docs/pull_request_template.md`.

## Instructions

1. **Analyze the changes**: Compare the current branch against the `main` branch to understand what was modified, added, or removed. Ignore merge commits, use git diff to see what are the real changes.

2. **Generate the PR title**: Format as `[VEN-XXX] Feature | Change Request | Bug` (ask me for the ticket number if not obvious from commit messages).

3. **Write a clear description**: 
   - Explain what the PR does and why
   - Highlight the main changes and their purpose
   - If UI changes were made, mention that screenshots should be included
   - Keep it concise but informative

4. **Create test instructions**:
   - List step-by-step instructions on how to verify the changes
   - Include any necessary setup or configuration
   - Mention which features/flows should be tested

5. **Review the checklist**:
   - Go through each item in the template's checklist
   - Flag any items that might need special attention based on the changes made
   - Highlight if feature toggles, i18n strings, or accessibility considerations are relevant

Please provide the complete PR description in markdown block, ready to be used in GitHub.

### [VAN-116]: Feat Update documentation

**Why:** Align docs and defaults with Med+ API (pt_BR, MySQL, Sail) and standardize dev workflow via Makefile.

**How:**
- **README.md**: Replaced Laravel boilerplate with Med+ Checklists API readme; requirements (PHP 8.4+, Composer, Docker/Colima); install steps (env copy, composer, `make up`, `make migrate`); table of Makefile commands; optional Sail alias.
- **Makefile**: Added Sail targets — `up`, `down`, `nuke`, `restart`, `shell`, `migrate`, `migrate-fresh`, `artisan`, `composer`, `test`.
- **.env.example**: `APP_LOCALE`/`APP_FALLBACK_LOCALE`/`APP_FAKER_LOCALE` set to `pt_BR`; default DB to MySQL (`DB_CONNECTION=mysql`, `DB_HOST=mariadb`, etc.); `EXPIRE_TOKEN_MINUTES=15`, `EXPIRE_REFRESH_TOKEN_MONTHS=3`, `EXPIRE_PERSONAL_ACCESS_TOKEN_MONTHS=6`.

**How to test:**
1. Clone branch, `cp .env.example .env`, `composer install`, `make up`, `make migrate`.
2. Confirm app responds and locale is pt_BR.
3. Run `make test`.

**Checklist:**
- [ ] I ran the app and migrations locally.
- [ ] I reviewed the changes.
- [ ] Tests pass locally.