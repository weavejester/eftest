(defproject com.exoscale/eftest "1.0.0-alpha1-SNAPSHOT"
  :description "A fast and pretty test runner"
  :url "https://github.com/exoscale/eftest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.namespace "1.3.0"]
                 [progrock "0.1.2"]
                 [io.aviso/pretty "1.3"]
                 [mvxcvi/puget "1.3.2"]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy" "clojars"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
