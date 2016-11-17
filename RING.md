# FW/1 and Ring

FW/1 repackages a Ring request to pass it to controllers. Specifically, the Ring `:params` keys is used as the core, with the original Ring request embedded, and then optionally adding `::abort`, `::event`, `::redirect`, and `::render` keys as needed.

Controllers are passed this hybrid map and return an updated version of it.

If it has one of the "magic" keys, FW/1 turns that into a redirect or rendering response. Otherwise, FW/1 looks for views based on the action, then layouts, and produces a regular (HTML) response.

Middleware can decide whether or not to call the handler. FW/1 could be middleware that calls `:before`, `before()`, the item controller itself, `after()`, and `:after` -- and decide whether / if to call the handler somewhere in there perhaps? After calling the handler (and the controllers), FW/1 could decide to run the view / layout pipeline to produce a response.

The question at this point is, what would become the handler in this model?

The current implementation uses a (configured) router that accepts a keyword that determines the section / item, and the section determines where to find the `before()` and `after()` functions.

If the handler was a simple Var, that could take the place of the keyword. But that wouldn't allow for FW/1 to wrap other middleware, making it a brittle solution. If the handler was a general request-handling function, what would be lost in FW/1? The automatically deduced `before()` and `after()` functionality (but the overall `:before` and `:after` hooks would still be present); the item controller would need to become a normal Ring handler, which could update into Ring's `:params` directly (or just add keys to the Ring request itself?); redirects, aborts, and render requests would need to be detectable by FW/1 based on normal Ring requests, to determine whether to run the view / layout pipeline.

Need to examine how other middleware deals with this logic!
