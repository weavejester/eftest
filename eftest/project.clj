(defproject eftest "0.5.8"
  :description "A fast and pretty test runner"
  :url "https://github.com/weavejester/eftest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [progrock "0.1.2"]
                 [io.aviso/pretty "0.1.34"]
                 [mvxcvi/puget "1.1.1"]]
  :plugins [[lein-eftest "0.5.8"]]
  :aliases {"test-all" ["with-profile" "default:+1.8:+1.9:+1.10" "eftest"]}
  :profiles
  {:1.8  {:dependencies [[org.clojure/clojure "1.8.0"]]}
   :1.9  {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}})
