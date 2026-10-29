# ledger

Wallet ledger for multi-tenant fintechs: double-entry journal, two-phase transfers, daily close with
Spring Batch and OAuth2 with the FAPI 2.0 profile. A learning project for Java and Spring Boot.

Design and decisions: [`docs/architecture.md`](docs/architecture.md). Diagrams (C4):
[`docs/diagrams/`](docs/diagrams/).

## Requirements

- [SDKMAN](https://sdkman.io) with `sdkman_auto_env=true` in `~/.sdkman/etc/config`. `.sdkmanrc`
  pins the JDK (Temurin 25); run `sdk env install` once in the repository and every later `cd` into
  it switches to that JDK. No environment variable to export.
- Maven comes with the wrapper (`./mvnw`); no global install needed.
- Git hooks, once per clone: `git config core.hooksPath .githooks` (it also covers every worktree).

## Layout

| Path | What |
|---|---|
| `ledger-app/` | Spring Boot 4 + Spring Modulith application (modular monolith) |
| `docs/` | Architecture decisions and diagrams |

The other modules in the plan (`auth-server`, `console-bff`, `console-web`, `libs/dpop`,
`contracts/`, `infra/terraform/`) are added when their step arrives.

Inside `ledger-app`, each Modulith module (`com.carloscuatin.ledgerapp.<module>`) is hexagonal:

| Package | Holds | May depend on |
|---|---|---|
| `domain` | Entities, value objects, domain errors | plain Java only |
| `application` | Use cases and the ports they need | `domain`, Spring's `@Transactional`, JSpecify annotations |
| `adapters.in` / `adapters.out` | REST, JPA, messaging, schedulers | anything |

`LayersTest` (ArchUnit) and `ModularityTest` (Spring Modulith) fail the build when a boundary is
crossed.

## Commands

Everything runs from the repository root; the root POM is the parent and aggregator of every module.

```sh
./mvnw clean verify          # the gate: enforcer, NullAway, tests, ArchUnit, Modulith, Spotless
./mvnw spotless:apply        # format Java with the shared Eclipse profile
./mvnw -Pmutation test-compile pitest:mutationCoverage   # mutation testing of every domain package
./mvnw -pl ledger-app spring-boot:run                    # run the application
```

- The mutation report lands in `ledger-app/target/pit-reports/index.html`. The threshold is 100 %: a
  surviving mutant is a missing test. An equivalent mutant is excluded by method with
  `excludedMethods`, never by lowering the threshold.
- The enforcer fails in `validate` with a JDK older than 25; run `sdk env` in the repository root.

## Nullness

Every package under `main` has a `package-info.java` annotated with JSpecify's `@NullMarked`: types
are non-null unless marked `@Nullable`. NullAway (inside Error Prone) checks that contract when
production code compiles and fails the build on an undeclared `null`. Tests are not checked, so they
can pass `null` to prove a boundary rejects it, and `requireNonNull` stays at the boundaries for
values from unchecked callers. `LayersTest` fails when a package is missing its `@NullMarked`
`package-info.java`, which would otherwise leave it out of NullAway silently.

Error Prone and NullAway run as annotation processors of the parent's `default-compile` execution.
A new annotation processor (such as `spring-boot-configuration-processor`) goes in that execution's
`annotationProcessorPaths`: one declared in a module is ignored.

## Git hooks

- `pre-commit` runs `./mvnw -q -B validate spotless:check` (a couple of seconds). On a format
  violation, run `./mvnw spotless:apply`, stage the result and commit again. When the shell
  environment is missing (a commit from VS Code opened from the Dock) it uses the JDK pinned in
  `.sdkmanrc`.
- `commit-msg` requires a Conventional Commit header of at most 100 characters, such as
  `feat(ledger): reject transfers between different currencies`. Merge, revert, `fixup!` and
  `squash!` messages pass as git writes them.
- Never skip them with `--no-verify`; fix the cause instead.

## CI

| Job | Runs on | Checks |
|---|---|---|
| `verify` | pull requests and `main` | `./mvnw -B verify`; uploads the test reports when it fails |
| `mutation` | pull requests | the mutation command above; uploads the PIT report when it fails |
| `trivy` | pull requests, `main` and every Monday | high and critical dependency vulnerabilities that have a fixed version |

A new push to a pull request cancels its previous run; runs on `main` are never cancelled. When
`trivy` fails, upgrade the dependency, or pin the fixed version in the parent's
`dependencyManagement`. Dependencies are upgraded by hand, without Dependabot for now.

## VS Code

- Install the recommended extensions (`.vscode/extensions.json`), including EditorConfig: VS Code does
  not read `.editorconfig` without it. The Oracle Java extension conflicts with the Red Hat one and is
  listed as unwanted.
- `.vscode/settings.json` holds only project settings: the shared formatter profile
  (`eclipse-formatter.xml`, the same file Spotless uses), formatting and organizing imports on save,
  and the import order the gate expects. Personal settings belong in your user settings.
- Null hints in the editor come from SonarLint, which understands JSpecify. The Eclipse compiler's
  null analysis is disabled because it reports false positives on unannotated libraries. NullAway in
  the build is the gate.
- VS Code compiles into `ledger-app/target-ide/` (the `ide` Maven profile, active only inside
  m2e-based IDEs), so its background builds never leave stale classes in Maven's `target/`.
