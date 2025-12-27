# Development

## Commands

```bash
# Run tests
bb test

# Generate API docs
bb codox

# Start REPL
bb nrepl
```

## Dev Dependencies

- **tools.namespace** - Hot-reload support
- **Malli** - Schema validation and generative testing
- **Codox** - API documentation generation

## Project Structure

```
src/esp1/fsr/
  core.clj      # URI→file resolution, path parameters
  ring.clj      # Ring middleware, hot-reload
  static.clj    # Static site generation
  compile.clj   # Route compilation for production
  runtime.clj   # Compiled route matching
  cache.clj     # Route caching

dev/esp1/fsr/
  schema.clj    # Malli schemas (dev/test only)

test/
  esp1/fsr/     # Unit and integration tests
  foo/, bar/    # Example route structures
```

## Related

- **[Core Module](core-module.md)** - Main routing implementation
- **[Cache Module](cache-module.md)** - Caching implementation
- **[Route Compilation](route-compilation.md)** - Production compilation
