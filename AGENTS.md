# Repository instructions

## Target

Soul Catcher Plus currently targets Minecraft 1.20.1, Forge 47.4.28 and Java 17.
Follow the installed `minecraft-modding` skill when changing mod code.

## Git branches

- `develop` is the default working branch for ongoing development.
- Create optional `feature/<description>` or `fix/<description>` branches from `develop`.
- `main` is the release line. Promote tested milestones from `develop` into `main`
  through a merge that preserves development history.
- Both branches begin at the initial 0.1.0 prototype snapshot. That snapshot is a
  development baseline, not a declaration of a stable 1.0 release.
- Do not rewrite shared history, force-push, or replace existing release tags.
- Keep changes local unless the user requests a remote operation.

## Release-writing subagent

The user explicitly requested a subagent responsible for change descriptions.
Reuse the `release_writer` subagent when available; otherwise create a replacement
with the same role. Delegate this work after substantive changes:

1. Inspect the actual diff, version, and validation results.
2. Write commit subjects/bodies, changelog entries, and release notes in English.
   User-facing conversation may remain in Russian.
3. Update `CHANGELOG.md` and, for a release, `docs/releases/<version>.md`.
4. Describe actual behavior, dependencies, validation, and material limitations.
   Do not claim tests ran if they did not, or promise universal mod compatibility.
5. Return the suggested commit message to the primary agent. Avoid concurrent
   Git mutations: the primary agent coordinates commits, merges, and tag creation.

## Major release tags

For each completed major release (`1.0.0`, `2.0.0`, etc.), delegate creation of an
annotated `v<major>.0.0` tag to the release-writing subagent. Use an English tag
message that summarizes the release and refers to its release notes.

Create the tag only after the version in `gradle.properties` and release notes
agree, required validation passes, and the release is committed and promoted to
`main`. Inspect existing tags and tag the verified `main` commit explicitly.
Do not tag an uncommitted working tree or an unreleased major-version plan.
Never move or replace an existing release tag; report a mismatch instead.

The current `0.1.0+1.20.1` prototype is not a major release and needs no major tag.
Do not invent version bumps or publish tags/remotes without a release to publish.

## Validation

The user currently handles testing during early development. Until they ask to
resume agent-driven validation, do not run builds, compilation, automated tests,
Minecraft clients/servers, or UI automation merely to verify changes. Source and
API inspection is allowed. Explicitly state when a change has not been built or
tested; do not present the earlier prototype checks as validation of new work.

Run checks appropriate to the change. Documentation-only and Git-organization
changes do not require another Minecraft build.

- Build and data tests: `.\scripts\gradle.ps1 build`
- Base gameplay: `.\scripts\gradle.ps1 runGameTestServer -PgameTests`
- Optional integration: `.\scripts\gradle.ps1 runGameTestServer -PgameTests -PwithMowzies`
- After game tests, package without test sources: `.\scripts\gradle.ps1 clean build`

Keep build output, downloaded tools, Gradle caches, logs, and local worlds ignored.
