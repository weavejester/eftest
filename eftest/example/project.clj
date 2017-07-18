(def eftest-version "0.3.1")

(defproject example "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[eftest ~eftest-version]
                 [org.clojure/clojure "1.8.0"]]
  :plugins [[lein-eftest ~eftest-version]]
  :profiles {:junit {:eftest {:report eftest.report.junit/report}}})
