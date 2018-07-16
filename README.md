# Workspaces

Current release:

Workspaces is a component development environment for ClojureScript, inspired by [devcards](https://github.com/bhauman/devcards).

## Play around

If you like to first get a feel about how workspaces function, you can try our live demo with some cards.

## Setup

First add the workspaces dependency on your project.

[![Clojars Project](https://clojars.org/nubank/workspaces/latest-version.svg)](https://clojars.org/nubank/workspaces)

This is recommended template for the HTML file for the workspaces:

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://fonts.googleapis.com/css?family=Open+Sans" rel="stylesheet">
  </head>
  <body>
    <div id="app"></div>
    <!-- you might need to change the js path depending on your configuration -->
    <script src="/js/workspaces/main.js" type="text/javascript"></script>
    <link rel="stylesheet" href="//cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/styles/github.min.css">
  </body>
</html>
```

Then create the entry point for the workspaces:

```clojure
(ns my-app.workspaces.main
  (:require [nubank.workspaces.core :as ws]
            ; require your cards namespaces here
            ))

(defonce init (ws/mount))
```

### With Shadow-CLJS

```clojure
;; shadow-cljs configuration
{:builds {:workspaces {:target           :browser
                       :output-dir       "resources/public/js/workspaces"
                       :asset-path       "/js/workspaces"
                       :devtools         {:before-load        nubank.workspaces.core/before-load
                                          :after-load         nubank.workspaces.core/after-load
                                          :http-root          "resources/public"
                                          :http-port          3689
                                          :http-resource-root "."}
                       :modules          {:main {:entries [my-app.workspaces.main]}}}}}
```

Now run with:

```
npx shadow-cljs watch workspaces
```

### With Figwheel

For some reason on my tests I couldn't make Figwheel `:on-jsload` hook to work with library
code, to go around this modify the main to look like this:

```clojure
(ns my-app.workspaces.main
  (:require [nubank.workspaces.core :as ws]
            ; require your cards namespaces here
            ))

(defn on-js-load [] (ws/after-load))

(defonce init (ws/mount))
```

So you can call the hook forom there, as:

```clojure
:cljsbuild {:builds
              [{:id           "dev"
                :source-paths ["src"]

                :figwheel     {:on-jsload "myapp.workspaces.main/on-js-reload"}

                :compiler     {:main                 myapp.workspaces.main
                               :asset-path           "js/workspaces/out"
                               :output-to            "resources/public/js/workspaces/main.js"
                               :output-dir           "resources/public/js/workspaces/out"
                               :source-map-timestamp true
                               :preloads             [devtools.preload]}}]}
```

Now run with:

```
lein figwheel
```

## Creating cards

To define cards you use the `ws/defcard` macro, here is an example to create a React card:

```clojure
(ns myapp.workspaces.cards
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.card-types.react :as ct.react]))

; simple function to create react elemnents
(defn element [name props & children]
  (apply js/React.createElement name (clj->js props) children))

(ws/defcard hello-card
  (ct.react/react-card
    (element "div" {} "Hello World")))
```

## Sharing workspaces

## Developing custom card types

## Differences from devcards

I have appreciate Bruces work from the first day in Devcards, I loved the idea of creating
the user interfaces in small blocks that can be re-accessed at any time. That said I wanted
to have more control over which cards/tests I wanna see in any given time on the screen,
I have this idea that I should be able to any number of cards from any number of different
namespaces, positioning then in the screen as fit to take max advantage of screen space.

Also I wanted the tool to have extensibility at its core, as your user interface grows
you start needing more and more specialized tools, and I wanted a system where the user
can provide those tools at card level.
