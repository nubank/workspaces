(defproject nubank/workspaces "1.0.7"
  :description "Work environments for development of web apps."
  :url "https://github.com/nubank/workspaces"
  :license {:name "Apache License 2.0"
            :url  "https://github.com/nubank/workspaces/blob/master/LICENSE"}
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
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
