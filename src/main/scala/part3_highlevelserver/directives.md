# Notes on directives

---

## What a route is
- A RequestContext contains
  - the HttpRequest being handled
  - the actor system
  - the actor materializer
  - the logging adapter
  - routing settings
  - etc
- This is the data structure handled by a Route
- Most cases you will not need to build a request context yourself

## What a Route is 2
- Directives create Routes; composing routes creates a routing tree
  - filtering and nesting
  - chaining with ~
  - extracting data
- What a route can do with a RequestContext:
  - complete it synchronously with a response
  - complete it asynchronously with a Future(response)
  - handle it asynchronously by returning a Source(advanced)
  - reject it and pass it onto the next Route
  - fail it