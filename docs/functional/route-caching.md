# Route Caching

## Purpose

Cache route resolution results to avoid repeated filesystem scans and namespace loading.

## Value

Faster response times—route resolution happens once per unique URI, subsequent requests use cached results.

## Requirements

**Resolution Caching** - Cache matched namespace, handler function, and path parameter patterns after first request.

**Cache Key** - Use request URI path as cache key (case-sensitive, excludes query params).

**Dev Mode Invalidation** - Clear cache on each request when `tools.namespace` is on classpath, enabling hot-reload.

**Production Mode Persistence** - Cache persists for app lifetime when `tools.namespace` is absent.

**Thread Safety** - Use Clojure atoms for lock-free concurrent access.

**Manual Clearing** - Provide `clear-route-cache!` function for programmatic invalidation.

**Cache Miss** - On miss, perform full resolution, store result, then handle request.

**Cache Hit Performance** - Cache hits avoid filesystem access and namespace loading; O(1) lookup.

## Edge Cases

- First request: full resolution, store result
- Concurrent first requests to same URI: both resolve, last write wins (both store same data)
- Mode detection: `tools.namespace` on classpath but production desired → call `clear-route-cache!` manually

## Related

- **[URI to File Routing](uri-to-file-routing.md)** - What is cached
- **[Ring Middleware](ring-middleware.md)** - When cache is used
- **[Cache Module](../technical/cache-module.md)** - Implementation details
