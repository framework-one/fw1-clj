FW/1 in Clojure
===============

This is a port from CFML to Clojure of Framework One (FW/1).

TL;DR - Want to try it out on Heroku? http://corfield.org/entry/fw-1-user-manager-example-on-heroku

FW/1 in Clojure is based on [Ring](https://github.com/ring-clojure/ring) and [Selmer](https://github.com/yogthos/Selmer).
FW/1 is a lightweight, convention-based MVC framework.
Controller functions and views are automatically selected based on standard URL patterns - with site sections and items within each section.
Layouts are applied, if provided, in a cascade from item to section to site.

The basic URL pattern is: `/section/item/arg1/value1/arg2/value2?arg3=value3</`

The arg / value pairs from the URL are assembled into a map called the request context (and referred to as `rc` in the documentation).

The standard file structure for a FW/1 application is:

* `controllers/` - contains a `.clj` file for each _section_ that needs business logic.
* `layouts/` - contains per-_item_, per-_section_ and per-site layouts as needed.
* `views/` - contains a folder for each _section_, containing an HTML view for each _item_.

The easiest way to get started with FW/1 is to use the
[fw1-template](https://github.com/framework-one/fw1-template) template
(plugin) for Leiningen. The template can create a basic FW/1 skeleton
project for you that "just works" and provides the directory structure
and some basic files for you to get started with. 

Controllers can have _before(rc)_ and _after(rc)_ handler functions that apply to all requests in a _section_.

A URL of <tt>/section/item</tt> will cause FW/1 to call:

* `(controllers.section/before rc)`, if defined.
* `(controllers.section/item rc)`, if defined.
* `(controllers.section/after rc)`, if defined.

A handler function should return the `rc`, updated as necessary.

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

Any controller function also has access to the the FW/1 API:

* `(cookie rc name)` - returns the value of `name` from the cookie scope
* `(cookie rc name value)` - sets `name` to `value` in the cookie scope, and returns the updated `rc`
* `(event rc name)` - returns the value of `name` from the event scope (`:action`, `:section`, `:item, or `:config`)
* `(event rc name value)` - sets `name` to `value` in the event scope, and returns the updated `rc`
* `(flash rc name)` - returns the value of `name` from the flash scope
* `(flash rc name value)` - sets `name` to `value` in the flash scope, and returns the updated `rc`
* `(redirect rc url)` - returns `rc` containing information to indicate a redirect to the specified `url`.
* `(reload? rc)` - returns `true` if the current request includes URL parameters to force an application reload.
* `(session rc name)` - returns the value of `name` from the session scope
* `(session rc name value)` - sets `name` to `value` in the session scope, and returns the updated `rc`
* `(to-long val)` - converts `val` to a long, returning zero if it cannot be converted (values in `rc` come in as strings so this is useful when you need a number instead and zero can be a sentinel for "no value").

The following symbols from Selmer are exposed as aliases via the FW/1 API:

* `add-tag!`, `add-filter!`

By default, FW/1 adds `empty?` as a filter with the same name so the following is possible out of the box:

    {% if some-var|empty? %}There are none!{% endif %}

You can start the server on port 8080 with:

    lein run -m usermanager.main

You can specify a different port like this:

    PORT=8111 lein run -m usermanager.main

In your main namespace, the call to `(fw1/start)` can be passed a map of configuration parameters:

* `:after` - a function (taking / returning `rc`) which will be called after invoking any controller
* `:application-key` - the namespace prefix for the application, default none.
* `:before` - a function (taking / returning `rc`) which will be called before invoking any controller
* `:default-section` - the _section_ used if none is present in the URL, default `"main"`.
* `:default-item` - the _item_ used if none is present in the URL, default `"default"`.
* `:error` - the action - _"section.item"_ - to execute if an exception is thrown from the initial request, defaults to `:default-section` value and `"error"` _[untested]_.
* `:home` - the _"section.item"_ pair used for the / URL, defaults to `:default-section` and `:default-item` values.
* `:middleware` - specify additional Ring-compatible middleware to use. By default, this is prepended to the default list (params, flash, session, resource). The behavior can be changed by specifying a keyword at the start of the list of additional middleware: `:append`, `:prepend`, `:replace`.
* `:password` - specify a password for the application reload URL flag, default `"secret"` - see also `:reload`.
* `:reload` - specify an `rc` key for the application reload URL flag, default `:reload` - see also `:password`.
* `:reload-application-on-every-request` - boolean, whether to reload controller, view and layout components on every request (intended for development of applications).
* `:selmer-tags` - if you are using the Selmer templating engine, you can specify a map that is passed to the Selmer parser to override what characters are used to identify tags, filters
* `:session-store` - specify storage used for Ring session storage. Legal values are `:memory` and `:cookie`. Default is whatever is Ring's default (which is memory storage as of this writing).
* `:suffix` - the file extension used for views and layouts. Default is `"html"`.

To create your own FW/1 application, use Leiningen to create a new fw1 project:

    lein new fw1 myapp

Edit the `main.clj` file to specify additional configuration, such as the template engine.
