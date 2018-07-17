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

You can use this to mount any React component, for a [re-frame](https://github.com/Day8/re-frame/) for example, you can
use `(reagent/as-element [re-frame-root])` as the content.

### Stateful React cards

Usually libraries like Fulcro or Re-frame will manage the state and trigger render in
the proper times, but if you wanna do something with raw React, you can provide an
atom to be the app state, and the card will watch that atom and triggers a root render
everytime it changes.

```clojure
(ws/defcard counter-example-card
  (let [counter (atom 0)]
    (ct.react/react-card
      counter
      (element "div" {}
        (str "Count: " @counter)
        (element "button" {:onClick #(swap! counter inc)} "+")))))
```

### Fulcro cards

Workspaces is built with [Fulcro](http://fulcro.fulcrologic.com/) and has some extra support for it. Using the `fulcro-card`
you can easely mount a Fulcro component with the entire app, here is an example:

```clojure
(ns myapp.workspaces.fulcro-demo-cards
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [nubank.workspaces.core :as ws]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [fulcro.client.mutations :as fm]))

(fp/defsc FulcroDemo
  [this {:keys [counter]}]
  {:initial-state (fn [_] {:counter 0})
   :ident         (fn [] [::id "singleton"])
   :query         [:counter]}
  (dom/div
    (str "Fulcro counter demo [" counter "]")
    (dom/button {:onClick #(fm/set-value! this :counter (inc counter))} "+")))

(ws/defcard fulcro-demo-card
  (ct.fulcro/fulcro-card
    {::f.portal/root FulcroDemo}))
```

By default the Fulcro card will wrap your component will a thin root, by having always
having components with idents you can leverage generic mutations, this is recommended
over making a special Root. But if you want to send your own root, you can set the
`::f.porta/wrap-root? false`. Here are more options available:

* `::f.portal/wrap-root?` (default: `true`) Wraps component into a light root
* `::f.portal/app` (default: `{}`) This is the app configuration, same options you could send to `fulcro/new-fulcro-client`
* `::f.portal/initial-state` (default `{}`) Accepts a value or a function. A value will
be used to call the initial state function of your root. If you provide a function, the
value returned by it will be the initial state.

### Test cards

Workspaces has default integration with `cljs.test`, but you have to start the tests
using `ws/deftest` instead of `cljs.test/deftest`. The `ws/deftest` will also emit a
`cljs.test/deftest` call, so you can use the same for running on CI. Example test card:

```clojure
(ws/deftest sample-test
  (is (= 1 1)))
```

### Namespace test cards

When you create test cards using `ws/deftest`, a card will be automatically created to
run on the test on that namespace, just click on the test namespace name on the index
to load the card.

### Card anathomy

## Using Workspaces [TODO]

## Sharing workspaces [TODO]

## Keyboard shortcuts

Here is a list of available shortcuts, all of then use `alt+shift` followed by a key:

* `alt+shift+a`: Add card to current workspace (open spotlight for card picking)
* `alt+shift+i`: Toggle index view
* `alt+shift+h`: Toggle card headers
* `alt+shift+n`: Create new local workspace
* `alt+shift+w`: Close current workspace

## Styling [TODO]

## Developing custom card types [TODO]
