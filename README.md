FW/1 in Clojure
===============

This is just a first cut of a port from CFML to Clojure of Framework One (FW/1).

Basic routes with defaults /section/item (to /main/default) will invoke item(params) in controllers.section namespace.
Views have to work a bit differently (from the CFML version) so there are views/section/item.html files and you define
a controllers.section/item-view(params) to provide the transforms on the HTML (incomplete).

Run lein deps to pull down everything you need. Then evaluate this form to start the server:

(run-jetty (wrap-reload (var app) '(fw1-clj.core)) {:port 8111})