FW/1 in Clojure
===============

This is just a first cut of a port from CFML to Clojure of Framework One (FW/1).

Basic routes with defaults /section/item (to /main/default) will invoke item(params) in controllers.section namespace.

Views have to work a bit differently (from the CFML version) so there are views/section/item.html files and you define controllers.section/item-view(nodes params) to provide the transforms on the HTML (based on Enlive library).

You can start the server on port 8080 with:

lein run -m fw1-test.core

You can specify a different port like this:

PORT=8111 lein run -m fw1-test.core
