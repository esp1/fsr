
# Implementation Plan: Route Caching System

**Branch**: `006-route-caching-system` | **Date**: 2025-09-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-route-caching-system/spec.md`

## Execution Flow (/plan command scope)
```
1. Load feature spec from Input path
   → If not found: ERROR "No feature spec at {path}"
2. Fill Technical Context (scan for NEEDS CLARIFICATION)
   → Detect Project Type from file system structure or context (web=frontend+backend, mobile=app+api)
   → Set Structure Decision based on project type
3. Fill the Constitution Check section based on the content of the constitution document.
4. Evaluate Constitution Check section below
   → If violations exist: Document in Complexity Tracking
   → If no justification possible: ERROR "Simplify approach first"
   → Update Progress Tracking: Initial Constitution Check
5. Execute Phase 0 → research.md
   → If NEEDS CLARIFICATION remain: ERROR "Resolve unknowns"
6. Execute Phase 1 → contracts, data-model.md, quickstart.md, agent-specific template file (e.g., `CLAUDE.md` for Claude Code, `.github/copilot-instructions.md` for GitHub Copilot, `GEMINI.md` for Gemini CLI, `QWEN.md` for Qwen Code or `AGENTS.md` for opencode).
7. Re-evaluate Constitution Check section
   → If new violations: Refactor design, return to Phase 1
   → Update Progress Tracking: Post-Design Constitution Check
