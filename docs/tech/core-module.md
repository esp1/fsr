# Technical Documentation: Core Module

**Module**: `esp1.fsr.core`
**File**: `src/esp1/fsr/core.clj`
**Purpose**: Core routing logic for URI to filesystem resolution

## Overview

The core module implements the fundamental routing algorithm that maps HTTP request URIs to Clojure namespace files. This module has zero external dependencies and provides the foundation for all other fsr functionality.

## Key Concepts

### URI to File Resolution
The core algorithm performs step-by-step matching of URI segments against filesystem entries, supporting both static filenames and dynamic path parameters.

### Path Parameters
Two types of path parameters are supported:
- `<param>` - Matches values without slashes (regex: `[^/]*`)
- `<<param>>` - Matches values with slashes (regex: `.*`)

### Namespace Resolution
File paths are converted to Clojure namespace symbols, with special handling for path parameters embedded in namespace names.

## Architecture

### Main Functions

#### `uri->file`
Maps a URI to a matching .clj file under the root filesystem path.

**Signature**:
```clojure
(uri->file root-fs-path uri)
→ {:file java.io.File
   :path-params {String String}}
```

**Algorithm**:
1. Start at root-fs-path
2. Split URI into segments
3. For each segment:
   - Try to match against filenames in current directory
   - Support both exact matches and parameter patterns
   - Move into matched directory or file
4. Accumulate path parameters during traversal
5. Return matched file and parameters map

**Example**:
```clojure
(uri->file "src/routes" "/user/123/profile")
;; Matches: src/routes/user/<id>/profile.clj
;; Returns: {:file #<File ...>, :path-params {"id" "123"}}
```

#### `get-root-ns-prefix`
Determines the namespace prefix for a root filesystem path by scanning for a Clojure file and extracting the corresponding namespace portion.

**Signature**:
```clojure
(get-root-ns-prefix root-fs-path)
→ String
```

**Implementation**:
```clojure
(defn get-root-ns-prefix [root-fs-path]
  (str/join "." (file-ns-name-components (io/file root-fs-path))))
```

**Example**:
```clojure
(get-root-ns-prefix "src/my_app/routes")
→ "my-app.routes"
```

#### `step-match`
Matches a single URI segment against filenames in a directory, handling both static names and parameter patterns.

**Signature**:
```clojure
(step-match uri cwd)
→ [remaining-uri matched-file path-params-map] | nil
```

**Algorithm**:
1. List all files in current working directory (cwd)
2. For each file, extract match string (namespace component or filename)
3. Convert match string to regex pattern (handling `<param>` and `<<param>>`)
4. Test URI against pattern
5. If match found, extract parameter values
6. Return remaining URI, matched file, and parameters
7. If no match, return nil

**Pattern Conversion Examples**:
- `about` → `^about(/(.*))?$` (exact match)
- `user_<id>` → `^user_([^/]*)(/(.*))?$` (single param)
- `article_<<path>>` → `^article_(.*)(/(.*))?$` (multi-segment param)

### Helper Functions

#### `clojure-file-ext`
Returns the extension if the file is `.clj` or `.cljc`, otherwise nil.

```clojure
(clojure-file-ext "routes.clj") → ".clj"
(clojure-file-ext "routes.txt") → nil
```

#### `file-ns-name-components`
Extracts namespace components from a file by reading the `ns` declaration.

```clojure
(file-ns-name-components (io/file "src/my_app/routes/about.clj"))
→ ["my-app" "routes" "about"]
```

#### `filename-match-info`
Converts a filename with parameter notation into a regex pattern and parameter name vector.

**Signature**:
```clojure
(filename-match-info filename)
→ [regex-pattern param-names-vector]
```

**Examples**:
```clojure
(filename-match-info "user_<id>")
→ ["^user_([^/]*)(/(.*))?$" ["id"]]

(filename-match-info "category_<cat>_article_<<path>>")
→ ["^category_([^/]*)_article_(.*)(/(.*))?$" ["cat" "path"]]
```

## Data Structures

### Route Resolution Result
```clojure
{:file #<File "/path/to/route.clj">
 :path-params {"id" "123"
               "section" "profile"}}
```

### Namespace Metadata (read from route files)
```clojure
{:endpoint/http {:get 'GET-handler
                 :post 'POST-handler}
 :endpoint/type 'template.namespace
 ;; Custom metadata
 :page/title "About Us"}
```

## Implementation Details

### Regex Pattern Construction

The algorithm constructs regex patterns to match URI segments against filenames:

