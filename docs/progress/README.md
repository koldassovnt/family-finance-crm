# Progress log

Where the project stands, so a new session can pick it up without re-deriving
everything. **The requirements are the intent; this log is what actually
happened.** When they disagree, trust `.claude/requirements/` for what should
be true and this file for what is.

Last updated: **2026-09-19**.

| File | What it covers |
|---|---|
| [state-of-the-project.md](state-of-the-project.md) | What is built, in both repos, and what is left |
| [environment.md](environment.md) | The running stack, credentials, seeded test data |
| [decisions-log.md](decisions-log.md) | Choices made recently and why, including ones a future session might undo by accident |

Related, outside this folder:

- `../running-the-service.md` — how to run it locally and on a server
- `../../.claude/requirements/` — one doc per phase, the source of truth
- `../../README.md` — the short version, and the phase status table

## The two repos

This project is **two separate git repos on one machine**, each worked on by
its own Claude session. Neither is a subdirectory of the other, and "pull the
latest" does not mean the same thing across them.

| | Backend | Frontend |
|---|---|---|
| Path | `~/IdeaProjects/personal/family-finance-crm` | `~/VSCodeProjects/family-finance-crm-front` |
| Remote | `git@github.com:koldassovnt/family-finance-crm.git` | `git@github.com:koldassovnt/family-finance-crm-front.git` |
| Stack | Kotlin, Spring Boot 3.5, Postgres, Flyway | React 19, TypeScript, Vite, TanStack Query, shadcn |
| Spec | `.claude/requirements/*.md` | `.claude/frontend-requirements.md` |
| Progress | this folder | `docs/progress/` in that repo |

The frontend requirements doc lives **only** in the frontend repo. A copy once
sat here and was moved; do not recreate it.

## Working across them

The two sessions coordinate by message, not by editing each other's files.
Useful habits, learned the hard way:

- **Read the other repo, never write to it.** A change arriving in a tree its
  own session did not make costs that session real time working out where it
  came from.
- **Only a pushed commit is durable.** Local state on either side is not
  something the other should build assumptions on.
- **Say what moved, not just what was added.** Reseeding test data
  invalidates figures the other session has already verified and recorded, and
  it will not notice until a previously-checked screen looks wrong.
- **Verify claims against the code or a running instance before sending
  them.** Several API details that "obviously" held turned out not to.
