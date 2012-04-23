FW/1 in Clojure
===============

This is a port from CFML to Clojure of Framework One (FW/1).

TL;DR - Want to try it out on Heroku? http://corfield.org/entry/fw-1-user-manager-example-on-heroku

FW/1 in Clojure is based on Ring and Enlive. FW/1 is a lightweight, convention-based MVC framework.
Controller functions and views are automatically selected based on standard URL patterns - with site sections and items within each section.
Layouts are applied, if provided, in a cascade from item to section to site.

The basic URL pattern is: <tt>/section/item/arg1/value1/arg2/value2?arg3=value3</tt>

The arg / value pairs from the URL are assembled into a map called the request context (and referred to as **rc** in the documentation).

The standard file structure for a FW/1 application is:

* **controllers/** - contains a <tt>.clj</tt> file for each _section_ that needs business logic.
* **layouts/** - contains per-_item_, per-_section_ and per-site layouts as needed.
* **views/** - contains a folder for each _section_, containing an HTML view for each _item_.

The easiest way to get started with FW/1 is to use the
[fw1-template](https://github.com/seancorfield/fw1-template) template
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

If **controllers.section/item-view(rc nodes)** exists, FW/1 will call that as an Enlive transform on the view template. A view function should return the **nodes**, updated as necessary.

Then FW/1 looks for a cascade of layouts:

* **layouts/section/item.html**, replacing an HTML element with the id **"body"** with the view, and then applying **controllers.section/item-layout(rc nodes)** as a transform, if it exists.
* **layouts/section.html**, replacing an HTML element with the id **"body"** with the view so far, and then applying **controllers.section/layout(rc nodes)** as a transform, if it exists.
* **layouts/default.html**, replacing an HTML element with the id **"body"** with the view so far, and then applying the function provided as the **:layout** element of the configuration as a transform, if it was specified.

Each of the layout functions should return the **nodes**, updated as necessary.

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

You can start the server on port 8080 with:

<pre>lein run -m usermanager.main</pre>

You can specify a different port like this:

<pre>PORT=8111 lein run -m usermanager.main</pre>

In fw1-test.core, the call to (fw1/start) can be passed a map of configuration parameters:

* **:after** - a function (taking / returning **rc**) which should be called after invoking any controller
* **:application-key** - the namespace prefix for the application, default none.
* **:before** - a function (taking / returning **rc**) which should be called before invoking any controller
* **:default-section** - the _section_ used if none is present in the URL, default **"main"**.
* **:default-item** - the _item_ used if none is present in the URL, default **"default"**.
* **:error** - the action - _"section.item"_ - to execute if an exception is thrown from the initial request, defaults to **:default-section** value and **"error"** _[untested]_.
* **:home** - the _"section.item"_ pair used for the / URL, defaults to **:default-section** and **:default-item** values.
* **:layout** - specify a transform function for the site-wide layout, if needed (default none).
* **:password** - specify a password for the application reload URL flag, default **"secret"** - see also **:reload**.
* **:reload** - specify an **rc** key for the application reload URL flag, default **:reload** - see also **:password**.
* **:reload-application-on-every-request** - boolean, whether to reload controller, view and layout components on every request (intended for development of applications).

To create your own FW/1 application, use Leiningen to create a new project, edit **project.clj** to add a dependency on **[framework-one "0.1.0"]** (or later, check on Clojars!) and then you'll need a "main" driver file that looks something like this (modulo the namespace):
<pre>
(ns main
  (:require [framework.one :as fw1])
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.reload]))

(defn -main[]
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))] 
    (run-jetty
      (fw1/start) ;; configuration can go here: (fw1/start :password "abracadabra" :reload "magic")
      {:port port})))</pre>
At a minimum you'll want **views/main/default.html** containing your default application's page (HTML).
