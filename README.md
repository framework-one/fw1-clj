FW/1 in Clojure
===============

This is a port from CFML to Clojure of Framework One (FW/1).

TL;DR - Want to try it out on Heroku? http://corfield.org/entry/fw-1-user-manager-example-on-heroku

FW/1 in Clojure is based on Ring and either
[Enlive](https://github.com/cgrand/enlive) or
[Selmer](https://github.com/yogthos/Selmer).
FW/1 is a lightweight, convention-based MVC framework.
Controller functions and views are automatically selected based on standard URL patterns - with site sections and items within each section.
Layouts are applied, if provided, in a cascade from item to section to site.

The basic URL pattern is: <tt>/section/item/arg1/value1/arg2/value2?arg3=value3</tt>

The arg / value pairs from the URL are assembled into a map called the request context (and referred to as **rc** in the documentation).

The standard file structure for a FW/1 application is:

* **controllers/** - contains a <tt>.clj</tt> file for each _section_ that needs business logic.
* **layouts/** - contains per-_item_, per-_section_ and per-site layouts as needed.
* **views/** - contains a folder for each _section_, containing an HTML view for each _item_.

The easiest way to get started with FW/1 is to use the
[fw1-template](https://github.com/framework-one/fw1-template) template
(plugin) for Leiningen. The template can create a basic FW/1 skeleton
project for you that "just works" and provides the directory structure
and some basic files for you to get started with. 

Controllers can have _before(rc)_ and _after(rc)_ handler functions that apply to all requests in a _section_.

A URL of <tt>/section/item</tt> will cause FW/1 to call:

* **controllers.section/before(rc)**, if defined.
* **controllers.section/item(rc)**, if defined.
* **controllers.section/after(rc)**, if defined.

A handler function should return the **rc**, updated as necessary.

Then FW/1 will look for an HTML view template:

* **views/section/item.html**

The suffix can be controlled by the **:suffix** configuration option. In the user manager example, you'll see an Enlive version using the default **"html"** configuration and a Selmer version using **:template :selmer** and **:suffix "tpl"**.

When using Enlive, if **controllers.section/item-view(rc nodes)** exists, FW/1 will call that as an Enlive transform on the view template. A view function should return the **nodes**, updated as necessary.

Then FW/1 looks for a cascade of layouts (again, the suffix configurable):

* **layouts/section/item.html**,
 * For Enlive, replacing an HTML element with the id **"body"** with the view, and then applying **controllers.section/item-layout(rc nodes)** as a transform, if it exists.
 * For Selmer, replacing **{{body}}** with the view (and not calling any transforms).
* **layouts/section.html**,
 * For Enlive, replacing an HTML element with the id **"body"** with the view so far, and then applying **controllers.section/layout(rc nodes)** as a transform, if it exists.
 * For Selmer, replacing **{{body}}** with the view so far.
* **layouts/default.html**,
 * For Enlive, replacing an HTML element with the id **"body"** with the view so far, and then applying the function provided as the **:layout** element of the configuration as a transform, if it was specified.
 * For Selmer, replacing **{{body}}** with the view so far. The **:layout** configuration is ignored.

For Enlive, each of the layout functions should return the **nodes**, updated as necessary.

Any controller function also has access to the the FW/1 API:

* **cookie(rc name)** - returns the value of **name** from the cookie scope
* **cookie(rc name value)** - sets **name** to **value** in the cookie scope, and returns the updated **rc**
* **flash(rc name)** - returns the value of **name** from the flash scope
* **flash(rc name value)** - sets **name** to **value** in the flash scope, and returns the updated **rc**
* **redirect(rc url)** - returns **rc** containing information to indicate a redirect to the specified **url**.
* **reload?(rc)** - returns **true** if the current request includes URL parameters to force an application reload.
* **session(rc name)** - returns the value of **name** from the session scope
* **session(rc name value)** - sets **name** to **value** in the session scope, and returns the updated **rc**
* **to-long(val)** - converts **val** to a long, returning zero if it cannot be converted (values in **rc** come in as strings so this is useful when you need a number instead and zero can be a sentinel for "no value").

The following transforms from Enlive are exposed as aliases via the FW/1 API:

* **append, at, clone-for, content, do->, html-content, prepend, remove-class, set-attr, substitute**

In addition FW/1 adds:

* **append-attr(attr v)** - appends **v** to the value of the specified attribute **attr** (useful to append data to **href** links).
* **prepend-attr(attr v)** - prepends **v** to the value of the specified attribute **attr** (useful to prepend protocol / domain to **href** links).

The following symbols from Selmer are exposed as aliases via the FW/1 API:

* **add-tag!**, **add-filter!**

By default, FW/1 adds **empty?** as a filter with the same name so the following is possible out of the box:
<pre>
{% if some-var|empty? %}There are none!{% endif %}
</pre>

You can start the server on port 8080 with:

<pre>lein run -m usermanager.main</pre>

You can specify a different port like this:

<pre>PORT=8111 lein run -m usermanager.main</pre>

In your main namespace, the call to **(fw1/start)** can be passed a map of configuration parameters:

* **:after** - a function (taking / returning **rc**) which will be called after invoking any controller
* **:application-key** - the namespace prefix for the application, default none.
* **:before** - a function (taking / returning **rc**) which will be called before invoking any controller
* **:default-section** - the _section_ used if none is present in the URL, default **"main"**.
* **:default-item** - the _item_ used if none is present in the URL, default **"default"**.
* **:error** - the action - _"section.item"_ - to execute if an exception is thrown from the initial request, defaults to **:default-section** value and **"error"** _[untested]_.
* **:home** - the _"section.item"_ pair used for the / URL, defaults to **:default-section** and **:default-item** values.
* **:layout** - specify a transform function for the site-wide layout, if needed (default none). Enlive only. Selmer ignores this.
* **:middleware** - specify additional Ring-compatible middleware to use. By default, this is prepended to the default list (params, flash, session, resource). The behavior can be changed by specifying a keyword at the start of the list of additional middleware: **:append**, **:prepend**, **:replace**.
* **:password** - specify a password for the application reload URL flag, default **"secret"** - see also **:reload**.
* **:reload** - specify an **rc** key for the application reload URL flag, default **:reload** - see also **:password**.
* **:reload-application-on-every-request** - boolean, whether to reload controller, view and layout components on every request (intended for development of applications).
* **:selmer-tags*** - if you are using the Selmer templating engine, you can specify a map that is passed to the Selmer parser to override what characters are used to identify tags, filters
* **:suffix** - the file extension used for views and layouts. Default is **"html"**.
* **:template** - the templating engine used. Legal values are **:enlive** and **:selmer**. Default is **:enlive**.

To create your own FW/1 application, use Leiningen to create a new fw1 project:
<pre>
lein new fw1 myapp
</pre>
Edit the **main.clj** file to specify additional configuration, such as the template engine.
