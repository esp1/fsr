# URI to File Routing

## Purpose

Map URIs to .clj files via filesystem structure. File location = URL path.

## Value

Organize routes using filesystem: file location directly indicates handled URL.

## Requirements

**R1: Basic mapping** - URI `/about` → `src/routes/about.clj` (case-sensitive, .clj/.cljc only)

**R2: Index files** - URI `/products` → `products.clj` OR `products/index.clj` (direct file wins). When resolving directory, `index.clj` prioritized over other .clj files (test: core_test.clj:file->clj-prioritizes-index-test)

**R3: Namespace = URI** - Namespace defines URL structure. Filesystem adapts to Clojure conventions.
- `foo.bar-baz` → URI `/foo/bar-baz`, file `foo/bar_baz.clj` or `foo/bar_baz/index.clj`
- `projects.2024-01-02_Some_Thing.index` → URI `/projects/2024-01-02_Some_Thing`, file `projects/2024_01_02_Some_Thing/index.clj`
- Namespace hyphens/underscores preserved in URI, filesystem converts all dashes to underscores

**R4: Single bracket params** - `<param>` matches non-slash values. File `thing/<id>.clj` matches `/thing/123` with `{"id" "123"}`

**R5: Double bracket params** - `<<param>>` matches all chars including slashes. File `article/<<path>>.clj` matches `/article/2024/01/post` with `{"path" "2024/01/post"}`. Only at route end.

**R6: Parameter injection** - Params in request map at `:endpoint/path-params` (string keys/values)

**R7: Namespace resolution** - File `routes/user/<id>.clj` → namespace `routes.user.<id>`

**R8: Matched namespace** - Matched namespace in request at `:endpoint/ns`

## Edge Cases

- Both `foo.clj` and `foo/index.clj` exist → `foo.clj` wins
- Empty params not allowed: `/user//profile` doesn't match `/user/<id>/profile`
- Case-sensitive matching

## Related

- [namespace-metadata.md](namespace-metadata.md) - Handler resolution
- [ring-middleware.md](ring-middleware.md) - Ring integration
- [route-caching.md](route-caching.md) - Performance
