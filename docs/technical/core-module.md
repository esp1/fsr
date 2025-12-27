# Core Module

**Module**: `esp1.fsr.core` (`src/esp1/fsr/core.clj`)

Maps URIs to .clj files via filesystem traversal. Zero dependencies.

## Key Functions

**`uri->file`** - Match URI to file
- Split URI into segments, match each against filesystem (exact or `<param>`/`<<param>>`), return file + params
- Example: `/user/123/profile` → `user/<id>/profile.clj` + `{"id" "123"}`

**`get-root-ns-prefix`** - Extract namespace prefix from filesystem path
- Example: `src/my_app/routes` → `"my-app.routes"`

**`filename-match-info`** - Convert filename to regex pattern + param names
- `user_<id>` → `["^user_([^/]*)(/(.*))?$" ["id"]]`

**`file->clj`** - Resolve file/dir to .clj file
- Directory → prioritize `index.clj`, then `index.cljc`, then first `.clj`

## Patterns

**Regex construction**:
- Exact: `about` → `^about(/(.*))?$`
- Single param: `<id>` → `([^/]*)` (no slashes)
- Multi param: `<<path>>` → `(.*)` (with slashes)

**Namespace↔Filename**:
- Namespace `-` → filename `_`
- Namespace `.` → directory `/`
- `my-app.user.<id>` → `my_app/user/<id>.clj`

## Performance

- Filesystem calls: O(URI segments)
- Mitigated by caching (see cache-module.md)
- Namespace loaded lazily, metadata cached

## Related

- [../functional/uri-to-file-routing.md](../functional/uri-to-file-routing.md) - Requirements
- [cache-module.md](cache-module.md) - Caching
- [../functional/ring-middleware.md](../functional/ring-middleware.md) - Ring integration
