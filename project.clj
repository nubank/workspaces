(defproject nubank/workspaces "1.2.0-BETA"
  :description "Workspaces is a component development environment for ClojureScript."
  :url "https://github.com/nubank/workspaces"
  :license {:name "Apache License 2.0"
            :url  "https://github.com/nubank/workspaces/blob/master/LICENSE"}
  :dependencies [[cljsjs/highlight "11.7.0-0"]
                 [cljsjs/react-grid-layout "0.17.1-0"]
                 [com.fulcrologic/fulcro "3.7.3" :exclusions [org.clojure/clojurescript]]
                 [com.fulcrologic/fulcro-garden-css "3.0.9"]
                 [fulcrologic/fulcro "2.8.13" :exclusions [org.clojure/clojurescript]]
                 [fulcrologic/fulcro-incubator "0.0.39" :exclusions [org.clojure/clojurescript org.clojure/google-closure-library org.clojure/google-closure-library-third-party com.google.javascript/closure-compiler-externs com.google.javascript/closure-compiler-unshaded]]
                 [fulcrologic/fulcro-inspect "2.2.5"]
                 [com.wsscode/fuzzy "1.0.0"]
                 [org.clojure/core.async "1.6.681"]]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :jar-exclusions [#"^workspaces/.*" #"^nubank/workspaces/workspaces/.*" #"\.DS_Store" #"^examples/"]
  :deploy-repositories [["releases" :clojars]]
  :aliases {"pre-release"  [["vcs" "assert-committed"]
                            ["change" "version" "leiningen.release/bump-version" "release"]
                            ["vcs" "commit"]
                            ["vcs" "tag" "v"]]

            "post-release" [["change" "version" "leiningen.release/bump-version"]
                            ["vcs" "commit"]
                            ["vcs" "push"]]}
  :profiles {:dev {:source-paths ["src" "workspaces"]}})
