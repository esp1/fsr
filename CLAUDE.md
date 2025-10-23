# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## About fsr

**fsr** is a filesystem router for Clojure web projects that maps URIs to Clojure namespace files based on filesystem structure. It supports dynamic path parameters, custom templates via namespace metadata, and can function as a static site generator.

## Project Structure

```
src/esp1/fsr/
  core.clj      # Core routing logic: URI→file resolution, path parameters, namespace metadata
  ring.clj      # Ring middleware with hot-reload support (dev mode)
  static.clj    # Static site generation + route compilation
  compile.clj   # Route compilation for production (non-GET routes)
  runtime.clj   # Runtime matching for compiled routes (no filesystem access)
  cache.clj     # Route caching module

test/           # Test route examples and core tests
  esp1/fsr/
    core_test.clj, compile_test.clj, runtime_test.clj, integration_test.clj
  foo/, bar/, baz/, bad/  # Example route structures
  integration/routes/     # Integration test routes

examples/       # Production deployment examples
  build.clj              # Build script for static + compiled routes
  production_server.clj  # Example production server
  README.md             # Deployment guide

schema/         # Git submodule for fsr-schema (Malli schemas)
  src/esp1/fsr/schema.clj

docs/
  spec/         # Feature requirement specifications
  tech/         # Technical implementation docs
  api/          # Generated API documentation
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
- **Development**: Use `wrap-fs-router` Ring middleware with hot-reload and filesystem scanning
- **Production**:
  - Use `publish` to generate static HTML (GET routes) + compiled routes (non-GET routes)
  - Use `wrap-compiled-routes` middleware for zero-filesystem-access runtime
  - See [examples/README.md](examples/README.md) for deployment guide

## Commands

```bash
# Run tests
bb test

# Generate API documentation
bb codox
# or
clojure -X:codox

# Build for production (static HTML + compiled routes)
bb examples/build.clj --routes src/routes --output dist

# Start production server
clojure -M -m production-server

# Start REPL with dev dependencies
clojure -M:dev
```

## Development Notes

- **Zero external dependencies** in core library (principle from constitution)
- Dev dependencies: Malli (schemas), tools.namespace (hot-reload), Codox (docs)
- Uses `fsr-schema` submodule for Malli validation schemas
- Route cache (`route-cache` atom in `core.clj`) cleared on each dev request
- Hot-reload via `clojure.tools.namespace` if available (optional dev feature)

## Malli Schema Requirements

**CRITICAL:** All public functions and data structures MUST have Malli schemas.

### Why Schemas Matter

- **Validation**: Catch errors early with runtime validation (in dev/test)
- **Documentation**: Schemas serve as executable documentation
- **Generative Testing**: Enable property-based testing
- **Zero Runtime Cost**: Schemas are metadata - no production dependency on Malli

### Schema Location and Organization

- **Data structure schemas**: Define in `schema/src/esp1/fsr/schema.clj` (git submodule)
- **Function schemas**: Add as `:malli/schema` metadata on the function itself
- **Schema registry**: Group related schemas in registry functions (e.g., `route-schemas`, `cache-schemas`)

### Adding Function Schemas

Function schemas MUST be added as metadata using the `:malli/schema` key. This keeps Malli as a dev-only dependency.

**IMPORTANT:** Always use `:catn` (not `:cat`) for function parameters to provide descriptive names. This makes schemas self-documenting and easier to understand.

**Template:**
```clojure
(defn my-function
  "Function docstring"
  {:malli/schema [:=> [:catn
                       [:param-name :param-type]
                       [:other-param :other-type]]
                  :return-type]}
  [param-name other-param]
  (implementation))
```

**Example from cache.clj:**
```clojure
(defn- make-cache-key
  "Create cache key from URI and root path."
  {:malli/schema [:=> [:catn
                       [:uri :string]
                       [:root-path :string]]
                  :string]}
  [uri root-path]
  (str uri "|" root-path))
```

**Why `:catn` instead of `:cat`:**
- `:cat` - Takes unnamed types: `[:cat :string :string]` - unclear which is which
- `:catn` - Takes named types: `[:catn [:uri :string] [:root-path :string]]` - self-documenting

For single-argument functions, `:catn` is still preferred for consistency:
```clojure
{:malli/schema [:=> [:catn [:file-or-filename [:or :file :file-name]]]
                [:maybe [:string {:title "File extension"}]]]}
