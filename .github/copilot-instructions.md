# GitHub Copilot Code Review Instructions

## Review Philosophy
- Follow the repository's existing code patterns and conventions.
- Only comment when you have high confidence that an issue exists.
- Be concise and actionable.
- Focus on correctness, safety, and maintainability issues over observations.
- When reviewing text, only comment on clarity issues if the text is genuinely confusing or could lead to errors.

## Priority Areas

### Security & Safety
- Command injection risks.
- Path traversal vulnerabilities.
- Credential exposure or hardcoded secrets.
- Missing validation on external input.
- Error handling that could leak sensitive information.

### Correctness Issues
- Logic errors that can cause incorrect behavior or exceptions.
- Race conditions in async or concurrent code.
- Resource leaks.
- Off-by-one or boundary-condition bugs.
- Incorrect error propagation.

### Architecture & Patterns
- Code that violates established patterns in this repository.
- Inconsistent use of libraries or frameworks.
- Missing error handling.
- Inconsistent naming of methods, variables, or classes.
- Comments that merely restate obvious code behavior.

## Low-Value Feedback To Skip

Do not comment on:
- Style or formatting issues already enforced by CI.
- Test failures that CI will report directly.
- Requests to add comments to self-documenting code.
- Refactoring ideas unless they address a concrete bug or maintenance risk.
- Multiple unrelated issues in one comment.

## Response Format

When you identify an issue:
1. State the problem.
2. Explain why it matters if that is not already obvious.
3. Suggest a specific fix.

## When To Stay Silent

If you are not reasonably certain that something is an issue, do not comment.
