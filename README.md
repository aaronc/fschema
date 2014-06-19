# fschema

Elegant functional data validation and mutation for Clojure and
Clojurescript.

fschema has the following design goals:

- The API should be so simple and intuitive that you mostly just
  remember it.
- Validators and mutators should be simple functions. It should be
  possible to create more complex validators and mutators through
  function composition.
- Error messages should be detailed and easy to transform into
  *localized* error messages.
- Error message should include information on property paths.
- It should be possible to validate individual properties within a schema.

An example:

```clojure
(ns example
    (:require [fschema.core :refer [schema-fn each]])
    (:require [fschema.constraints :as c])

(def my-schema
    (schema-fn
        {:a [c/not-nil (c/>= 0)]
         :b [c/string?]
         :c [c/not-nil c/keyword?]
         :d [c/vector? (each c/integer?)]}))

(my-schema {:a -1})
;; [{:path [:a], :value -1, :error-id :fschema.constraints/>=, :params [0]}
;;  {:path [:c], :value nil, :error-id :fschema.constraints/not-nil}]
    
(my-schema {:a 5 :b 6 :c :xyz :d [1 "abc" 3]})
;; [{:path [:b], :value 6, :error-id :fschema.constraints/string?}
;   {:path [:d 1], :value "abc", :error-id :fschema.constraints/integer?}]

(my-schema {:a 5 :b "6" :c :xyz :d [1 2 3]})
;; {:a 5 :b "6" :c :xyz :d [1 2 3]}
;; Validators return the input data upon successful validation
```

## Installation

Add the following dependency to your `project.clj`:

```clojure
[fschema "0.2.0"]
```

## Basics

All validators and mutators are composed of functions taking a signal
argument. A validator is a function that always returns the value it
was passed or an `error` value. A mutator may return the value it was
passed, a "mutated" value, or an `error` value. `error`
values control the flow of execution in fschema.

### Errors

An `error` value is created using the `error` function. A new `error`
value may be created by passing a map describing the `error` to the
`error` function. The result will be a vector containing this `error`
marked with `{:error true}` in its metadata map.

```clojure
user> (error {:error-id ::my-error})
[{:error-id :user/my-error}]

user> (meta *1)
{:error true}
```

Errors can also be combined using the `error` function.

```clojure
user> (error {:error-id ::my-error} {:error-id ::my-error2})
[{:error-id :user/my-error} {:error-id :user/my-error2}]

user> (error *1 {:error-id ::my-error3})
[{:error-id :user/my-error} {:error-id :user/my-error2} {:error-id :user/my-error3}]
```

To check for `error` values, the `error?` function is used. If it is
passed an `error` value it will return that value. If it is passed a
non-error value, it will return `nil`.

```clojure
user> (error? (error {:error-id ::my-error}))
[{:error-id :user/my-error}]

user> (error? {:error-id ::my-error})
nil
```

This gives us a simple way to collect errors that may occur at
different places within execution (for instance on different branches
of the same map) and to easily differentiate them from other valid
values (`error` values are marked using Clojure metadata facilities).

### Constraints

