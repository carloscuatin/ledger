# ledger

Wallet ledger for multi-tenant fintechs: double-entry journal, two-phase transfers, daily close with
Spring Batch and OAuth2 with the FAPI 2.0 profile. A learning project for Java and Spring Boot.

Design and decisions: [`docs/architecture.md`](docs/architecture.md). Decision records per phase:
[`docs/adr/`](docs/adr/). Diagrams (C4): [`docs/diagrams/`](docs/diagrams/).

## Requirements

- [SDKMAN](https://sdkman.io) with `sdkman_auto_env=true` in `~/.sdkman/etc/config`. `.sdkmanrc`
  pins the JDK (Temurin 25); run `sdk env install` once in the repository and every later `cd` into
  it switches to that JDK. No environment variable to export.
- Maven comes with the wrapper (`./mvnw`); no global install needed.
- Docker, running, for `./mvnw verify` and for the local database.
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
| `adapters.in` | REST, messaging, schedulers | anything |
| `adapters.out` | Implementations of the ports: JPA persistence, the tenant on each transaction, the database role check at startup | anything |
| `adapters.config` | Spring configuration: typed properties and the `@Bean` methods that build use cases | anything |

`LayersTest` (ArchUnit) and `ModularityTest` (Spring Modulith) fail the build when a boundary is
crossed.

## Commands

Everything runs from the repository root; the root POM is the parent and aggregator of every module.

```sh
./mvnw test                  # enforcer, NullAway, unit tests, ArchUnit, Modulith; no Docker
./mvnw clean verify          # the gate: all of that plus the database tests and Spotless; needs Docker
./mvnw spotless:apply        # format Java with the shared Eclipse profile
./mvnw -Pmutation test-compile pitest:mutationCoverage   # mutation testing of domain and use cases
./mvnw -pl ledger-app spring-boot:run                    # run the application; needs the database settings below
```

- Tests named `*Test` run in `test`. Tests named `*IT` run in `verify` against a real PostgreSQL that
  Testcontainers starts in Docker; with Docker off, `verify` fails instead of skipping them. The
  mutation command needs no Docker.
- The mutation report lands in `ledger-app/target/pit-reports/index.html`. The threshold is 100 %: a
  surviving mutant is a missing test, and one no test can kill is removed by changing the shape of
  the code, never by excluding it or lowering the threshold.
- The enforcer fails in `validate` with a JDK older than 25; run `sdk env` in the repository root.
- Add `-o` to work offline from `~/.m2`; leave it out only when a dependency or plugin changes.

## Configuration

| Setting | Environment variable | Default | Meaning |
|---|---|---|---|
| `ledger.currencies` | `LEDGER_CURRENCIES` | `COP` | Currencies this deployment operates, comma separated |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | none | JDBC URL of the PostgreSQL database |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | none | Database role of this process |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | none | Password of that role |

Settings are validated at startup: a missing value, an unknown code or a currency without a scale
stops the application and the report names the setting. A command line argument wins over an
environment variable, and an environment variable wins over the packaged `application.properties`.

Two processes use the database, with the same three settings and a role each, in this order:

1. `SPRING_PROFILES_ACTIVE=migrate`, with the role that owns the schema: applies the Flyway
   migrations in `ledger-app/src/main/resources/db/migration` and exits. An administrator creates
   both roles before the first migration.
2. No profile, with the application role.

The application does not start without connection settings, against a schema that does not match its
mapping (a database not yet migrated included), or with a role that row level security does not
apply to: a superuser, a role with `BYPASSRLS`, or the owner of a table in the `public` schema or a
member of that owner. The report names the cause.

The application has no web server, so when every check passes nothing keeps the process alive: it
starts and exits by itself with status 0. That is a successful run, not a crash.

### Local Postgres

From the repository root. `roles.sql` creates both roles when the container initializes; its
passwords and the administrator's exist only for this local database.

```sh
./mvnw -DskipTests package   # build the executable jar
docker run -d --name ledger-postgres \
  -e POSTGRES_DB=ledger -e POSTGRES_PASSWORD=test-only-admin \
  -p 127.0.0.1:55440:5432 \
  -v "$PWD/ledger-app/src/test/resources/db/roles.sql:/docker-entrypoint-initdb.d/roles.sql:ro" \
  postgres:16.15-alpine
# By TCP: while the container initializes, its temporary server listens only on the Unix socket.
until docker exec ledger-postgres pg_isready -h 127.0.0.1 -U postgres; do sleep 1; done
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55440/ledger
SPRING_PROFILES_ACTIVE=migrate \
  SPRING_DATASOURCE_USERNAME=ledger_migrator SPRING_DATASOURCE_PASSWORD=test-only-migrator \
  java -jar ledger-app/target/ledger-app-0.0.1-SNAPSHOT.jar
export SPRING_DATASOURCE_USERNAME=ledger_app SPRING_DATASOURCE_PASSWORD=test-only-app
java -jar ledger-app/target/ledger-app-0.0.1-SNAPSHOT.jar
```

With those variables exported:

```sh
./mvnw -pl ledger-app spring-boot:run
./mvnw -pl ledger-app spring-boot:run -Dspring-boot.run.arguments=--debug  # what Boot auto-configured and why
LEDGER_CURRENCIES=COP,USD java -jar ledger-app/target/ledger-app-0.0.1-SNAPSHOT.jar
```

To remove the container and its data:

```sh
docker rm -f -v ledger-postgres
```

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
| `verify` | pull requests and `main` | `./mvnw -B verify`, whose database tests use the runner's Docker; uploads the test reports when it fails |
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