1. **Exact match**: `filename` → `^filename(/(.*))?$`
2. **Single param**: `<id>` → `([^/]*)`
3. **Multi param**: `<<path>>` → `(.*)`

The trailing `(/(.*))?` captures any remaining URI after the match.

### Parameter Extraction

When a pattern matches, captured groups are mapped to parameter names:

```clojure
(let [pattern #"^user_([^/]*)(/(.*))?$"
      matcher (re-matcher pattern "user_123/profile")
      [_ id _ remaining] (re-find matcher)]
  {:id id, :remaining remaining})
→ {:id "123", :remaining "profile"}
```

### Namespace to File Conversion

Clojure namespace naming conventions:
- Dashes (`-`) in namespace → underscores (`_`) in filename
- Dots (`.`) in namespace → directory separators (`/`) in path
- Angle brackets preserved in namespace symbols

Example conversions:
| Namespace | Filename |
|-----------|----------|
| `my-app.routes.about` | `my_app/routes/about.clj` |
| `my-app.routes.user.<id>` | `my_app/routes/user/<id>.clj` |
| `my-app.routes.article.<<path>>` | `my_app/routes/article/<<path>>.clj` |

## Error Handling

### Common Errors

1. **Multiple Matches**: If multiple files match the same URI pattern, an error is thrown
2. **No Match**: Returns nil, allowing fallback to wrapped handler
3. **Invalid Root Path**: Error on initialization if root path doesn't exist
4. **Malformed Namespace**: Error if namespace can't be read from file

### Error Messages

Errors include context about:
- The URI being matched
- The current directory being scanned
- The files considered for matching

## Performance Considerations

### Filesystem Access
- Each route resolution requires filesystem listing operations
- Number of filesystem calls = number of URI segments + 1
- Mitigated by route caching (see cache module)

### Namespace Loading
- Namespaces are loaded on first access
- Metadata cached after load
- Hot-reload invalidates caches in development

### Regex Compilation
- Patterns compiled on each match (could be optimized)
- Pattern complexity is O(n) where n = number of files in directory

## Testing

### Test Coverage
- Unit tests for pattern matching logic (see test/esp1/fsr/core_test.clj)
- Example routes demonstrate various patterns (test/foo/, test/bar/, etc.)
- Malli schema tests validate function contracts

### Example Test Routes
```
test/
├── foo/index.clj                           # /foo
├── bar/
│   ├── abc_<param1>_def_<<param2>>_xyz.clj # /bar/abc/{x}/def/{y}/xyz
│   └── 2024_01_02_Some_Thing/index.clj     # /bar/2024/01/02/Some/Thing
└── baz/<<arg>>/moo.clj                     # /baz/{anything}/moo
```

## Integration with Other Modules

### With Ring Module
- Ring module calls `uri->file` to resolve routes
- Result is used to load namespace and find handler
- Path params are added to request map

### With Cache Module
- Core functions are wrapped by cache lookups
- Cache stores results of `uri->file` calls
- `clear-route-cache!` delegates to cache module

### With Static Module
- Static generation uses same resolution logic
- Scans filesystem to enumerate routes
- Invokes handlers for each discovered route

## Extension Points

### Custom Matching Logic
While not currently supported, custom matching logic could be added by:
- Extending `filename-match-info` to support new parameter syntax
- Adding new match strategies in `step-match`
- Implementing plugin system for custom resolvers

### Custom Namespace Loading
Currently uses standard Clojure namespace loading, but could be extended to:
- Support alternative namespace formats
- Add namespace validation hooks
- Implement custom metadata resolution

## Code Example

Here's a simplified version of the core algorithm:

```clojure
(defn uri->file [root-fs-path uri]
  (loop [uri uri
         cwd (io/file root-fs-path)
         params {}]
    (if (empty? uri)
      {:file cwd :path-params params}
      (if-let [[remaining-uri matched-file new-params] (step-match uri cwd)]
        (recur remaining-uri matched-file (merge params new-params))
        nil))))

(defn step-match [uri cwd]
  (let [files (.listFiles cwd)]
    (some (fn [f]
            (let [[pattern param-names] (filename-match-info (.getName f))]
              (when-let [match (re-find (re-pattern pattern) uri)]
                (let [param-values (take (count param-names) (rest match))
                      remaining-uri (last match)]
                  [remaining-uri f (zipmap param-names param-values)]))))
          files)))
```

## Related Documentation

- [URI to File Routing Spec](../spec/uri-to-file-routing.md) - Requirements for this module
- [Cache Module](cache-module.md) - Performance optimization
- [Ring Integration](ring-integration.md) - How this is used in requests
