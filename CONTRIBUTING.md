# Contributing

Thanks for your interest in contributing! This document explains how to get set up
and what to expect from the process.

## Requirements

- Java 21+
- Maven 3.9+
- Docker (Testcontainers runs PostgreSQL for integration tests)

## Building

```bash
mvn clean verify
```

The build runs all checks:

- **Tests** — unit tests and Testcontainers-based integration tests
- **Spotless** — code format (Palantir Java Format); fix violations with `mvn spotless:apply`
- **JaCoCo** — minimum 85% line coverage per library module
- **Maven Enforcer** — environment and dependency rules
- **Javadoc** — must build without errors

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Make your change; add or update tests for any behavior change.
3. Run `mvn clean verify` locally — CI runs the same checks.
4. Open a pull request with a clear description of the motivation and the change.

Keep pull requests focused: one logical change per PR is much easier to review.

By participating, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting bugs and requesting features

Use the [issue templates](https://github.com/KHolodilin/spring-boot-state-machine-starter/issues/new/choose).
For bugs, include the library version, Spring Boot version, and a minimal reproduction
if possible.

## Code style

Formatting is fully automated — run `mvn spotless:apply` before committing.
A few conventions beyond formatting:

- Javadoc on public API explains behavior and contracts, not implementation details.
- Comments explain *why*, not *what*.
- Persistence and queue implementations stay in their own modules (`state-machine-persistence-jdbc`, `state-machine-queue-memory`).
