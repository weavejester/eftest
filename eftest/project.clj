(defproject eftest "0.5.0"
  :description "A fast and pretty test runner"
  :url "https://github.com/weavejester/eftest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.reader "1.2.2"]
                 [progrock "0.1.2"]
                 [io.aviso/pretty "0.1.34"]
                 [mvxcvi/puget "1.0.2"]]
  :main eftest.main)
