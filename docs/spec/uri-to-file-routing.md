# Feature Specification: URI to File Routing

## Overview

The URI to File Routing feature enables automatic mapping of HTTP request URIs to Clojure namespace files based on filesystem structure. This is the core capability that makes fsr a filesystem router.

## User Value

Developers can organize their web application code using a filesystem structure that directly mirrors their website's URL structure. This creates an intuitive mental model where the location of a file immediately tells you what URL it handles.

## User Stories

### Primary Story
**As a** web application developer
**I want** my URIs to automatically map to .clj files in my source directory
**So that** I can organize my application logic using a familiar filesystem-based approach

### Supporting Stories

1. **Simple Route Matching**
   - **As a** developer
   - **I want** `/foo` to map to either `foo.clj` or `foo/index.clj`
   - **So that** I have flexibility in organizing my route files

2. **Dash/Underscore Conversion**
   - **As a** developer
   - **I want** URIs with dashes like `/foo-bar` to map to `foo_bar.clj`
   - **So that** my URIs can use idiomatic web conventions while my filenames follow Clojure conventions

3. **Dynamic Path Parameters (No Slashes)**
   - **As a** developer
   - **I want** to use `<id>` in filenames to match URI segments without slashes
   - **So that** I can handle routes like `/user/123` with a single file

4. **Dynamic Path Parameters (With Slashes)**
   - **As a** developer
   - **I want** to use `<<title>>` in filenames to match URI segments that may contain slashes
   - **So that** I can handle routes like `/article/2024/01/my-post` with flexible path structures

## Functional Requirements

### FR-001: Basic URI to File Mapping
The system MUST map incoming HTTP request URIs to corresponding .clj files within a configured root filesystem path.

**Acceptance Criteria**:
- Given a root path `src/routes` and URI `/about`, the system locates `src/routes/about.clj`
- The system performs case-sensitive matching
- The system only matches files with `.clj` or `.cljc` extensions

### FR-002: Index File Support
The system MUST support index file matching where a URI can map to an `index.clj` file in a directory.

**Acceptance Criteria**:
- Given URI `/products`, the system matches either `products.clj` OR `products/index.clj`
- If both exist, direct file match (`products.clj`) takes precedence
- When resolving a directory to a namespace file, `index.clj` MUST be prioritized over other `.clj` files in the directory
- If no `index.clj` exists, the system falls back to the first `.clj` file found
- Index files allow nested route organization

**Test Coverage**: T101 (`file->clj-prioritizes-index-test` in `core_test.clj`)
### FR-003: Dash to Underscore Conversion
### FR-003: Dash to Underscore Conversion
The system MUST convert dashes in URIs to underscores when matching filenames, following Clojure namespace naming conventions.

**Acceptance Criteria**:
- URI `/foo-bar` matches file `foo_bar.clj`
- URI `/my-long-route` matches file `my_long_route.clj`
- The conversion only applies to filenames, not directory names

### FR-004: Single Angle Bracket Parameters
The system MUST extract path parameters using single angle bracket notation `<param>` for values that do not contain forward slashes.

**Acceptance Criteria**:
- File `thing/<id>.clj` matches URI `/thing/123` with parameter `{"id" "123"}`
- The pattern does NOT match URIs with slashes in the parameter position
- Parameter names can contain alphanumeric characters and underscores
- Multiple parameters can exist in a single route

### FR-005: Double Angle Bracket Parameters
The system MUST extract path parameters using double angle bracket notation `<<param>>` for values that may contain any characters including forward slashes.

**Acceptance Criteria**:
- File `article/<<path>>.clj` matches `/article/2024/01/my-post` with parameter `{"path" "2024/01/my-post"}`
- Double angle bracket parameters can only appear at the end of a route pattern
- The pattern matches everything remaining in the URI

### FR-006: Path Parameter Extraction
The system MUST provide extracted path parameters to handler functions via the `:endpoint/path-params` key in the request map.

**Acceptance Criteria**:
- Parameters are provided as a map of string keys to string values
- Parameter names match the names specified in the angle brackets
- The map is empty if no parameters are present in the route
- Parameters preserve their extracted string values without type conversion

### FR-007: Namespace Symbol Resolution
The system MUST convert filesystem paths to valid Clojure namespace symbols for handler resolution.

**Acceptance Criteria**:
- File path `routes/user/<id>.clj` converts to namespace `routes.user.<id>`
- Angle bracket parameters are preserved in namespace symbols
- The conversion handles nested directory structures correctly

### FR-008: Matched Namespace Metadata
The system MUST provide the matched namespace symbol to handlers via the `:endpoint/ns` key in the request map.

**Acceptance Criteria**:
- The namespace symbol corresponds to the .clj file that matched the URI
- Handlers can use this to reflect on their own namespace
- Templates can use this to delegate back to the original matched namespace

## Non-Functional Requirements

### Performance
- Route matching should complete in under 10ms for typical route structures
- Caching should be utilized to avoid repeated filesystem scans

### Reliability
- Missing files should fail gracefully with clear error messages
- Invalid namespace structures should be detected and reported
- Malformed URIs should be handled without crashing

### Maintainability
- Route resolution logic should be isolated from Ring integration
- Algorithm should be testable independent of HTTP concerns

## Edge Cases

1. **Multiple Matches**: If both `foo.clj` and `foo/index.clj` exist, `foo.clj` takes precedence
2. **Special Characters**: URIs with special characters should be URL-decoded before matching
3. **Empty Parameters**: Parameters cannot match empty strings (e.g., `/user//profile` does not match `/user/<id>/profile`)
4. **Conflicting Parameters**: A route cannot have both `<id>` and `<<id>>` patterns - the more specific pattern wins
5. **Case Sensitivity**: Matching is case-sensitive for both URIs and filenames

## Dependencies

- Requires configured root filesystem path
- Depends on Clojure namespace loading mechanism
- Integrates with route cache module for performance

## Related Requirements

- [Namespace Metadata System](namespace-metadata.md) - Describes how handlers are found within matched namespaces
- [Ring Middleware Integration](ring-middleware.md) - Describes how route resolution integrates with Ring
- [Route Caching](route-caching.md) - Describes performance optimization for route resolution
