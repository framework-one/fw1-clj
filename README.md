FW/1 in Clojure [![Join the chat at https://gitter.im/frameork-one/fw1-clj](https://badges.gitter.im/frameork-one/fw1-clj.svg)](https://gitter.im/frameork-one/fw1-clj?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
===============

This is a port from CFML to Clojure of Framework One (FW/1).

FW/1 in Clojure is based on [Ring](https://github.com/ring-clojure/ring) and [Selmer](https://github.com/yogthos/Selmer).
FW/1 is a lightweight, convention-based MVC framework.

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

In a FW/1 application, Controller functions and Views are automatically located based on standard URL patterns - with site sections and items within each section. Layouts are applied, if provided, in a cascade from item to section to site.

The basic URL pattern is: `/section/item/arg1/value1/arg2/value2?arg3=value3`

The arg / value pairs from the URL are assembled into a map called the request context (and referred to as `rc` in the documentation).

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
* `(event rc name)` - returns the value of `name` from the event scope (`:action`, `:section`, `:item, or `:config`).
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

By default, FW/1 adds `empty?` as a filter with the same name so the following is possible out of the box:

    {% if some-var|empty? %}There are none!{% endif %}

Application Startup & Configuration
-----------------------------------

As noted above, you can start the server on port 8080, running the User Manager example if you cloned this repository, with:

    boot run

You can specify a different port like this:

    boot run -p 8111

or:

    PORT=8111 boot run

In your main namespace -- `main.clj` in the example here -- the call to `(fw1/start)` can be passed configuration parameters either
as a map (preferred) or as an arbitrary number of inline key / value pairs (legacy support):

* `:after` - a function (taking / returning `rc`) which will be called after invoking any controller
* `:application-key` - the namespace prefix for the application, default none.
* `:before` - a function (taking / returning `rc`) which will be called before invoking any controller
* `:default-section` - the _section_ used if none is present in the URL, default `"main"`.
* `:default-item` - the _item_ used if none is present in the URL, default `"default"`.
* `:error` - the action - _"section.item"_ - to execute if an exception is thrown from the initial request, defaults to `:default-section` value and `"error"` _[untested]_.
* `:home` - the _"section.item"_ pair used for the / URL, defaults to `:default-section` and `:default-item` values.
* `:json-config` - specify formatting information for Cheshire's JSON `generate-string`, used in `render-json` (`:date-format`, `:ex`, `:key-fn`, etc).
* `:middleware` - specify additional Ring-compatible middleware to use. By default, this is prepended to the default list (params, flash, session, resource). The behavior can be changed by specifying a keyword at the start of the list of additional middleware: `:append`, `:prepend`, `:replace`.
* `:options-access-control` - specify what an `OPTIONS` request should return (`:origin`, `:headers`, `:credentials`, `:max-age`).
* `:password` - specify a password for the application reload URL flag, default `"secret"` - see also `:reload`.
* `:reload` - specify an `rc` key for the application reload URL flag, default `:reload` - see also `:password`.
* `:reload-application-on-every-request` - boolean, whether to reload controller, view and layout components on every request (intended for development of applications).
* `:routes` - a vector of hash maps, specifying route patterns and what to map them to (full documentation coming in due course).
* `:selmer-tags` - you can specify a map that is passed to the Selmer parser to override what characters are used to identify tags, filters
* `:session-store` - specify storage used for Ring session storage. Legal values are `:memory` and `:cookie`. Default is whatever is Ring's default (which is memory storage as of this writing).
* `:suffix` - the file extension used for views and layouts. Default is `"html"`.

For example: `(fw1/start :default-section "hello" :default-item "world")` will tell FW/1 to use `hello.world` as the default action.
You could also say: `(fw1/start {:default-section "hello" :default-item "world"})`.

License & Copyright
===================

Copyright (c) 2015-2016 Sean Corfield.

Distributed under the Apache Source License 2.0.
