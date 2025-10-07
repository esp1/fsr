# Data Model: Route Caching System

## Core Entities

### Cache Entry
Represents a single cached route resolution result.

**Fields**:
- `uri`: String - The original URI that was resolved
- `root-path`: String - The filesystem root path used for resolution
- `resolved-path`: String - The resolved file path
- `params`: Map - Extracted route parameters
- `timestamp`: Long - Last access timestamp for LRU tracking
- `metadata`: Map - Additional route metadata (optional)

**Validation Rules**:
- `uri` must be non-empty string
- `root-path` must be valid filesystem path
- `resolved-path` must be absolute path
- `params` must be map (can be empty)
- `timestamp` must be positive long

**State Transitions**:
- Created: When route is first resolved and cached
- Accessed: When cached entry is retrieved (timestamp updated)
- Evicted: When LRU policy removes entry due to cache limits

### Cache Key
Composite identifier for cache entries.

**Fields**:
- `uri`: String - Request URI
- `root-path`: String - Filesystem root used for resolution

**Validation Rules**:
- Both fields must be non-empty strings
- Must uniquely identify a cache entry
- Used as key in cache map

### Cache Configuration
Runtime configuration for cache behavior.

**Fields**:
- `max-entries`: Long - Maximum number of cached entries (default: 1000)
- `enabled?`: Boolean - Whether caching is active (default: true)
- `eviction-policy`: Keyword - Eviction strategy (default: :lru)

**Validation Rules**:
- `max-entries` must be positive integer
- `eviction-policy` must be one of #{:lru, :fifo, :none}

### Cache Metrics
Performance tracking data.

**Fields**:
- `hits`: Long - Number of cache hits
- `misses`: Long - Number of cache misses
- `evictions`: Long - Number of entries evicted
- `current-size`: Long - Current number of cached entries

**Validation Rules**:
- All counters must be non-negative
- `current-size` must be <= configured `max-entries`

**Computed Fields**:
- `hit-rate`: Double - hits / (hits + misses)
- `memory-usage`: Long - Estimated memory usage

## Data Relationships

```
Cache Store (atom)
├── Cache Entries (map: cache-key -> cache-entry)
├── Access Order (for LRU tracking)
└── Configuration (cache-config)

Cache Metrics (separate atom)
├── Counters (hits, misses, evictions)
└── Current State (size, memory estimates)
```

## Schema Definitions

The following Malli schemas will be defined in `esp1.fsr.schema`:

- `::cache-entry` - Full cache entry structure
- `::cache-key` - Composite key structure
- `::cache-config` - Configuration map
- `::cache-metrics` - Metrics data structure
- `::uri` - Valid URI string
- `::filesystem-path` - Valid filesystem path

## Concurrency Model

- **Cache Store**: Single atom containing cache state
- **Updates**: Atomic swap! operations for thread safety
- **Read Access**: Direct deref (no coordination needed)
- **Eviction**: Atomic operation during cache updates
- **Metrics**: Separate atom, updated independently

This design ensures thread safety while maintaining the zero-dependency constitutional requirement and providing O(1) access performance.