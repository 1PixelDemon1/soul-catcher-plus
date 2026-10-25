# Repository instructions

## Target

Soul Catcher Plus currently targets Minecraft 1.20.1, Forge 47.4.28 and Java 17.
Follow the installed `minecraft-modding` skill when changing mod code.

## Git branches

- `develop` is the default working branch for ongoing development.
- Create optional `feature/<description>` or `fix/<description>` branches from `develop`.
- `main` is the release line. Promote tested milestones from `develop` into `main`
  through a merge that preserves development history only when the user explicitly
  requests a release. Ordinary commits remain on development branches.
- Both branches begin at the initial 0.1.0 prototype snapshot. That snapshot is a
  development baseline, not a declaration of a stable 1.0 release.
- Do not rewrite shared history, force-push, or replace existing release tags.
- Keep changes local unless the user requests a remote operation.

## Code-quality subagent

The user explicitly requested an ongoing code-quality reviewer. Reuse
`code_quality_reviewer` when available; otherwise create a replacement with the
same role. After substantive mod-code changes, ask it to review the relevant diff
and nearby code before considering the work complete.

- Prioritize readability, maintainability, clear responsibilities, and simple
  control flow. Identify duplicated rules, scattered state, magic constants,
  brittle coupling, and places that make future mob integrations harder.
- Suggest the smallest useful simplification. Explain the concrete cost of a
  problem and cite its file and line. Separate correctness bugs from optional
  maintainability improvements; do not produce a style-only checklist.
- Prefer repository and version-matched API evidence over speculation. Respect
  server/client separation, saved-data compatibility, and native mob behavior.
- Default to read-only review. Do not rewrite code, change public APIs, add
  abstractions/dependencies, or broaden the task without coordination from the
  primary agent. The primary agent owns implementation decisions and Git writes.
- Follow the user's standing validation preference: no builds, compilation,
  tests, game launches, or UI automation unless the user resumes such checks.
  A source review is not proof that a feature builds or works in-game.
- Report actionable findings with priority and a short recommendation, or state
  that no actionable issues were found. Do not manufacture findings.

## Releases and release-writing subagent

Create releases only when the user explicitly asks. An ordinary feature, bugfix,
commit, or request to continue development is not a release request. Do not bump
versions, promote changes to `main`, create release tags, write new release-note
documents, or publish releases automatically after changes.

The `release_writer` subagent is activated only for an explicitly requested
release. Do not invoke it for ordinary change descriptions or commit messages.
The primary agent can write routine English commit messages and, when helpful,
maintain unreleased notes without activating the release writer.

When the user requests a release, reuse `release_writer` if available; otherwise
create a replacement with the same role. Delegate the following release work:

1. Inspect the actual diff, version, and validation results.
2. Write release descriptions, changelog entries, and release notes in English.
   User-facing conversation may remain in Russian.
3. Update `CHANGELOG.md` and, for a release, `docs/releases/<version>.md`.
4. Describe actual behavior, dependencies, validation, and material limitations.
   Do not claim tests ran if they did not, or promise universal mod compatibility.
5. Return any suggested release commit message to the primary agent. Avoid concurrent
   Git mutations: the primary agent coordinates commits, merges, and tag creation.

## Major release tags

For each explicitly requested and completed major release (`1.0.0`, `2.0.0`, etc.), delegate creation of an
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
