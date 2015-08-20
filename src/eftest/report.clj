(ns eftest.report)

(def ^:dynamic *context*
  "Used by eftest.runner/run-tests to hold a mutable atom that persists for the
  duration of the test run. This atom can be used by reporters to hold
  additional statistics and information during the tests."
  nil)
