<!--
Sync Impact Report:
- Version change: Initial → 1.0.0
- Modified principles: N/A (initial version)
- Added sections: All principles and governance (initial constitution)
- Removed sections: N/A
- Templates requiring updates: All templates reviewed and aligned ✅
- Follow-up TODOs: None
-->

# fsr Constitution

## Core Principles

### I. Zero-Dependency Library
All functionality must be implemented without runtime dependencies on external libraries. Malli schemas are used for validation and contracts but only as function metadata (:malli/schema), ensuring Malli is not a runtime dependency. Development and testing dependencies are acceptable but must not be required for library operation. This ensures maximum compatibility and minimal deployment footprint.

### II. Documentation-First Development
Every function, namespace, and data structure must be thoroughly documented with comments that explain purpose, parameters, return values, and usage examples. Documentation must be kept synchronized with code changes - no code changes are complete without corresponding documentation updates. Comments should focus on why and how, not just what.

### III. Malli Schema Contracts (NON-NEGOTIABLE)
All functions must define input/output contracts using Malli schemas attached as :malli/schema metadata. All data structures must have corresponding Malli schemas. Schema validation must be enforced in tests but not at runtime (to maintain zero-dependency requirement). This provides compile-time safety while avoiding runtime overhead.

### IV. Test-First Development
All functionality must be developed using Test-Driven Development with clojure.test. Tests must be written before implementation, must fail initially, then pass after implementation (Red-Green-Refactor cycle). Test coverage must include schema validation using Malli. Property-based testing with clojure.test.check is encouraged for complex logic.

### V. API Stability & Simplicity
Public APIs must remain stable and backward-compatible. Follow semantic versioning strictly. Design APIs to be simple and intuitive - prefer pure functions over stateful operations. Avoid premature abstraction and follow YAGNI principles. Each namespace should have a single, well-defined responsibility.

### VI. Filesystem-Based Architecture
The core architectural principle is mapping URIs to filesystem structure. URIs must correspond directly to Clojure namespace files, with dynamic path parameters supported through `<param>` (single segments) or `<<param>>` (multi-segment with slashes) syntax. This convention ensures predictable routing and maintainable code organization that mirrors web structure.

## Development Standards

All code must follow standard Clojure conventions and idioms. Function names should clearly indicate their purpose and side effects. Prefer immutable data structures and pure functions. Use meaningful error messages and appropriate exception handling. Performance optimizations must be justified and measured.

### Namespace Organization
The library must maintain a three-namespace architecture: `esp1.fsr.core` for URI-to-file mapping logic, `esp1.fsr.ring` for web middleware integration, and `esp1.fsr.static` for static site generation. Each namespace has a single responsibility and clear boundaries.

### Metadata Conventions
Namespaces must use `:endpoint/http` metadata to map HTTP methods to handler functions. Template delegation is supported through `:endpoint/type` metadata. All namespace metadata is merged into Ring requests, enabling flexible templating and custom attributes.

### Development Tooling
Custom file-related schemas must be defined in `esp1.fsr.schema` and used consistently across the codebase. The malli-schema-expert agent is available for schema-related development tasks and should be utilized for complex schema work. Property-based testing with clojure.test.check is encouraged for complex logic validation.

## Quality Assurance

Code reviews must verify compliance with all principles above. New features require comprehensive tests including edge cases and error conditions. All commits must include updated documentation and passing tests. Breaking changes require version bump and migration guidance.

## Governance

This constitution supersedes all other development practices. Amendments require documentation of rationale, approval process, and migration plan for existing code. All development decisions must be evaluated against these principles. Complexity must be justified against the Zero-Dependency and Simplicity principles. Use CLAUDE.md for runtime development guidance and implementation details.

**Version**: 1.1.0 | **Ratified**: 2025-01-25 | **Last Amended**: 2025-09-27