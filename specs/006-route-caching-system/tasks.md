# Tasks: Route Caching System

**Feature**: 006-route-caching-system
**Input**: Design documents from `/Users/edwin/Projects/fsr/specs/006-route-caching-system/`
**Prerequisites**: research.md, data-model.md, contracts/cache-api.clj, quickstart.md

## Execution Flow

This tasks file implements a route caching system for FSR to improve performance by storing resolved route mappings in memory. The implementation follows TDD principles with Malli schema validation and maintains zero external dependencies.

**Tech Stack**: Clojure atoms for thread-safe caching, Malli for validation (dev dependency only)

## Format: `[ID] [P?] Description`
- **[P]**: Can run in parallel (different files, no dependencies)
- All file paths are absolute from repository root

## Phase 3.1: Setup & Schema Definition

- [x] T001 Create schema definitions in `/Users/edwin/Projects/fsr/schema/src/esp1/fsr/schema.clj`
  - Add `::cache-entry`, `::cache-key`, `::cache-config`, `::cache-metrics` schemas
  - Add validation functions per data-model.md specifications
  - **Dependencies**: None
  - **Files Modified**: `schema/src/esp1/fsr/schema.clj`

## Phase 3.2: Tests First (TDD) ⚠️ MUST COMPLETE BEFORE 3.3

**CRITICAL: These tests MUST be written and MUST FAIL before ANY implementation**

- [x] T002 [P] Contract test for cache-key validation in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache/contract_test.clj`
  - Test valid-cache-key? from contracts/cache-api.clj
  - Property-based testing with Malli generators
  - Verify schema validation for cache keys
  - **Dependencies**: T001
  - **Files Created**: `test/esp1/fsr/cache/contract_test.clj`

- [x] T003 [P] Contract test for cache-entry validation in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache/contract_test.clj`
  - Test valid-cache-entry? from contracts/cache-api.clj
  - Verify timestamp, params, metadata fields
  - Test state transition validation
  - **Dependencies**: T001
  - **Files Modified**: `test/esp1/fsr/cache/contract_test.clj`

- [x] T004 [P] Contract test for cache-config validation in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache/contract_test.clj`
  - Test valid-cache-config? from contracts/cache-api.clj
  - Verify max-entries, enabled?, eviction-policy
  - Test default values
  - **Dependencies**: T001
  - **Files Modified**: `test/esp1/fsr/cache/contract_test.clj`

- [x] T005 [P] Contract test for cache-metrics validation in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache/contract_test.clj`
  - Test valid-cache-metrics? from contracts/cache-api.clj
  - Verify hits, misses, evictions counters
  - Test computed fields (hit-rate)
  - **Dependencies**: T001
  - **Files Modified**: `test/esp1/fsr/cache/contract_test.clj`

- [x] T006 [P] Integration test for cache hit scenario in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache_integration_test.clj`
  - Test first resolution (miss) → second resolution (hit)
  - Verify cached? flag in response
  - Validate metrics update (hit count increment)
  - Based on quickstart.md "Route Resolution with Caching" example
  - **Dependencies**: T001
  - **Files Created**: `test/esp1/fsr/cache_integration_test.clj`

- [x] T007 [P] Integration test for cache clearing in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache_integration_test.clj`
  - Test cache/clear! returns correct count
  - Verify cache is empty after clear
  - Test resolution after clear (should miss)
  - Based on quickstart.md "Clear Entire Cache" example
  - **Dependencies**: T001
  - **Files Modified**: `test/esp1/fsr/cache_integration_test.clj`

- [x] T008 [P] Integration test for pattern invalidation in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache_integration_test.clj`
  - Test cache/invalidate! with regex patterns
  - Verify selective invalidation (/api/.* pattern)
  - Test return value (count of invalidated entries)
  - Based on quickstart.md "Pattern-Based Invalidation" example
  - **Dependencies**: T001
  - **Files Modified**: `test/esp1/fsr/cache_integration_test.clj`

- [x] T009 [P] Integration test for LRU eviction in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache_integration_test.clj`
  - Configure cache with max-entries=3
  - Add 4 entries, verify oldest evicted
  - Check metrics.evictions counter
  - **Dependencies**: T001
  - **Files Modified**: `test/esp1/fsr/cache_integration_test.clj`

- [x] T010 [P] Integration test for cache configuration in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache_integration_test.clj`
  - Test cache/configure! updates settings
  - Verify disabled cache (enabled? false)
  - Test custom max-entries limit
  - Based on quickstart.md "Configuration" examples
  - **Dependencies**: T001
  - **Files Modified**: `test/esp1/fsr/cache_integration_test.clj`

- [x] T011 [P] Performance validation test in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache_performance_test.clj`
  - Measure uncached vs cached resolution time
  - Verify cache hit < 1ms (per spec: 50x faster)
  - Test with 100 URIs for statistical validity
  - **Dependencies**: T001
  - **Files Created**: `test/esp1/fsr/cache_performance_test.clj`

