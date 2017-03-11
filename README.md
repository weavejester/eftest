# Eftest

Eftest is a fast and pretty Clojure test runner.

## Installation

To install, add the following to your project `:dependencies`:

    [eftest "0.1.4"]

## Screenshots

When all the tests pass, it looks like this:

![Passing example](doc/passing-example.png)

When a test fails, it looks like:

![Failing example](doc/failing-example.png)

And when a test throws an exception, it looks like:

![Erroring example](doc/erroring-example.png)

## Usage

Eftest has two main functions: `find-tests` and `run-tests`.

The `find-tests` function searches a source, which can be a namespace,
directory path, symbol, var, or a collection of any of the previous.
It returns a collection of test vars found in the source.

The `run-tests` function accepts a collection of test vars and runs
them, delivering a report on the tests as it goes.

Typically these two functions are used together:

```clojure
user=> (require '[eftest.runner :refer [find-tests run-tests]])
nil
user=> (run-tests (find-tests "test"))
...
```

The above example will run all tests found in the "test" directory.

By default Eftest runs tests in parallel, which can cause issues with
tests that expect to be single-threaded. To disable this, set the
`:multithread?` option to `false`:

```clojure
user=> (run-tests (find-tests "test") {:multithread? false})
```

Alternatively, you can add the `:eftest/synchronized` key as metadata
to any tests you want to force to be executed in serial:

```clojure
(deftest ^:eftest/synchronized a-test
  (is (= 1 1)))
```

You can also change the reporting function used. For example, if you
want a colorized reporter but without the progress bar:

```clojure
user=> (run-tests (find-tests "test") {:report eftest.report.pretty/report})
```

Or maybe you want the old Clojure test reporter:

```clojure
user=> (run-tests (find-tests "test") {:report clojure.test/report})
```

## License

Copyright © 2017 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
