# Route Caching

## Purpose

Cache route resolution results to avoid repeated filesystem scans and namespace loading.

## Value

Faster response times—route resolution happens once per unique URI, subsequent requests use cached results.

## Requirements

**FR-001: Resolution Caching** - Cache matched namespace, handler function, and path parameter patterns after first request.

**FR-002: Cache Key** - Use request URI path as cache key (case-sensitive, excludes query params).

**FR-003: Dev Mode Invalidation** - Clear cache on each request when `tools.namespace` is on classpath, enabling hot-reload.

**FR-004: Production Mode Persistence** - Cache persists for app lifetime when `tools.namespace` is absent.

**FR-005: Thread Safety** - Use Clojure atoms for lock-free concurrent access.

**FR-006: Manual Clearing** - Provide `clear-route-cache!` function for programmatic invalidation.

**FR-007: Cache Miss** - On miss, perform full resolution, store result, then handle request.

**FR-008: Cache Hit Performance** - Cache hits avoid filesystem access and namespace loading; O(1) lookup.

## Edge Cases

- First request: full resolution, store result
- Concurrent first requests to same URI: both resolve, last write wins (both store same data)
- Mode detection: `tools.namespace` on classpath but production desired → call `clear-route-cache!` manually

## Related

- [uri-to-file-routing.md](uri-to-file-routing.md) - What is cached
- [ring-middleware.md](ring-middleware.md) - When cache is used
- [../technical/cache-module.md](../technical/cache-module.md) - Implementation details
