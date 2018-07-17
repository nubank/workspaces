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

## Card settings

### Card size

You can define settings for your card, like what initial size it should have, to do that
you can add maps to the card definition:

```clojure
(ns myapp.workspaces.configurated-cards
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]))

(ws/defcard sized-card
  {::wsm/card-width 5
   ::wsm/card-height 7}
  (ct.react/react-card
    (dom/div "Foo")))
```

The measuremnt is in grid tiles.

### Card content alignment

For the built-in cards you can also determine how the
element will be positioned in the card. So far we have been using the center card position
but depending on the kind of component you are trying that might not be the best option.

```clojure
(ws/defcard positioned-top
  {::wsm/card-width  5
   ::wsm/card-height 7
   ::wsm/align       {:flex 1}}
  (ct.react/react-card
    (dom/div "Foo on top")))
```

The card container is a flex element, so the previous example will put the card on
top and make it occupy the full width of the container.

The default `::wsm/align` is:

```clojure
{:display         "flex"
 :align-items     "center"
 :justify-content "center"}
```

### Container node props

Using the key `::wsm/node-props` you can set the style or other properties of the container node.

```clojure
(ws/defcard styles-card
  {::wsm/node-props {:style {:background "red" :color "white"}}}
  (ct.react/react-card
    (dom/div "I'm in red")))
```

### Setting templates

You will probably find some combinations of card settings you keep repeating, it's totally ok to
put those in variables and re-use. You can also send as many configuration maps as you
want, in fact the return of `(ct.react/react-card)` is also a map, they all just get
merged and stored as the card definition.

```clojure
(def purple-card {::wsm/node-props {:style {:background "#79649a"}}})
(def align-top {::wsm/align {:flex 1}})

(ws/defcard widget-card
  {::wsm/card-width 3 ::wsm/card-height 7}
  purple-card
  align-top
  (ct.react/react-card
    (dom/div "💜")))
```

## Using Workspaces

Now that we know how to define cards, it's time to learn how to work with then.

Imagine when you are about to start working on some components of your project, you can
start by looking at the index or searching using the spotlight feature (`alt+shift+a`).

By clicking on the card names you will add then to the current workspace (one will be
created if you don't have any open).

The idea here is that you add just the cards there are relevant to the work you need
to do, and create a workspace that can make the best use of your screen pixels.

And workspaces comes on tabs, enabling you to quickly switch between different workspace
settings.

The following topics will describe what you can do to help you manage your workspaces. 

### Creating workspaces

You can create new workspaces by clicking at the `+` tab on the interface. The workspaces
are created and stored in your browser local storage. You can rename the workspace
by clicking on its tab while it's active.

### Responsive grid

Your cards are placed in a responsive grid, this means that the number of columns you
have available will vary according to your page width size. In the right below the
workspace tabs you can see how many columns you have available right now (eg: `c8` means 8 columns).

Each responsive breakpoint will have stored separated, so you can arange a workspace
to fit that available width. The sizes and positions will be recorded separated by each
column numbers (they vary from 2 to 20).

Each column size has 120~140px, varies depending on page width.

### Workspace actions

When you have an open workspace, there is a toolbar with some action buttons, here is
a description of what each does:

* `Copy layout`: actually a select here, use this to copy the layout from a different responsive breakpoint
* `Refresh cards`: triggers a refresh on every card on this active workspace
* `Duplicate`: creates a copy of current workspace
* `Unify layouts`: makes every breakpoint have the same layout as the current active one
* `Export`: Export current workspace layouts to data (logged into browser console)
* `Delete`: Delete current workspace

### Sharing workspaces

A lot of times your workspaces will be disposable, just pull a few components, work
and throw away. But other times you like to create more durable ones, like a kitchen
sink of all your components buildings blocks, or maybe a setup that works nice for a
specific task. You a lot of effort to make it look good on many different responsive
breakpoints. So would be a pain if every user of the system had to redo the task to
organize those types of workspaces.

To solve that, you can use the `Export` button on the workspace toolbar. It outputs
the workspace layout as a transit data on the console. You can copy that, and use
to store that workspace setup on the code, making it available to any other person
using this workspace setup.

```clojure
(ws/defworkspace ui-block
  "[\"^ \",\"c10\",[[\"^ \",\"i\",\"~$fulcro.incubator.workspaces.ui.reakit-ws/reakit-base\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"minH\",2]],\"c8\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c16\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c14\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c2\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c12\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c4\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c18\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c20\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]],\"c6\",[[\"^ \",\"i\",\"^0\",\"w\",2,\"h\",4,\"x\",0,\"y\",0,\"^1\",2]]]"))
```

When you open a shared workspace, you can't change it, it's static, but you can duplicate
it and change the copy as you please.

## Keyboard shortcuts

Here is a list of available shortcuts, all of then use `alt+shift` followed by a key:

* `alt+shift+a`: Add card to current workspace (open spotlight for card picking)
* `alt+shift+i`: Toggle index view
* `alt+shift+h`: Toggle card headers
* `alt+shift+n`: Create new local workspace
* `alt+shift+w`: Close current workspace

## Developing custom card types [TODO]