Constraints are the simplest validators. Constraints can be
created using the `constraint` function or the `defconstraint` macro
(see [Creating Constraints](#creating-constraints)).
fschema includes a number of [built-in constraints](#built-in-constraints).

Constraints return error maps that include the `:error-id` and
failing `:value` as well as possibly `:params` specific to this
constraint instance and a default error `:message`.
This enables error maps to be used to generate specific localizable error
messages. fschema's builtin constraints intentionally mirror Clojure's
basic functions so they are easy to remember. Because of this the
`fschema.constraints` namespace must always be `require`'d using an
`:as` alias.

```clojure
user> (require '[fschema.constraints :as c])
nil

user> (c/string? 5)
[{:value 5, :error-id :fschema.constraints/string?}]

user> (c/string? "abc")
"abc" ;; Constraints always return the value they were passed upon success

user> ((c/> 5) 3)
[{:value 3, :error-id :fschema.constraints/>, :params [5]}]
```

*By default, all constraints include the *`not-nil`* constraint. To
 allow for *`nil`* values to pass through silently (without an *`error`*)
 the *[`optional`](#optional)* function can be used.*

## Composing validators and mutators

To make things more interesting we must can validators and
mutators using the `schema-fn` and `each`functions.

### schema-fn

`schema-fn` is the main function used for composing validators and
mutators. It creates "schema functions", thus the name `schema-fn`.

#### Function Chaining with Error Checking

If `schema-fn` is passed multiple functions or a vector of functions it will
chain these functions together and create a composite function which
threads the value passed to it through each function. In some ways it is
similar to Clojure's `->` macro. `schema-fn`'s special feature is that
it will stop threading if any function in the chain return
an `error` value and it will instead return that error.

Here is an example of creating a composite validator using constraints
and `schema-fn`:

```clojure
user> (def v1 (schema-fn c/not-nil c/string?)
#'user/v1

user> (v1 "5")
"5" ;; successful validation returns the same value

user> (v1 5)
[{:value 5, :error-id :fschema.constraints/string?}]

user> (v1 nil)
[{:value nil, :error-id :fschema.constraints/not-nil}]
```

Mutators can also be composed using `schema-fn`:

```clojure
user> (def f1 (schema-fn inc str))
#'user/f1

user> (f1 5)
"6"

;; This is similar to:
user> (-> 5 inc str)
"6"
```

A vector of functions can also be used to get the same result:


```clojure
user> (def f2 (schema-fn [inc str]))
#'user/f2

user> (f2 5)
"6"
```

#### Validating maps

`schema-fn` can also be used to create map validators and mutators. If a map is
passed as an argument to `schema-fn`, `schema-fn` is applied to each
value in that map and the resulting functions at each key are applied
to each value with the corresponding keys in an input map. This gives
us a simple model to compose map validators and nested map validators.

`(schema-fn {:a [c/not-nil c/integer?])` is equivalent to
`(schema-fn {:a (schema-fn c/not-nil c/integer?)})`. Both
functions will require the input map to contain an integer value for
the key `:a`.

One nice feature about map validators is that they will return the
path of the failing property in error messages (even through multiple
levels of nesting).

```clojure
user> ((schema-fn {:a c/not-nil}) {})
[{:path [:a], :value nil, :error-id :fschema.constraints/not-nil}]

user> ((schema-fn {:a c/not-nil}) {:a 1})
{:a 1}

;; Nested maps
user> ((schema-fn {:a [c/not-nil {:b c/not-nil}]}) {:a {}})
[{:path [:a :b], :value nil, :error-id :fschema.constraints/not-nil}]

user> ((schema-fn {:a [c/not-nil {:b c/not-nil}]}) {:a {:b 5}})
{:a {:b 5}}
```

### each

The `each` function is used to compose a `schema-fn` that will be executed
upon each member of a sequence and return a sequence of the same type.

```clojure
user> ((each c/not-nil c/integer?) [1 2.0 nil])
[{:path [1], :value 2.0, :error-id :fschema.constraints/integer?}
 {:path [2], :value nil, :error-id :fschema.constraints/not-nil}]
;; each functions return the index in the sequence as part of the path

user> ((each c/not-nil c/integer?) [1 2 3])
[1 2 3]
```

### where

The `where` function is used to conditionally apply a chain of functions. Its
first parameter is the test function that will be used to test if
the remaining arguments should be applied (as a `schema-fn` chain). If
the test-fn returns a `nil`, `false`, or `error` value, the
function(s) won't be applied - test-fn may therefore be either a
regular function or a validator.

```
user> ((where number? (c/> 0)) -1)
[{:value -1, :error-id :fschema.constraints/>, :params [0]}]

user> ((where number? (c/> 0)) "abc")
"abc"
```

### optional

`(optional f)` is short-hand for `(where? #(not (nil? %)) f)`. It is
used to allow `nil` values to pass through silently without an `error`.

```clojure
user> ((schema-fn {:a (optional c/integer?)}) {})
{} ;; The fact that :a is a missing key is not flagged as an error
```


## Creating Constraints

### constraint

TODO

### defconstraint

TODO

## Built-in Constraints

### not-nil

The `not-nil` constraint is to be used whenever it is necessary to
ensure that a value is not nil. *All other constraints will return
*`nil`* when passed a *`nil`* value.*

### string?, number?, integer?, map?, vector?, seq?, keyword?, symbol?, set?, coll?, list?, instance?, boolean?
    
These constraints are available to validate the type of arguments.

*Note:  these functions intentionally mirror Clojure's type checking
functions.  The *`fschema.constraints`* namespace must never be loaded
with *`:use`* (which is bad practice anyway).*

```clojure
user> (require '[fschema.constraints :as c])
nil

user> (c/string? 7)
[{:value 7, :error-id :fschema.constraints/string?}]

user> (c/map? {:a 1})
{:a 1}

user> ((c/instance? String) [\a])
[{:value [\a], :error-id :fschema.constraints/instance?, :params [java.lang.String]}]
```

### re-matches
The `re-matches` constraint factory function can be used to match
strings against regular expressions. (*Note: the *`string?`* constraint is
invoked implicity when `re-matches` is used.*)

*Note: this function intentionally mirrors Clojure's *`re-matches`*
function.  The *`fschema.constraints`* namespace must never be loaded
with *`:use`* (which is bad practice anyway).*

```clojure
user> (require '[fschema.constraints :as c])
nil

user> ((c/re-matches #"a.\*z") "abx")
[{:value "abx", :error-id :re-matches, :params [#"a.\*z"]}]

user> ((c/re-matches #"a.\*z") 4)
[{:value 4, :error-id :fschema.constraints/string?}]

user> ((c/re-matches #"a.\*z") "abcz")
"abcz"
```

### <, >, <=, =>, not=, =
The `<`, `>`, `<=`, `>=`, `not=` and `=` constraint constructors are
available to validate the range of numeric values. (*Note: the*
`number?` *constraint is invoked implicitly when any of the numeric
range functions are used.*)

*Note:  these functions intentionally mirror Clojure's comparison
operators.  The *`fschema.constraints`* namespace must never be loaded
with *`:use`* (which is bad practice anyway).*

```clojure
user> (require '[fschema.constraints :as c])
nil

user> ((c/> 3) 2)
[{:value 2, :error-id :fschema.constraints/>, :params [3]}]

user> ((c/> 3) "abc")
[{:value "abc", :error-id :fschema.constraints/number?}]

user> ((c/> 3) 4)
4
```

### count=, count<, count>, count<=, and count>=

The `count=`, `count<`, `count>`, `count<=`, and `count>=` constraint
constructors are available to validate the length of strings and
sequences.

```clojure
user> ((c/count> 5) "abc")
[{:value "abc", :error-id :fschema.constraints/count>, :params [5]}]

user> ((c/count> 5) "abcefg")
"abcefg"

user> ((c/count= 2) [1 2 3])
[{:value [1 2 3], :error-id :fschema.constraints/count=, :params [2]}]

user> ((c/count= 2) [1 2])
[1 2]
```

### any

The `any` validator always validates successfully. It is essentially
the `identity` function marked as a validator.

```clojure
user> (c/any 2362)
2362

user> (c/any nil)
nil
```

## Validating Properties

### for-path

## Inspecting schemas

### inspect

## Error API

### error

### error?

## License

Distributed under the Eclipse Public License, the same as Clojure.
