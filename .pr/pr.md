# PR Description Generator

Please generate a comprehensive Pull Request description that follows the structure defined in `docs/pull_request_template.md`.

## Instructions

1. **Analyze the changes**: Compare the current branch against the `main` branch to understand what was modified, added, or removed. Ignore merge commits, use git diff to see what are the real changes.

2. **Generate the PR title**: Format as `[VAN-XXX] Feature | Change Request | Bug` (ask me for the ticket number if not obvious from commit messages).

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
   - Highlight if feature toggles, strings, or accessibility considerations are relevant

Please provide the complete PR description in markdown block, ready to be used in GitHub.

**Checklist:**
- [ ] I ran the app and migrations locally.
- [ ] I reviewed the changes.
- [ ] Tests pass locally.