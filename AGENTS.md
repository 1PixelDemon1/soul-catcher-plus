# Repository instructions

## Target

Primary development targets Minecraft 1.16.5, Forge 36.2.42 and a Java 8 runtime.
Gradle and javac run on Java 17; Jabel 1.0.0 compiles modern syntax to Java 8
with `--release 8`. Jabel is compile-only and must never be bundled into the mod.
Use `develop` for ongoing 1.16.5 work. `forge/1.19.2` and `forge/1.20.1`
maintain their version-specific ports. `archive/neoforge-1.20.4` is a preserved
experimental port, outside the current release.
For the next Minecraft 1.20.1 release, retain Forge **47.4.28** as the build
target and minimum Forge version in mod metadata, as requested by the user.
`main` retains release history and the public project overview. Update it for
requested releases or explicitly requested publication/documentation preparation.
Verify old APIs against the actual 1.16.5 mapped JAR and Forge sources. The class
names are legacy MCP names with official member mappings; modern examples differ.
Follow the installed `minecraft-modding` skill when changing mod code.

## Git branches

- `develop` is the default working branch for Minecraft 1.16.5 development.
- `main` holds release source and the GitHub-facing project overview.
- `forge/1.19.2` and `forge/1.20.1` are maintained version-specific port branches.
- `archive/neoforge-1.20.4` preserves the experimental port; do not develop there
  without an explicit request to resume that platform.
- Create optional `feature/<description>` or `fix/<description>` branches from `develop`.
- Promote gameplay changes to `main` through a history-preserving merge only for
  an explicitly requested release. Publication/documentation preparation may
  synchronize README and repository guidance without promoting gameplay changes.
- The former `codex/forge-1.16.5` bootstrap branch was fully merged and removed;
  its commits remain in development/release history.
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
- Keep the reviewer read-only: the primary agent compiles and launches the
  development client for the user's manual checks after each mod refinement.
  Do not run automated tests, GameTests or UI automation unless requested.
  A source review is not proof that a feature builds or works in-game.
- Report actionable findings with priority and a short recommendation, or state
  that no actionable issues were found. Do not manufacture findings.

## Releases and release-writing subagent

Create releases only when the user explicitly asks. An ordinary feature, bugfix,
commit, or request to continue development is not a release request. Do not bump
versions, promote gameplay changes to `main`, create release tags, write new release-note
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

Existing release tags are immutable. Repository housekeeping does not create a
new release. Do not invent version bumps or publish tags/remotes without a release
to publish.

## Validation

The user handles manual gameplay testing. After each mod refinement, compile and
launch the current development client for them, and confirm startup from the log.
For the primary 1.16.5 development branch, use:

`.\scripts\gradle.ps1 runClient -PgameTests -PwithTwilightForest -PwithAquamirae`

`-PgameTests` makes the QA command available; launching the client is not a request
to run GameTests. Leave the game open for the user. Do not force-kill a running
client or discard unsaved worlds. Automated tests, GameTest servers and UI
automation remain opt-in. Distinguish successful compilation/startup from actual
gameplay verification; do not present earlier checks as validation of new work.

Run checks appropriate to the change. Documentation-only and Git-organization
changes do not require another Minecraft build.

- Package without QA sources or automated tests: `.\scripts\gradle.ps1 build -x test`
- Prepare the private playtest archive: `.\scripts\gradle.ps1 playtestZip -PwithTwilightForest -PwithAquamirae`

Minecraft 1.16.5 has no supported GameTest task here. `-PgameTests` is retained as
a compatibility flag for the manual QA command from `src/qa/java` only. The old
`src/gametest`/`src/test` sources belong to the newer branch and are not validated
for this port. Do not enable or run them without a separate port/testing request.
Build output uses `build/1.16.5`; worlds use `run-client/1.16.5` and `run/1.16.5`.
Archive original SRG dependencies from `playtestMods`, never the remapped dev JARs.
Do not delete artifacts or open worlds belonging to the other version branches.

Keep build output, downloaded tools, Gradle caches, logs, and local worlds ignored.


## Development isolation

Keep core builds, development runs and playtest archives independent of external
integration projects. Do not load compatibility addon sources, build scripts or
JARs during routine core development. Test external integrations separately only
when the user requests it. Generic public extension APIs remain in the core;
do not embed mod-specific behavior corrections here.

## Forge 26.3 port

`forge/26.3` targets Minecraft 26.3, Forge 66.1.0 and Java 25 with the official
ForgeGradle 7 / Gradle 9.7.1 layout. The game is unobfuscated: do not add legacy
mappings, Jabel, fg.deobf or reobfJar on that branch. Use its Java 25 bootstrap and
Gradle scripts. Build output is `build/26.3`; keep its profiles separate from other
ports. The client uses queued render-state submission and SDL input. Verify those
APIs against the actual Forge 66.1.0 sources rather than copying 1.20.1 callbacks.
Published Forge Patchouli and creature-integration builds have not been verified
for 26.3. Do not claim their compatibility based only on core/addon startup.

Authored model sources are maintained on develop. Port their generated geometry
with the platform-specific renderer API; legacy exporters are not a substitute
for reviewing a newer branch's native rendering code.
