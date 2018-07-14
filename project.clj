(defproject nubank/workspaces "0.1.0-SNAPSHOT"
  :description "Work environments for development of web apps."
  :url "https://github.com/nubank/workspaces"
  :license {:name "Apache License 2.0"
            :url  "https://github.com/nubank/workspaces/blob/master/LICENSE"}
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]
                           :aliases [:dev]}
  :profiles {:dev {:source-paths ["src" "workspaces"]}})
