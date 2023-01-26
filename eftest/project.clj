(defproject eftest "0.6.0"
  :description "A fast and pretty test runner"
  :url "https://github.com/weavejester/eftest"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.namespace "1.3.0"]
                 [progrock "0.1.2"]
                 [io.aviso/pretty "1.3"]
                 [mvxcvi/puget "1.3.4"]
                 [juji/editscript "0.6.2"]]
  :plugins [[lein-eftest "0.6.0"]]
  :aliases {"test-all"
            ["with-profile" "default:+1.8:+1.9:+1.10:+1.11" "eftest"]}
  :profiles
  {:1.8  {:dependencies [[org.clojure/clojure "1.8.0"]]}
   :1.9  {:dependencies [[org.clojure/clojure "1.9.0"]]}
   :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}
   :1.11 {:dependencies [[org.clojure/clojure "1.11.1"]]}})
