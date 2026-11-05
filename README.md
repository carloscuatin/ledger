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
| `contracts/openapi/` | The API contract, `ledger-v1.yaml`: a folder, not a Maven module |
| `docs/` | Architecture decisions and diagrams |

The other modules in the plan (`auth-server`, `console-bff`, `console-web`, `libs/dpop`,
`infra/terraform/`) are added when their step arrives.

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

## API contract

`contracts/openapi/ledger-v1.yaml` is the source of the API. Every build generates from it the
interfaces the controllers implement and the models they exchange, in
`ledger-app/target/generated-sources/openapi`. They are never committed, so a contract change shows
in a pull request as a change to that file. `ContractIT` holds the responses of the running API to
the contract, and compares the routes the application maps with the operations it documents.

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
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | none | JDBC URL of the PostgreSQL database, without credentials |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | none | Database role of this process |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | none | Password of that role |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | none | The process this run is: `migrate`, `api`, or none |
| `server.port` | `SERVER_PORT` | `8080` | Port of the `api` profile, on every network interface |

Settings are validated at startup: a missing value, an unknown code or a currency without a scale
stops the application and the report names the setting. A command line argument wins over an
environment variable, and an environment variable wins over the packaged `application.properties`.

Credentials never go in the JDBC URL, because Hibernate prints the URL in the log at `INFO`,
parameters included. Use the username and password settings, which it does not print.

The profile decides what a process is. All of them take the same three database settings, with a
role each, and `migrate` runs first:

| Profile | Database role | What it does | Port | How it ends |
|---|---|---|---|---|
| `migrate` | The one that owns the schema | Applies the Flyway migrations in `ledger-app/src/main/resources/db/migration` | None | By itself, with status 0 |
| None | `ledger_app` | Checks its settings, its role and its schema | None | By itself, with status 0 |
| `api` | `ledger_app` | The same checks, then serves the API | `server.port` | Stays alive until stopped; `SIGTERM` shuts it down, and the JVM reports 143 |

Exiting with status 0 is a successful run of the first two, not a crash: they have no web server, so
when every check passes nothing keeps the process alive. `api` and `migrate` together do not start:
the process exits with status 1, naming both profiles, before it connects to the database.
A process made a server by hand, with `spring.main.web-application-type=servlet` and without `api`,
does not start either, with or without `migrate`: it exits with status 1, saying it would open a port
without the security filter chain, before it connects to the database.

An administrator creates both roles before the first migration. The migration grants access to
`ledger_app` by name, so that is the name of the application role: any other role starts, because
starting reads no rows, and fails on its first access to a table.

There is no way to authenticate yet, so whatever reaches the security filter chain of `api` answers
`401` with an `application/problem+json` body and `WWW-Authenticate: DPoP`, on any route and whatever
the credential. A body is not read to refuse a request. What does not get that far:

| Request | Answer |
|---|---|
| A path parameter (`;`), or a URL the firewall of the chain refuses | `400`, a `bad-request` problem |
| `TRACE` | `405`, a `method-not-allowed` problem |
| `/WEB-INF/*` and `/META-INF/*`, which the server keeps from every client | `404`, a `not-found` problem |
| `OPTIONS *`, which names no route | `200` from the server, with `Allow` and no body |
| What the server cannot take as a request: `%2F` or `%00` in a path, headers over 8 KiB, `CONNECT`, an unknown `Expect`, another HTTP version | `400`, `501`, `417` or `505` from the server, in its short HTML, without its name or version |

The application does not start without connection settings, against a schema that does not match its
mapping (a database not yet migrated included), or with a role that can get past row level security:
a superuser, a role with `BYPASSRLS`, the owner of a table or of a schema that holds a table, or a
member of any of those. Every schema counts except the system ones. So does owning the database,
because the `public` schema belongs to `pg_database_owner`, whose member is the database's owner. The
report names the cause, the process exits with status 1, and `api` never opens its port.

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
java -jar ledger-app/target/ledger-app-0.0.1-SNAPSHOT.jar   # checks everything and exits with status 0
SPRING_PROFILES_ACTIVE=api java -jar ledger-app/target/ledger-app-0.0.1-SNAPSHOT.jar   # serves until stopped
```

From another terminal, while `api` runs:

```sh
curl -i http://127.0.0.1:8080/v1/transfers   # 401, application/problem+json, WWW-Authenticate: DPoP
```

With those variables exported:

```sh
./mvnw -pl ledger-app spring-boot:run
./mvnw -pl ledger-app spring-boot:run -Dspring-boot.run.arguments=--debug  # what Boot auto-configured and why
SPRING_PROFILES_ACTIVE=api SERVER_PORT=8081 ./mvnw -pl ledger-app spring-boot:run
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

The code generated from the contract lies outside that rule: its package,
`com.carloscuatin.ledger.contract.v1`, is outside the application's base package, so Modulith takes
it for no module and `LayersTest` does not look at it. NullAway checks nothing about calling it; the
`@NotNull` and `@Valid` it generates reject a missing value when a request arrives.

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
