# Phase 0: Research & Technical Decisions

## Cache Implementation Approach

**Decision**: Use Clojure's built-in atom with a map for the cache store
**Rationale**:
- Thread-safe by default with atomic updates
- Zero external dependencies (constitutional requirement)
- O(1) lookup time with map
- Simple and maintainable

**Alternatives considered**:
- Java ConcurrentHashMap: Would require Java interop, less idiomatic
- Custom ref/dosync: Unnecessary complexity for this use case
- Third-party cache libraries: Violates zero-dependency principle

## LRU Eviction Strategy

**Decision**: Implement LRU using a combination of map + linked access tracking
**Rationale**:
- Can achieve O(1) access and eviction with proper data structure
- Maintains constitutional simplicity while meeting performance requirements
- Can use Clojure's priority-map or implement with access timestamps

**Alternatives considered**:
- Simple FIFO: Less optimal for cache hit rates
- Time-based TTL: Adds complexity and may not solve memory pressure
- No eviction: Would violate memory constraint requirements

## Thread Safety Approach

**Decision**: Use Clojure atom for cache state with swap! operations
**Rationale**:
- Built-in thread safety with atomic compare-and-swap
- No locks needed, better performance under contention
- Idiomatic Clojure approach

**Alternatives considered**:
- Explicit locking: More complex, potential for deadlocks
- Java concurrent structures: Less idiomatic, external dependency concerns

## Cache Key Design

**Decision**: Use composite key of [uri root-path] as string or vector
**Rationale**:
- Ensures unique identification per spec requirements
- Simple to implement and debug
- Supports the existing FSR routing paradigm

**Alternatives considered**:
- Hash-based keys: Could have collisions, harder to debug
- Separate caches per root: More complex management

## Integration with FSR Core

**Decision**: Integrate caching at the route resolution layer in core.clj
**Rationale**:
- Transparent to existing users
- Can wrap existing resolve-route function
- Maintains backward compatibility

**Alternatives considered**:
- Ring middleware level: Would miss non-Ring usage
- Separate cache namespace only: Requires manual integration by users

## Configuration Approach

**Decision**: Use a configuration map with sensible defaults
**Rationale**:
- Allows runtime configuration without breaking zero-dependency
- Supports per-application tuning
- Simple to test and validate

**Alternatives considered**:
- Environment variables: Less flexible, harder to test
- Configuration files: Adds file dependency complexity

## Pattern-Based Invalidation

**Decision**: Use regex pattern matching against cache keys
**Rationale**:
- Flexible for development workflows
- Built into Clojure, no external dependencies
- Familiar to developers

**Alternatives considered**:
- Glob patterns: Would need custom implementation
- Exact key matching: Too rigid for development needs
- Tag-based invalidation: Adds complexity to cache entries

## Performance Monitoring

**Decision**: Track metrics in separate atom, expose via functions
**Rationale**:
- Simple to implement and maintain
- Can be easily extended in future
- Minimal performance overhead

**Alternatives considered**:
- External metrics libraries: Violates zero-dependency
- JVM metrics integration: Too complex for initial implementation

## Next Steps

All technical unknowns have been resolved. The approach maintains constitutional compliance while meeting all functional and non-functional requirements from the specification.

Ready to proceed to Phase 1: Design & Contracts.