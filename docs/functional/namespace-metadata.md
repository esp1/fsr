# Namespace Metadata

## Purpose

Configure route handlers declaratively using Clojure namespace metadata.

## Value

Route configuration lives alongside code without external config files—idiomatic Clojure.

## Requirements

**HTTP Method Mapping** - `:endpoint/http` maps HTTP method keywords (`:get`, `:post`, etc.) to handler function symbols.

```clojure
(ns my-app.routes.users
  {:endpoint/http {:get 'GET-users, :post 'POST-create-user}})
```

**Template Delegation** - `:endpoint/type` delegates handler resolution to another namespace. Template receives original namespace via `:endpoint/ns`.

```clojure
(ns my-app.routes.blog.post-1
  {:endpoint/type 'my-app.templates.blog-post
   :blog/title "My First Post"})
```

**Request Enrichment** - Namespace metadata merged into request map. Standard Ring keys preserved.

**Matched Namespace** - `:endpoint/ns` always contains the matched namespace symbol (original, not template).

**Handler Resolution** - Symbols resolved via `ns-resolve`. Private functions allowed.

**Custom Metadata** - Arbitrary keys allowed for app-specific config (e.g., `:cache/ttl`, `:auth/required`).

## Edge Cases

- Missing HTTP method → 405 Method Not Allowed
- Circular template references → detect and prevent infinite loops
- Invalid function symbols → clear error message
- Nil metadata → handle gracefully
- Conflicting keys → system keys (`:endpoint/*`) override custom metadata

## Template Flow

1. URI matches namespace A
2. A has no matching `:endpoint/http` method
3. Read `:endpoint/type` → namespace B
4. Look up `:endpoint/http` in B
5. Invoke B's handler with `:endpoint/ns` = A and A's metadata merged

## Related

- **[URI to File Routing](uri-to-file-routing.md)** - How namespaces are matched
- **[Ring Middleware](ring-middleware.md)** - How handlers are invoked
