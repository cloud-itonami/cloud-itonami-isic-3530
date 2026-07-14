# Contributing

We welcome contributions to this thermal supply actor blueprint. Please follow these guidelines:

## Before You Start

1. Read this repository's README.md and GOVERNANCE.md to understand the scope and liability model.
2. Familiarize yourself with the existing architecture (steam.governor, steam.store, steam.phase).
3. For safety-critical changes, open an issue first to discuss with maintainers.

## Contributions We Accept

- Improvements to documentation and operator guides
- New jurisdiction requirements (with official spec-basis citations only)
- Bug fixes and performance improvements
- Test coverage enhancements
- Portability improvements (`.cljc` code that works across JVM/ClojureScript/GraalVM)

## Contributions We Do Not Accept

- Hardcoded jurisdiction-specific policies without a source citation
- Automatic approval of high-stakes actuation (provision or suspension)
- Removal of protected-recipient gates or safety checks
- Speculation about real-world thermal systems without expert review

## Process

1. Fork the repository
2. Create a feature branch
3. Make your changes following existing code style
4. Write tests for new functionality
5. Ensure all tests pass (`clj -M:test`)
6. Ensure linting passes (`clj -M:lint`)
7. Submit a pull request with a clear description

## Code Style

- Use `.cljc` files for all source code (portable across runtimes)
- Include docstrings for all public functions
- Keep functions small and testable
- Use clear, descriptive variable names
- Follow the style established in existing modules

## Testing

- Write tests for all new features
- Ensure tests are isolated and deterministic
- Use the MemStore for demo and test data
- Test both happy paths and error conditions

## License

By contributing, you agree that your contributions will be licensed under AGPL-3.0-or-later.
