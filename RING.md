# FW/1 and Ring

FW/1 repackages a Ring request to pass it to controllers. Specifically, the Ring `:params` keys is used as the core, with the original Ring request embedded, and then optionally adding `::abort`, `::event`, `::redirect`, and `::render` keys as needed.

Controllers are passed this hybrid map and return an updated version of it.

If it has one of the "magic" keys, FW/1 turns that into a redirect or rendering response. Otherwise, FW/1 looks for views based on the action, then layouts, and produces a regular (HTML) response.

Middleware can decide whether or not to call the handler. FW/1 could be middleware that calls `:before`, `before()`, the item controller itself, `after()`, and `:after` -- and decide whether / if to call the handler somewhere in there perhaps? After calling the handler (and the controllers), FW/1 could decide to run the view / layout pipeline to produce a response.

The question at this point is, what would become the handler in this model?

The current implementation uses a (configured) router that accepts a keyword that determines the section / item, and the section determines where to find the `before()` and `after()` functions.

If the handler was a simple Var, that could take the place of the keyword. But that wouldn't allow for FW/1 to wrap other middleware, making it a brittle solution. If the handler was a general request-handling function, what would be lost in FW/1? The automatically deduced `before()` and `after()` functionality (but the overall `:before` and `:after` hooks would still be present); the item controller would need to become a normal Ring handler, which could update into Ring's `:params` directly (or just add keys to the Ring request itself?); redirects, aborts, and render requests would need to be detectable by FW/1 based on normal Ring requests, to determine whether to run the view / layout pipeline.

## FW/1 in the Wild

It's perhaps so obvious that it doesn't really need stating but there is almost no FW/1 code in production anywhere. Even at World Singles, our usage of FW/1 is relatively new and is currently only at the QA stage (as of 12/10/2016, but it will go to production shortly). Fundamentally changing how FW/1 works would inconvenience almost no one (although, due to World Singles' usage, I'll want a migration path for my own sanity!).

The `:before` hook is used in all World Singles' apps, the `:after` hook is used in none. The `before()` hook is used in several controllers (but not all). The `after()` hook is used in one controller. Refactoring all that code into single per-app `:before` and/or `:after` hooks would work just fine, and therefore that could be part of a middleware-based FW/1 approach without losing anything, as long as the `:section/item` concept was retained at the router level.

The `::abort` special key is only ever used after a `render-json` call when the intent is to stop processing. If the `render-*` functions actually added a response at the time, then using `ring.util.response/response?` would be a suitable way to "abort" further processing. The same goes for the `redirect` function, which could use `ring.util.response/redirect` directly.

## FW/1 as Middleware

It could call `:before`, and if that didn't produce a `response?` then it could call the handler, and if that didn't produce a `response?` then it could call `:after`, and if that didn't produce a `response?` then it could locate a template and render that as the response. We would no longer need the `:section/item` router for the `before()` or `after()` functions at this point.

Would the `:section/item` router idea need to be maintained? That is needed to locate the actual handler function, as well the view and layouts. That's the benefit of the conventions, but the downside is that you lose the composability of Ring's normal machinery. If we take away the router, then the handler could be a regular (explicit) Ring handler, specified in the route itself. The handler could indicate what `:section/item` to use for the view and layouts. The default could be the original `:uri` I suspect?

Since we only need the view and layout pipeline for certain applications, that could be its own middleware. That would simplify the `:before` and `:after` middleware logic. `(set-layout rc :section/item)` could set `::view` to handle this.

This line of thinking reduces FW/1 to two pieces of middleware, with the view and layout pipeline as the outermost and the before and after pipeline as the innermost (so that it occurs closest to the handler itself).

At this point, handlers (and indeed the before/after functions) could all accept a regular Ring request and return an updated request or a response. The only glitch at this point is that FW/1 controllers etc all expect a flat hash map of data (initially the `:params` of a Ring request) and they add to or remove from that, as it is used as a data bus through the whole framework. To address this, the FW/1 before/after middleware could merge the `:params` directly into the Ring request, which would preserve the current semantics, otherwise all controller code would need to change to use `(get-in req [:params k])` and `(assoc-in req [:params k] v)` instead of the current `(k rc)` and `(assoc rc k v)`.

