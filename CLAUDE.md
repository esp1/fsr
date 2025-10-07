# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About fsr

**fsr** is a filesystem router for Clojure web projects that maps URIs to Clojure namespace files based on filesystem structure. It supports dynamic path parameters, custom templates via namespace metadata, and can function as a static site generator.

## Project Structure

```
src/esp1/fsr/
  core.clj      # Core routing logic: URI→file resolution, path parameters, namespace metadata
  ring.clj      # Ring middleware with hot-reload support (dev mode)
  static.clj    # Static site generation

test/           # Test route examples and core tests
  esp1/fsr/core_test.clj
  foo/, bar/, baz/, bad/  # Example route structures

schema/         # Git submodule for fsr-schema (Malli schemas)
  src/esp1/fsr/schema.clj

docs/api/       # Generated API documentation
```

## Key Concepts

### Route Resolution
- URIs map to `.clj` files under a configured root path
- `/foo` → `foo.clj` OR `foo/index.clj`
- Dashes in URIs map to underscores in filenames: `/foo-bar` → `foo_bar.clj`
- Path parameters: `<param>` for non-slash values, `<<param>>` for values with slashes
  - `/thing/<id>.clj` matches `/thing/123` with `{:endpoint/path-params {"id" "123"}}`

### Namespace Metadata
- `:endpoint/http` - Map of HTTP methods (`:get`, `:post`) to handler function symbols
- `:endpoint/type` - Symbol of template namespace to delegate to (enables custom templates)
- Handler functions receive Ring request map with:
  - `:endpoint/ns` - Namespace symbol that matched the URI
  - `:endpoint/path-params` - Map of path parameter names to values (strings)
  - Full namespace metadata merged in

### Dev vs Production
- **Development**: Use `wrap-fs-router` Ring middleware with hot-reload
- **Production**: Use `publish-static` to generate static site or pre-compiled routes

## Commands

```bash
# Run tests
clojure -M:dev -m clojure.test.runner

# Generate API documentation
bb codox
# or
clojure -X:codox

# Start REPL with dev dependencies
clojure -M:dev
```

## Development Notes

- **Zero external dependencies** in core library (principle from constitution)
- Dev dependencies: Malli (schemas), tools.namespace (hot-reload), Codox (docs)
- Uses `fsr-schema` submodule for Malli validation schemas
- Route cache (`route-cache` atom in `core.clj`) cleared on each dev request
- Hot-reload via `clojure.tools.namespace` if available (optional dev feature)

## Testing

Test route examples in `test/` directory demonstrate:
- Simple routes: `test/foo/index.clj`
- Path parameters: `test/bar/abc_<param1>_def_<<param2>>_xyz.clj`
- Nested params: `test/baz/<<arg>>/moo.clj`
- Date-based routes: `test/bar/2024_01_02_Some_Thing/index.clj`

Tests use Malli generative testing with custom file schema registry.
