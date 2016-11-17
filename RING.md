# FW/1 and Ring

FW/1 repackages a Ring request to pass it to controllers. Specifically, the Ring `:params` keys is used as the core, with the original Ring request embedded, and then optionally adding `::abort`, `::event`, `::redirect`, and `::render` keys as needed.

Controllers are passed this hybrid map and return an updated version of it.

If it has one of the "magic" keys, FW/1 turns that into a redirect or rendering response. Otherwise, FW/1 looks for views based on the action, then layouts, and produces a regular (HTML) response.

Middleware can decide whether or not to call the handler. FW/1 could be middleware that calls `:before`, `before()`, the item controller itself, `after()`, and `:after` -- and decide whether / if to call the handler somewhere in there perhaps? After calling the handler (and the controllers), FW/1 could decide to run the view / layout pipeline to produce a response.

