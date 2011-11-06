FW/1 in Clojure
===============

This is a port from CFML to Clojure of Framework One (FW/1).

FW/1 in Clojure is based on Ring and Enlive. FW/1 is a lightweight, convention-based MVC framework. Controller functions and views are automatically selected based on standard URL patterns - with site sections and items within each section. Layouts are applied, if provided, in a cascade from item to section to site.

The basic URL pattern is: <tt>/section/item/arg1/value1/arg2/value2?arg3=value3</tt>

The arg / value pairs from the URL are assembled into a map called the request context (and referred to as *rc* in the documentation).

The standard file structure for a FW/1 application is:
* *controllers/* - contains a <tt>.clj</tt> file for each _section_ that needs business logic.
* *layouts/* - contains per-_item_, per-_section_ and per-site layouts as needed.
* *views/* - contains a folder for each _section_, containing an HTML view for each _item_.

Controllers can have _before(rc)_ and _after(rc)_ functions that apply to all requests in a _section_.

A URL of <tt>/section/item</tt> will cause FW/1 to call:
* *controllers.section/before(rc)*, if defined.
* *controllers.section/item(rc)*, if defined.
* *controllers.section/after(rc)*, if defined.

Then FW/1 will look for an HTML view template:
* *views/section/item.html*

If *controllers.section/item-view(rc nodes)* exists, FW/1 will call that as an Enlive transform on the view template.

Then FW/1 looks for a cascade of layouts:
* *layouts/section/item.html*, replacing an HTML element with the id *"body"* with the view, and then applying *controllers.section/item-layout(rc nodes)* as a transform, if it exists.
* *layouts/section.html*, replacing an HTML element with the id *"body"* with the view so far, and then applying *controllers.section/layout(rc nodes)* as a transform, if it exists.
* *layouts/default.html*, replacing an HTML element with the id *"body"* with the view so far, and then applying the function provided as the *:layout* element of the configuration as a transform, if it was specified.

You can start the server on port 8080 with:

<pre>lein run -m fw1-test.core</pre>

You can specify a different port like this:

<pre>PORT=8111 lein run -m fw1-test.core</pre>

In fw1-test.core, the call to (fw1/start) can be passed a map of configuration parameters:
* *:default-section* - the _section_ used if none is present in the URL, default *"main"*.
* *:default-item* - the _item_ used if none is present in the URL, default *"default"*.
* *:reload-application-on-every-request* - boolean, whether to reload controller, view and layout components on every request (intended for development of applications).
* *:error* - the action - *"section.item"* - to execute if an exception is thrown from the initial request _[not yet implemented]_.