FW/1 also merges `:flash` if present into that set of data, overwriting URL / form parameters. FW/1 also maintains a `:config` key of its own, which in current World Singles' apps contains the Application Component itself, as well as the `:application-key`. The latter is used for the namespace and file path stem to locate controllers and views and layouts. I am inclined to move those to the `resources` folder, out of the `src` tree and to simply lose the stem. The concept of `:application-key` exists to support multiple web applications under a single project root but I no longer consider that a good idea, and we're already using `resources/public` for web-accessible assets, which is a once per project concept.

## Migration Path

There are several changes required of user code in order to migrate from the status quo to the proposed future:
* Any `before()` function would need to be merged into a `:before` handler, and any `after()` function would need to be merged into an `:after` handler.
* Any `views` and `layouts` folders would need to move from `src/{stem}` to `resources`, and controllers would need to explicitly call the (new) `set-layout` function if they wanted a view rendered.
* Any route definitions would need to change to use explicit handler function references, wrapped in the appropriate middleware.
* Calls to most `fw1` namespace functions would go away.

Conversion of controllers (and before/after handlers) could be made less painful by a switch in the middleware that optionally merged `:params` (and `:flash`) into the main Ring request, and by a predicate that could tell whether the argument was a FW/1 request context or a plain Ring request.

The new namespaces will be `framework.one.middleware`, `framework.one.server`, `framework.one.request` (for the predicate mentioned above) and `framework.one.response`. The latter will contain the new `render-*` functions.

# A Critical Look At FW/1

Having now gone through the exercise of refactoring FW/1 into middleware and other components, it has become clear that the closer you get to "pure Ring", the less you need a framework at all. The before/after middleware I extracted is little more than a function you might write for each application:
```
(defn wrapper
  [handler]
  (fn [req]
    (let [r (before req)]
      (if (resp/response? r)
        r
        (after (handler r))))))
```
If you don't need either `before` or `after`, it gets simpler still.

The view/layout middleware is slightly more valuable but only if you have a traditional HTML application with a number of different views and need more than a simple layout wrapper. Even then, it's really only two or three calls to `selmer.parser/render-file` which could be wrapped up in a simple function for convenience.

The response side of things provides multiple rendering types but, in reality, you probably only need JSON and you can get that from `ring/ring-json` (`wrap-json-response`) which uses Cheshire, just like FW/1. FW/1 already uses this middleware for `wrap-json-params` to decode HTTP request bodies into the Ring `:params` map.

On the request side, the smarter `remote-addr` function is available via standard Ring middleware already (`wrap-forwarded-remote-addr` if you add `:proxy true` to the defaults' config). I just didn't realize that until today!

On the other hand, the `servlet-request` function that proxies an `HttpServletRequest` on top of a Ring request is not directly available: there's a private version of this inside `ring.util.test.servlet` but that doesn't implement `getParameter`. The latter could be provided as a very simple external library, with only `ring/ring-core` as a dependency for `ring.util.request` and `ring.util.response` utility functions (to properly handle `getContentType()` and `getHeader()`).

The WebServer Component is a useful way to encapsulate starting a Ring server with an Application Component as a dependency. `danielsz/system` has something similar for Jetty and http-kit but without the Application Component dependency and without the ability to easily select the container at runtime (and that library depends on Schema which I wouldn't want to pull in).

FW/1's default middleware stack is based on `ring/ring-defaults` and assumes a full application stack, which isn't really necessary for a REST API, but the minimal API stack doesn't include a number of things that actually are useful. Either way, building a middleware stack is easy enough with `ring/ring-defaults`.

Worth keeping:
* The `servlet-request` proxy function (which was written to support using the Apache OAuth2 library).
* The `web-server` machinery and the middleware-wraps-application concept.
* Perhaps an abstraction around the `ring-defaults` construction, that allows configuration tweaking?
