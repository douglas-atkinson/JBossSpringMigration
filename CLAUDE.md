# Project Context

This repository is an AI-assisted modernization of the JBoss EAP
kitchensink quickstart.

## Branches

- `master`: untouched legacy JBoss baseline
- `migration-to-springboot`: active modernization work

## Working Rules

- Do not modify `master`.
- Preserve observable behavior unless a change is explicitly approved.
- Add characterization tests before replacing legacy behavior.
- Keep commits focused on one architectural change.
- Run relevant tests before proposing a commit.
- Explain architectural decisions and migration risks.