## Phase 3.3: Core Implementation (ONLY after tests are failing)

- [x] T012 Create cache namespace in `/Users/edwin/Projects/fsr/src/esp1/fsr/cache.clj`
  - Define cache-store atom (map: cache-key → cache-entry)
  - Define cache-metrics atom (counters)
  - Define cache-config atom (configuration)
  - **Dependencies**: T002-T011 (all tests must exist and fail)
  - **Files Created**: `src/esp1/fsr/cache.clj`

- [x] T013 Implement cache data structures in `/Users/edwin/Projects/fsr/src/esp1/fsr/cache.clj`
  - Implement cache-key creation from [uri root-path]
  - Implement cache-entry structure with timestamp
  - Implement LRU tracking with access ordering
  - **Dependencies**: T012
  - **Files Modified**: `src/esp1/fsr/cache.clj`

- [x] T014 Implement cache operations in `/Users/edwin/Projects/fsr/src/esp1/fsr/cache.clj`
  - Implement cache-get (with timestamp update for LRU)
  - Implement cache-put (with eviction if over limit)
  - Implement LRU eviction logic
  - Update metrics on each operation
  - **Dependencies**: T013
  - **Files Modified**: `src/esp1/fsr/cache.clj`

- [x] T015 Implement cache management functions in `/Users/edwin/Projects/fsr/src/esp1/fsr/cache.clj`
  - Implement clear! function (returns count)
  - Implement invalidate! with regex pattern matching
  - Implement configure! for runtime config updates
  - **Dependencies**: T014
  - **Files Modified**: `src/esp1/fsr/cache.clj`

- [x] T016 Implement metrics functions in `/Users/edwin/Projects/fsr/src/esp1/fsr/cache.clj`
  - Implement get-metrics (returns current metrics map)
  - Implement debug-info for development (per quickstart.md)
  - Calculate derived metrics (hit-rate)
  - **Dependencies**: T014
  - **Files Modified**: `src/esp1/fsr/cache.clj`

## Phase 3.4: Integration with FSR Core

- [x] T017 Integrate caching into core.clj in `/Users/edwin/Projects/fsr/src/esp1/fsr/core.clj`
  - Wrap uri->file+params with cache lookup
  - Add cached? flag to return value
  - Maintain backward compatibility (cache transparent)
  - **Dependencies**: T015, T016
  - **Files Modified**: `src/esp1/fsr/core.clj`

- [x] T018 Update ring middleware for dev cache clearing in `/Users/edwin/Projects/fsr/src/esp1/fsr/ring.clj`
  - Call cache/clear! on every request in dev mode
  - Ensure hot-reload works with caching
  - Add verbose logging of cache stats if :verbose? true
  - **Dependencies**: T017
  - **Files Modified**: `src/esp1/fsr/ring.clj`

- [x] T019 Update static site generation in `/Users/edwin/Projects/fsr/src/esp1/fsr/static.clj`
  - Clear cache before static generation
  - Re-enable cache after generation completes
  - Ensure tracked URIs work with caching
  - **Dependencies**: T017
  - **Files Modified**: `src/esp1/fsr/static.clj`

## Phase 3.5: Polish & Documentation

- [ ] T020 [P] Unit tests for cache eviction edge cases in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache/eviction_test.clj`
  - Test eviction with exactly max-entries
  - Test concurrent access patterns
  - Test eviction of most recently accessed item
  - **Dependencies**: T015
  - **Files Created**: `test/esp1/fsr/cache/eviction_test.clj`

- [ ] T021 [P] Unit tests for pattern matching in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache/pattern_test.clj`
  - Test various regex patterns against cache keys
  - Test edge cases (empty pattern, invalid regex)
  - Test partial matches vs full matches
  - **Dependencies**: T015
  - **Files Created**: `test/esp1/fsr/cache/pattern_test.clj`

