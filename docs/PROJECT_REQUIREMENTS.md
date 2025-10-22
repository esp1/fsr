# Project Requirements

## Product Vision

**fsr** (Filesystem Router) is a zero-dependency Clojure library that enables developers to organize web applications using a filesystem-based routing approach. The library maps incoming HTTP requests to Clojure namespace files based on directory structure, providing an intuitive way to build dynamic web applications and static sites.

## Core Value Proposition

- **Simplicity**: Route structure mirrors filesystem structure, making application organization intuitive
- **Flexibility**: Supports both dynamic web applications and static site generation
- **Zero Dependencies**: Core functionality has no external dependencies, ensuring minimal footprint
- **Developer Experience**: Convention-over-configuration approach reduces boilerplate

## High-Level Features

### 1. URI to File Route Resolution
[See detailed specification: spec/uri-to-file-routing.md](spec/uri-to-file-routing.md)

The system automatically maps incoming HTTP request URIs to Clojure namespace files within a configured root directory. This includes:
- Direct file matching (`/foo` → `foo.clj`)
- Index file matching (`/foo` → `foo/index.clj`)
- Automatic conversion between URI segments and Clojure naming conventions

### 2. Dynamic Path Parameters
[See detailed specification: spec/uri-to-file-routing.md#path-parameters](spec/uri-to-file-routing.md)

Support for extracting dynamic values from URIs using parameter notation:
- Single angle brackets `<param>` for values without slashes
- Double angle brackets `<<param>>` for values that may contain slashes
- Parameters are extracted and provided to handlers as string key-value maps

### 3. Namespace Metadata System
[See detailed specification: spec/namespace-metadata.md](spec/namespace-metadata.md)

A metadata-driven system for configuring route handlers:
- `:endpoint/http` - Maps HTTP methods to handler functions
- `:endpoint/type` - Enables custom template delegation
- Metadata is merged into request maps for handler access

### 4. Ring Middleware Integration
[See detailed specification: spec/ring-middleware.md](spec/ring-middleware.md)

Seamless integration with Ring-based web applications:
- Middleware wrapper for easy integration
- Hot-reload support in development mode
- Standard Ring request/response handling

### 5. Static Site Generation
[See detailed specification: spec/static-site-generation.md](spec/static-site-generation.md)

Generate complete static websites from dynamic route handlers:
- Automatic discovery of GET endpoints
- URI tracking for dynamic path parameters
- File output organized to mirror URI structure

### 6. Production Route Compilation
[See detailed specification: spec/compiled-route-production.md](spec/compiled-route-production.md)

Compile filesystem routes into optimized production artifacts:
- GET routes rendered to static HTML files
- Non-GET routes compiled to efficient lookup data structures
- Zero runtime filesystem scanning
- Minimal deployment footprint (no source files needed)

### 7. Route Caching System
[See detailed specification: spec/route-caching.md](spec/route-caching.md)

Performance optimization through intelligent route caching:
- Automatic caching of route resolution results
- Cache invalidation on file changes (dev mode)
- Reduced filesystem operations for frequently accessed routes

## User Personas

### Primary: Web Application Developer
A Clojure developer building dynamic web applications who values:
- Clean code organization that mirrors site structure
- Minimal configuration and boilerplate
- Fast development iteration with hot-reload
- Flexibility to use custom templates and layouts

### Secondary: Static Site Author
A developer or content creator building static websites who needs:
- Simple content organization using filesystem conventions
- Ability to preview dynamic site during development
- One-command static site generation for deployment
- Support for dynamic content like blogs with date-based URLs

## Success Criteria

- Developers can organize routes intuitively using filesystem structure
- Zero external dependencies in core library maintains minimal footprint
- Ring integration enables easy adoption in existing applications
- Static site generation produces deployable output without additional processing
- Development workflow supports hot-reload for rapid iteration

## Dependencies and Assumptions

### Assumptions
- Users are familiar with Clojure and Ring web applications
- Deployment environments can serve static files or run Ring applications
- Route organization follows filesystem naming conventions

### Dependencies
- **Core**: Zero external dependencies (principle from project constitution)
- **Development**: tools.namespace (hot-reload), Malli (schema validation)
- **Documentation**: Codox (API docs)
- **Runtime**: Ring specification (for middleware integration)

## Out of Scope

The following are explicitly not part of this project:
- Authentication/authorization systems
- Database integration or ORM functionality
- Client-side routing or JavaScript framework integration
- Built-in templating language (users provide their own via namespace functions)
- Server-side session management
- WebSocket or real-time communication support
