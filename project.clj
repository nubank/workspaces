(defproject nubank/workspaces "1.0.16"
  :description "Work environments for development of web apps."
  :url "https://github.com/nubank/workspaces"
  :license {:name "Apache License 2.0"
            :url  "https://github.com/nubank/workspaces/blob/master/LICENSE"}
  :dependencies [[cljsjs/highlight "9.12.0-2"]
                 [cljsjs/react-grid-layout "0.16.6-0"]
                 [com.fulcrologic/fulcro "3.0.5"]
                 [com.fulcrologic/fulcro-garden-css "3.0.6"]
                 [fulcrologic/fulcro "2.6.16"]
                 [fulcrologic/fulcro-incubator "0.0.19"]
                 [fulcrologic/fulcro-inspect "2.2.4"]
                 [com.wsscode/fuzzy "1.0.0"]
                 [org.clojure/core.async "0.4.474"]]
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
