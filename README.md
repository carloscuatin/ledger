# ledger

Wallet ledger for multi-tenant fintechs: double-entry journal, two-phase transfers, daily close with
Spring Batch and OAuth2 with the FAPI 2.0 profile. A learning project for Java and Spring Boot.

Design and decisions: [`docs/architecture.md`](docs/architecture.md). Diagrams (C4):
[`docs/diagrams/`](docs/diagrams/).

## Requirements

- JDK 25 (`brew install openjdk@25`, then point `JAVA_HOME` at `/opt/homebrew/opt/openjdk@25`)
- Maven comes with the wrapper (`./mvnw`); no global install needed

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
| `application` | Use cases and the ports they need | `domain`, Spring's `@Transactional` |
| `adapters.in` / `adapters.out` | REST, JPA, messaging, schedulers | anything |

`LayersTest` (ArchUnit) and `ModularityTest` (Spring Modulith) fail the build when a boundary is
crossed.

## Gates

```sh
./mvnw verify            # compile, tests, ArchUnit, Modulith verification, Spotless check
./mvnw spotless:apply    # format (palantir-java-format)
```

CI runs `./mvnw -B verify` on every pull request and on `main`.
