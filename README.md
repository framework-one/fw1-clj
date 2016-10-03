FW/1 in Clojure [![Join the chat at https://gitter.im/framework-one/fw1-clj](https://badges.gitter.im/framework-one/fw1-clj.svg)](https://gitter.im/framework-one/fw1-clj?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
===============

This is based on a port from CFML to Clojure of Framework One (FW/1). Most concepts carry over but, like Clojure itself, the emphasis is on simplicity.

FW/1 in Clojure is based on [Ring](https://github.com/ring-clojure/ring), [Compojure](https://github.com/weavejester/compojure), and [Selmer](https://github.com/yogthos/Selmer).
FW/1 is a lightweight, (partially) convention-based MVC framework.

The easiest way to get started with FW/1 is to use the
[fw1-template](https://github.com/framework-one/fw1-template) template
for Boot. The template can create a basic FW/1 skeleton
project for you that "just works" and provides the directory structure
and some basic files for you to get started with.

Assuming you have [Boot](http://boot-clj.com) installed, you can create a new skeleton FW/1 app like this:

    boot -d seancorfield/boot-new new -t fw1 -n myfw1app

This will create a skeleton FW/1 app in the `myfw1app` folder. You can run it like this:

    cd myfw1app
    boot run -p 8111

If you omit the `-p` / `--port` argument, it will default to port 8080, unless overridden by an environment variable:

    PORT=8111 boot run

URL Structure
-------------

In a FW/1 application, Controller functions and Views are automatically located based on standard patterns - with site sections and items within each section. Layouts are applied, if provided, in a cascade from item to section to site. You specify the site and item as a namespaced keyword `:section/item` and FW/1 will locate Controllers, Views, and Layouts based on that.

Actual URL route processing is handled via Compojure and FW/1 provides a default set of routes that should serve most purposes. The `usermanager` example leverages that default in the `fw1-handler` function:

``` clojure
(defn fw1-handler
  "Build the FW/1 handler from the application. This is where you can
  specify the FW/1 configuration and the application routes."
  [application]
  (fw1/default-handler application
                       {:application-key "usermanager"
                        :home            "user.default"}))
```

The default handler behavior is equivalent to this:

``` clojure
(defn fw1-handler
  "Build the FW/1 handler from the application. This is where you can
  specify the FW/1 configuration and the application routes."
  [application]
  (let-routes [fw1 (fw1/configure-router {:application     application
                                          :application-key "usermanager"
                                          :home            "user.default"})]
    (route/resources "/")
    (ANY "/" [] (fw1))
    (context "/:section" [section]
             (ANY "/"             []     (fw1 (keyword section)))
             (ANY "/:item"        [item] (fw1 (keyword section item)))
             (ANY "/:item/id/:id" [item] (fw1 (keyword section item))))))
```

As above, the handler is initialized with an application Component. It obtains a router from FW/1 by providing configuration for FW/1. It then defines routes using Compojure, starting with a general `resources` route, followed by a few standard route patterns that map to `:section/item` keywords.

Project Structure
-----------------

The standard file structure for a FW/1 application is:

* `controllers/` - contains a `.clj` file for each _section_ that needs business logic.
* `layouts/` - contains per-_item_, per-_section_ and per-site layouts as needed.
* `views/` - contains a folder for each _section_, containing an HTML view for each _item_.

Your Model can be anywhere since it will be `require`d into your controller namespaces as needed.

Request Lifecycle
-----------------

Controllers can have _before(rc)_ and _after(rc)_ handler functions that apply to all requests in a _section_.

A URL of `/section/item` will cause FW/1 to call:

* `(controllers.section/before rc)`, if defined.
* `(controllers.section/item rc)`, if defined.
* `(controllers.section/after rc)`, if defined.

A handler function should return the `rc`, updated as necessary. Strictly speaking, FW/1 will also call any `:before` / `:after` handlers defined in the configuration -- see below.

Then FW/1 will look for an HTML view template:

* `views/section/item.html`

The suffix can be controlled by the `:suffix` configuration option but defaults to `"html"`.

FW/1 looks for a cascade of layouts (again, the suffix configurable):

* `layouts/section/item.html`,
 * Replacing `{{body}}` with the view (and not calling any transforms).
* `layouts/section.html`,
 * Replacing `{{body}}` with the view so far.
* `layouts/default.html`,
 * Replacing `{{body}}` with the view so far. The `:layout` configuration is ignored.

Framework API
-------------

Any controller function also has access to the the FW/1 API (after `require`ing `framework.one`):

* `(cookie rc name)` - returns the value of `name` from the cookie scope.
* `(cookie rc name value)` - sets `name` to `value` in the cookie scope, and returns the updated `rc`.
* `(event rc name)` - returns the value of `name` from the event scope (`:action`, `:section`, `:item`, or `:config`).
* `(event rc name value)` - sets `name` to `value` in the event scope, and returns the updated `rc`.
* `(flash rc name)` - returns the value of `name` from the flash scope.
* `(flash rc name value)` - sets `name` to `value` in the flash scope, and returns the updated `rc`.
* `(header rc name)` - return the value of the `name` HTTP header, or `nil` if no such header exists.
* `(header rc name value)` - sets the `name` HTTP header to `value` for the response, and returns the updated `rc`.
* `(redirect rc url)` - returns `rc` containing information to indicate a redirect to the specified `url`.
* `(reload? rc)` - returns `true` if the current request includes URL parameters to force an application reload.
* `(remote-addr rc)` - returns the IP address of the remote requestor (if available).
* `(render-xxx rc data)` or `(render-xxx rc status data)` - render the specified `data`, optionally with the specified `status` code, in format _xxx_: `html`, `json`, `text`, `xml`.
* `(servlet-request rc)` - returns a "fake" `HttpServletRequest` object that delegates `getParameter` calls to pull data out of `rc`; used for interop with other HTTP-centric libraries.
* `(session rc name)` - returns the value of `name` from the session scope.
* `(session rc name value)` - sets `name` to `value` in the session scope, and returns the updated `rc`.
* `(to-long val)` - converts `val` to a long, returning zero if it cannot be converted (values in `rc` come in as strings so this is useful when you need a number instead and zero can be a sentinel for "no value").

The following symbols from Selmer are exposed as aliases via the FW/1 API:

* `add-tag!`, `add-filter!`

Application Startup & Configuration
-----------------------------------

As noted above, you can start the server on port 8080, running the User Manager example if you cloned this repository, with:

    boot run

You can specify a different port like this:

    boot run -p 8111

or:

    PORT=8111 boot run

In your main namespace -- `main.clj` in the example here -- the call to `(fw1/configure-router)` can be passed configuration parameters either
as a map (preferred) or as an arbitrary number of inline key / value pairs (legacy support):

* `:after` - a function (taking / returning `rc`) which will be called after invoking any controller
* `:application-key` - the namespace prefix for the application, default none.
* `:before` - a function (taking / returning `rc`) which will be called before invoking any controller
* `:default-section` - the _section_ used if none is present in the URL, default `"main"`.
* `:default-item` - the _item_ used if none is present in the URL, default `"default"`.
* `:error` - the action - _"section.item"_ - to execute if an exception is thrown from the initial request, defaults to `:default-section` value and `"error"` _[untested]_.
* `:home` - the _"section.item"_ pair used for the / URL, defaults to `:default-section` and `:default-item` values.
* `:json-config` - specify formatting information for Cheshire's JSON `generate-string`, used in `render-json` (`:date-format`, `:ex`, `:key-fn`, etc).
* `:middleware-default-fn` - an optional function that will be applied to Ring's site defaults; note that by default we do **not** enable the XSRF Anti-Forgery middleware that is normally part of the site defaults since that requires session scope and client knowledge which is not appropriate for many uses of FW/1. Specify `#(assoc-in [:security :anti-forgery] true)` here to opt into XSRF Anti-Forgery (you'll probably also want to change the :session :store from the in-memory default unless you have just a single server instance).
* `:options-access-control` - specify what an `OPTIONS` request should return (`:origin`, `:headers`, `:credentials`, `:max-age`).
* `:password` - specify a password for the application reload URL flag, default `"secret"` - see also `:reload`.
* `:reload` - specify an `rc` key for the application reload URL flag, default `:reload` - see also `:password`.
* `:reload-application-on-every-request` - boolean, whether to reload controller, view and layout components on every request (intended for development of applications).
* `:routes` - a vector of hash maps, specifying route patterns and what to map them to (full documentation coming in due course).
* `:selmer-tags` - you can specify a map that is passed to the Selmer parser to override what characters are used to identify tags, filters
* `:suffix` - the file extension used for views and layouts. Default is `"html"`.

For example: `(fw1/configure-router {:default-section "hello" :default-item "world"})` will tell FW/1 to use `hello.world` as the default action.

License & Copyright
===================

Copyright (c) 2015-2016 Sean Corfield.

Distributed under the Apache Source License 2.0.