8. Plan Phase 2 → Describe task generation approach (DO NOT create tasks.md)
9. STOP - Ready for /tasks command
```

**IMPORTANT**: The /plan command STOPS at step 7. Phases 2-4 are executed by other commands:
- Phase 2: /tasks command creates tasks.md
- Phase 3-4: Implementation execution (manual or via tools)

## Summary
Implement an in-memory route caching system for the FSR library to improve performance by avoiding repeated filesystem operations. The cache will use LRU eviction, support pattern-based invalidation, and provide basic metrics (hit rate and cache size). Default maximum of 1000 cached entries with configurable limits.

## Technical Context
**Language/Version**: Clojure (existing FSR library)
**Primary Dependencies**: None (zero-dependency principle from constitution)
**Storage**: In-memory cache (no persistent storage required)
**Testing**: clojure.test with Malli schema validation
**Target Platform**: JVM (existing FSR library platform)
**Project Type**: Single library (extends existing FSR routing library)
**Performance Goals**: O(1) cache lookup, significantly faster than filesystem resolution
**Constraints**: Configurable memory limits (default 1000 entries), LRU eviction
**Scale/Scope**: Route caching for web applications using FSR, thread-safe for concurrent requests

## Constitution Check
*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Initial Check (Pre-Phase 0)**:
**Zero-Dependency Library**: ✅ PASS - Cache implementation uses only core Clojure, no external dependencies
**Documentation-First**: ✅ PASS - All cache functions will be documented with purpose, params, examples
**Malli Schema Contracts**: ✅ PASS - Cache entry, key, and configuration schemas planned
**Test-First Development**: ✅ PASS - TDD approach with tests before implementation specified
**API Stability**: ✅ PASS - Adding new cache namespace, existing FSR API unchanged

**Post-Design Check (After Phase 1)**:
**Zero-Dependency Library**: ✅ PASS - Design uses only core Clojure atoms and maps
**Documentation-First**: ✅ PASS - Quickstart.md created, API contracts documented
**Malli Schema Contracts**: ✅ PASS - Complete contracts defined in contracts/cache-api.clj
**Test-First Development**: ✅ PASS - Contract tests and TDD approach specified
**API Stability**: ✅ PASS - Cache integrates transparently, no breaking changes

## Project Structure

### Documentation (this feature)
```
specs/[###-feature]/
├── plan.md              # This file (/plan command output)
├── research.md          # Phase 0 output (/plan command)
├── data-model.md        # Phase 1 output (/plan command)
├── quickstart.md        # Phase 1 output (/plan command)
├── contracts/           # Phase 1 output (/plan command)
└── tasks.md             # Phase 2 output (/tasks command - NOT created by /plan)
```

### Source Code (repository root)
```
src/esp1/fsr/
├── core.clj              # Existing - URI-to-file mapping
├── ring.clj              # Existing - Ring middleware
├── static.clj            # Existing - Static site generation
├── cache.clj             # NEW - Route caching functionality
└── schema.clj            # NEW/Enhanced - Cache-related schemas

test/esp1/fsr/
├── core_test.clj         # Existing tests
├── cache_test.clj        # NEW - Cache functionality tests
└── schema_test.clj       # NEW/Enhanced - Schema tests

docs/
└── cache.md              # NEW - Cache documentation
```

**Structure Decision**: Single project structure - extending existing FSR library with new cache namespace and functionality

## Phase 0: Outline & Research
1. **Extract unknowns from Technical Context** above:
   - For each NEEDS CLARIFICATION → research task
   - For each dependency → best practices task
   - For each integration → patterns task

2. **Generate and dispatch research agents**:
   ```
   For each unknown in Technical Context:
     Task: "Research {unknown} for {feature context}"
   For each technology choice:
     Task: "Find best practices for {tech} in {domain}"
   ```

3. **Consolidate findings** in `research.md` using format:
   - Decision: [what was chosen]
   - Rationale: [why chosen]
   - Alternatives considered: [what else evaluated]

**Output**: research.md with all NEEDS CLARIFICATION resolved

## Phase 1: Design & Contracts
*Prerequisites: research.md complete*

1. **Extract entities from feature spec** → `data-model.md`:
   - Entity name, fields, relationships
   - Validation rules from requirements
   - State transitions if applicable

2. **Generate API contracts** from functional requirements:
   - For each user action → endpoint
   - Use standard REST/GraphQL patterns
   - Output OpenAPI/GraphQL schema to `/contracts/`

3. **Generate contract tests** from contracts:
   - One test file per endpoint
   - Assert request/response schemas
   - Tests must fail (no implementation yet)

4. **Extract test scenarios** from user stories:
   - Each story → integration test scenario
   - Quickstart test = story validation steps

5. **Update agent file incrementally** (O(1) operation):
   - Run `.specify/scripts/bash/update-agent-context.sh claude`
     **IMPORTANT**: Execute it exactly as specified above. Do not add or remove any arguments.
   - If exists: Add only NEW tech from current plan
   - Preserve manual additions between markers
   - Update recent changes (keep last 3)
   - Keep under 150 lines for token efficiency
   - Output to repository root

**Output**: data-model.md, /contracts/*, failing tests, quickstart.md, agent-specific file

## Phase 2: Task Planning Approach
*This section describes what the /tasks command will do - DO NOT execute during /plan*

**Task Generation Strategy**:
- Load `.specify/templates/tasks-template.md` as base
- Generate tasks from Phase 1 design docs (contracts, data model, quickstart)
- Schema definition tasks from contracts/cache-api.clj
- Cache core implementation from data-model.md
- Integration tasks for FSR core modification
- Test tasks following TDD approach
- Documentation tasks from quickstart.md

**Specific Task Categories**:
1. **Schema Setup** [P]: Create/enhance esp1.fsr.schema with cache schemas
2. **Core Implementation** [P]: Create esp1.fsr.cache namespace
3. **Integration**: Modify esp1.fsr.core for cache integration
4. **Testing**: Contract tests, unit tests, integration tests
5. **Documentation**: Update function docs, create cache.md

**Ordering Strategy**:
- Schemas first (dependencies for everything else)
- Cache implementation with TDD approach
- Core integration after cache is working
- Documentation updates throughout
- Mark [P] for parallel execution where possible

**Estimated Output**: 18-22 numbered, ordered tasks in tasks.md

**Key Dependencies**:
- Cache schemas → Cache implementation
- Cache implementation → Core integration
- Contract definitions → Test creation
- All implementation → Documentation updates

**IMPORTANT**: This phase is executed by the /tasks command, NOT by /plan

## Phase 3+: Future Implementation
*These phases are beyond the scope of the /plan command*

**Phase 3**: Task execution (/tasks command creates tasks.md)  
**Phase 4**: Implementation (execute tasks.md following constitutional principles)  
**Phase 5**: Validation (run tests, execute quickstart.md, performance validation)

## Complexity Tracking
*Fill ONLY if Constitution Check has violations that must be justified*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |


## Progress Tracking
*This checklist is updated during execution flow*

**Phase Status**:
- [x] Phase 0: Research complete (/plan command)
- [x] Phase 1: Design complete (/plan command)
- [x] Phase 2: Task planning complete (/plan command - describe approach only)
- [ ] Phase 3: Tasks generated (/tasks command)
- [ ] Phase 4: Implementation complete
- [ ] Phase 5: Validation passed

**Gate Status**:
- [x] Initial Constitution Check: PASS
- [x] Post-Design Constitution Check: PASS
- [x] All NEEDS CLARIFICATION resolved
- [x] Complexity deviations documented (none required)

---

## Summary

**Implementation plan completed successfully**. All design artifacts have been generated:

**Generated Artifacts**:
- `research.md`: Technical decisions and approach validation
- `data-model.md`: Cache entities, schemas, and relationships
- `contracts/cache-api.clj`: Malli contracts and API specifications
- `quickstart.md`: Usage examples and integration guide
- `CLAUDE.md`: Updated agent context file

**Key Decisions**:
- LRU cache using Clojure atoms for thread safety
- Zero external dependencies (constitutional compliance)
- Transparent integration with existing FSR core
- Configurable limits with sensible defaults (1000 entries)
- Pattern-based invalidation for development workflows

**Next Step**: Run `/tasks` command to generate implementation tasks

---
*Based on Constitution v1.1.0 - See `.specify/memory/constitution.md`*