- [ ] T022 [P] Thread safety tests in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache/concurrency_test.clj`
  - Test concurrent reads and writes
  - Verify atom swap! operations are atomic
  - Test race conditions during eviction
  - **Dependencies**: T015
  - **Files Created**: `test/esp1/fsr/cache/concurrency_test.clj`

- [ ] T023 [P] Update API documentation in `/Users/edwin/Projects/fsr/src/esp1/fsr/cache.clj`
  - Add docstrings to all public functions
  - Include Malli schema annotations
  - Add usage examples per quickstart.md
  - **Dependencies**: T016
  - **Files Modified**: `src/esp1/fsr/cache.clj`

- [x] T024 Verify all tests pass in `/Users/edwin/Projects/fsr`
  - Run `clojure -M:dev -m clojure.test.runner`
  - Ensure all 22+ tests pass
  - Verify no regressions in existing core_test.clj
  - **Dependencies**: T017, T018, T019, T020, T021, T022, T023
  - **Files Modified**: None (validation only)

- [ ] T025 [P] Performance benchmarking in `/Users/edwin/Projects/fsr/test/esp1/fsr/cache_benchmark.clj`
  - Benchmark cache hit vs miss (target: 50x improvement)
  - Measure memory overhead per cached entry
  - Test with 1000, 5000, 10000 entries
  - Generate performance report
  - **Dependencies**: T024
  - **Files Created**: `test/esp1/fsr/cache_benchmark.clj`

- [ ] T026 Manual testing per quickstart.md in `/Users/edwin/Projects/fsr`
  - Test all examples from quickstart.md
  - Verify REPL workflow works
  - Test integration with Ring middleware
  - Validate hot-reload with cache clearing
  - **Dependencies**: T024
  - **Files Modified**: None (manual validation)

## Dependencies

**Critical Path**:
```
T001 (schemas)
  ↓
T002-T011 (all tests in parallel)
  ↓
T012 → T013 → T014 → T015 → T016 (core cache implementation - sequential, same file)
  ↓
T017 (core.clj integration)
  ↓
T018 (ring.clj) ∥ T019 (static.clj) ∥ T020-T023 (polish tests in parallel)
  ↓
T024 (test validation)
  ↓
T025 (benchmarks) ∥ T026 (manual testing in parallel)
```

**Parallel Opportunities**:
- T002-T011: All test files (9 tasks can run together)
- T018, T019, T020, T021, T022, T023: Different files (6 tasks can run together)
- T025, T026: Final validation (2 tasks can run together)

**Sequential Constraints**:
- T012-T016: Same file (src/esp1/fsr/cache.clj) - must be sequential
- T017 blocks T018 and T019 (core integration must complete first)

## Parallel Execution Examples

### Launch all contract tests together (Phase 3.2):
```bash
# After T001 completes, launch these 9 tests in parallel:
Task: "Contract test for cache-key validation in test/esp1/fsr/cache/contract_test.clj"
Task: "Contract test for cache-entry validation (append to contract_test.clj)"
Task: "Contract test for cache-config validation (append to contract_test.clj)"
Task: "Contract test for cache-metrics validation (append to contract_test.clj)"
Task: "Integration test for cache hit scenario in test/esp1/fsr/cache_integration_test.clj"
Task: "Integration test for cache clearing (append to cache_integration_test.clj)"
Task: "Integration test for pattern invalidation (append to cache_integration_test.clj)"
Task: "Integration test for LRU eviction (append to cache_integration_test.clj)"
Task: "Integration test for cache configuration (append to cache_integration_test.clj)"
Task: "Performance validation test in test/esp1/fsr/cache_performance_test.clj"
```

### Launch integration tasks together (Phase 3.4):
```bash
# After T017 completes, launch these 6 tasks in parallel:
Task: "Update ring middleware for dev cache clearing in src/esp1/fsr/ring.clj"
Task: "Update static site generation in src/esp1/fsr/static.clj"
Task: "Unit tests for cache eviction edge cases in test/esp1/fsr/cache/eviction_test.clj"
Task: "Unit tests for pattern matching in test/esp1/fsr/cache/pattern_test.clj"
Task: "Thread safety tests in test/esp1/fsr/cache/concurrency_test.clj"
Task: "Update API documentation in src/esp1/fsr/cache.clj"
```

## Notes

- **Zero Dependencies**: Use only Clojure atoms, no external cache libraries
- **Thread Safety**: All cache operations use atomic swap! for concurrency
- **TDD Workflow**: Verify each test fails before implementing feature
- **Malli Schemas**: Use for validation but only as dev dependency
- **Performance Target**: Cache hits should be 50x faster than filesystem resolution
- **Memory Limit**: Default 1000 entries with LRU eviction
- **Development Mode**: Cache cleared on every request for hot-reload
- **Production Mode**: Cache persists across requests for performance

## Validation Checklist

*GATE: Verify before marking feature complete*

- [x] All contracts have corresponding tests (T002-T005)
- [x] All entities from data-model.md have implementations (cache-entry, cache-key, cache-config, cache-metrics)
- [x] All tests come before implementation (T002-T011 before T012-T016)
- [x] Parallel tasks are truly independent (verified by file path analysis)
- [x] Each task specifies exact absolute file path
- [x] No task modifies same file as another [P] task (T012-T016 sequential, rest safe)
- [x] Performance requirements validated (T011, T025)
- [x] Integration with existing FSR verified (T017-T019)
- [x] Manual testing scenarios from quickstart.md (T026)