```

### Adding Data Structure Schemas

1. **Define the schema** in `schema/src/esp1/fsr/schema.clj`:
   ```clojure
   (def my-data-structure?
     "Description of what this validates"
     [:map
      [:required-key :string]
      [:optional-key {:optional true} :int]])
   ```

2. **Add to registry function**:
   ```clojure
   (defn my-schemas []
     {:my-data-structure my-data-structure?
      :other-schema other-schema?})
   ```

3. **Register in tests** - Update test fixtures to include the new registry:
   ```clojure
   (use-fixtures :once
     (fn [f]
       (mr/set-default-registry!
        (merge
         (m/comparator-schemas)
         (m/type-schemas)
         (m/sequence-schemas)
         (m/base-schemas)
         (fsr-schema/file-schemas)
         (fsr-schema/cache-schemas)
         (fsr-schema/route-schemas)
         (fsr-schema/my-schemas)))  ;; <-- Add here
       (mdev/start!)
       (f)
       (mdev/stop!)
       (mr/set-default-registry! m/default-registry)))
   ```

### Schema Checklist for New Code

When adding or modifying code, ensure:

- [ ] All public functions have `:malli/schema` metadata
- [ ] Function schemas use `:catn` (not `:cat`) with descriptive parameter names
- [ ] All data structures passed between functions are defined in `schema.clj`
- [ ] New schemas are added to appropriate registry function
- [ ] Test registries are updated to include new schema groups
- [ ] Tests pass with `bb test` (validates schemas work correctly)
- [ ] Schemas use custom types from registry (`:file`, `:http-method`, etc.) not just primitives

### Common Schema Patterns

**Function with optional parameters:**
```clojure
{:malli/schema [:=> [:catn
                     [:required-param :required-type]
                     [:options [:map
                                [:opt-key {:optional true} :string]]]]
                :return-type]}
```

**Function returning maybe/optional:**
```clojure
{:malli/schema [:=> [:catn [:input :input-type]]
                [:maybe :output-type]]}
```

**Higher-order function (returns function):**
```clojure
{:malli/schema [:=> [:catn [:input :input-type]]
                [:fn #(fn? %)]]}
```

**Map with specific keys:**
```clojure
[:map
 [:required-key :string]
 [:optional-key {:optional true} :int]
 [:namespaced-key {:optional true} :keyword]]
```

### Verification

Before committing code:
1. Run `bb test` - ensures all schemas compile and validate
2. Check that Malli dev-mode instruments all functions
3. Verify no schema-related warnings in test output

### Example: Complete Workflow

```clojure
;; 1. Define data structure schema in schema/src/esp1/fsr/schema.clj
(def request-context?
  "Schema for request context"
  [:map
   [:uri :uri-template]
   [:method :http-method]
   [:params {:optional true} :path-params]])

;; 2. Add to registry
(defn route-schemas []
  {:request-context request-context?
   ;; ... other schemas
   })

;; 3. Add function with schema in src/esp1/fsr/core.clj
(defn process-request
  "Processes incoming request context"
  {:malli/schema [:=> [:cat :request-context]
                  :ring-response]}
  [ctx]
  ;; implementation
  )

;; 4. Update test registry in test files
;; (Already shown above)

;; 5. Run tests
;; bb test
```

This ensures comprehensive schema coverage while keeping Malli as a dev-only dependency.

## Working with Clojure Code

**IMPORTANT:** When reading or editing `.clj`, `.cljs`, or `.cljc` files:

1. **ALWAYS invoke the `clojure-edit` skill FIRST** before reading or editing Clojure files
   - Prevents syntax errors (unmatched parens, brackets, braces)
   - Provides structure-aware editing guidance
   - Enables using form-aware scripts for safer edits

2. **Use the skill's tools when available:**
   - `scripts/read_clojure_file.clj` - Pattern-based reading (see functions by name/type)
   - `scripts/edit_clojure_form.clj` - Form-aware editing (target functions by name)
   - These scripts use bundled babashka (no installation needed)

3. **For REPL-driven development:**
   - Invoke the `clojure-repl` skill when testing code interactively
   - Prototype in REPL first, then save to files using `clojure-edit`

Example workflow:
```bash
# 1. Invoke clojure-edit skill
/clojure-edit

# 2. Read function signatures
scripts/read_clojure_file.clj src/esp1/fsr/compile.clj --form-type defn --collapsed

# 3. Edit a specific function by name
scripts/edit_clojure_form.clj --file src/esp1/fsr/compile.clj \
  --name compile-static-html \
  --operation replace \
  --new-form "(defn compile-static-html [root publish-dir] ...)"
```

## Testing

Test suite includes:
- **Unit tests**: `esp1.fsr.{core,compile,runtime,cache}-test`
- **Integration tests**: `esp1.fsr.integration-test` - full compilation & runtime flow
- **Example routes**: `test/foo/`, `test/bar/`, `test/baz/` demonstrate:
  - Simple routes: `test/foo/index.clj`
  - Path parameters: `test/bar/abc_<param1>_def_<<param2>>_xyz.clj`
  - Nested params: `test/baz/<<arg>>/moo.clj`
  - Date-based routes: `test/bar/2024_01_02_Some_Thing/index.clj`

Tests use Malli generative testing with custom file schema registry.

## Production Deployment

See [examples/README.md](examples/README.md) for complete deployment guide.

**Quick Overview:**
1. **Build**: `bb examples/build.clj` generates:
   - Static HTML files for GET routes → `dist/*.html`
   - Compiled routes for non-GET methods → `dist/compiled-routes.edn`

2. **Deploy**:
   - Static files → CDN/S3/static hosting
   - Compiled routes + handler code → application server

3. **Runtime**: Use `wrap-compiled-routes` middleware (no filesystem access)
