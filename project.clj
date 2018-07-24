(defproject nubank/workspaces "1.0.0-preview5-SNAPSHOT"
  :description "Work environments for development of web apps."
  :url "https://github.com/nubank/workspaces"
  :license {:name "Apache License 2.0"
            :url  "https://github.com/nubank/workspaces/blob/master/LICENSE"}
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]
                           :aliases [:dev]}
  :jar-exclusions [#"^workspaces/.*" #"^nubank/workspaces/workspaces/.*" #"\.DS_Store" #"^examples/"]
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:source-paths ["src" "workspaces"]}})
