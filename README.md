# fschema

Elegant functional data validation and transformation for Clojure and
Clojurescript.

fschema has the following design goals:

- Have a simple intuitive API which allows for easy functional
  composition of validators and mutators.
- Provide detailed error messages which can easily be rendered into
  human readable (and localizable error messages).
- Allow for validation of individual properties within a complex
  schema.
- Return information about property paths in validation error
  messages.

## Installation

Add the following dependency to your `project.clj`:

```
[fschema "0.2.0"]
```

## Basics

All validators and mutators are composed of functions taking a signal
argument. A validator is a function that always returns the value it
was passed or an *error* value. A mutator may return the value it was
passed, a different "mutated" value, or return an *error* value. *error*
values control the flow of execution in fschema.

### Errors

An *error* value is created using the `error` function. A new error
value may be created by passing a map describing the `error` to the
error function. The result will be a vector containing this error
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

This allows us for a simple way to mark 

### Validators and Constraints

A validator is any function that takes a value and returns either the
value it was passed (for successful validation) or an *error* value.
An error value is any object that returns a truthy (not `nil` or
`false`) reponse to the `error?` function.

The simplest type of validator is a constraint. Constraints can be
created with the `constraint` function or the `defconstraint` macro.

Other validators can be creating by composing constraints using the
`schema-fn`, `each` and `where` functions.

### Constraints and nil values

Constraints have the following property: for every constraint *c*
other than the `not-nil` constraint, `(= (c nil) nil)`. That is, every
constraint when passed a `nil` value will silently fail and return
`nil` instead of an *error* value. To return an *error* when `nil` is
passed in, use the `not-nill` constraint. This is to facilitate the
functional composability of constraints.

### Mutators

A mutator is any function taking one argument. Mutators may also
return *error* values (see TODO) to signal that an error has occurred.

## Composing validators and mutators

### schema-fn

### each

### where

## Creating Constraints

### constraint

Constraints can be created with the `constraint` function which takes 

### defconstraint

## Built-in Constraints

### not-nil

The `not-nil` constraint is to be used whenever it is necessary to
ensure that a value is not nil. *All other constraints will return
*`nil`* when passed a *`nil`* value.


### Type checking
The `string?`, `map?`, `seq?`, `number?`, `integer?`, `boolean?`,
`keyword?`, and `symbol?` constraints are available to validate the
type of arguments.

*Note:  these functions intentionally mirror Clojure's type checking
functions.  The *`fschema.constraints`* namespace must never be loaded
with *`:use`* (which is bad practice anyway).*

```clojure
user> (c/string? 7)
[{:value 7, :error-id :fschema.constraints/string?}]

user> (c/map? {:a 1})
{:a 1}
```

### Regex
The `re-matches` constraint factory function can be used to match
strings against regular expressions. (*Note: the *`string?`* constraint is
invoked implicity when `re-matches` is used.*)

*Note: this function intentionally mirrors Clojure's *`re-matches`*
function.  The *`fschema.constraints`* namespace must never be loaded
with *`:use`* (which is bad practice anyway).*

```clojure
user> ((c/re-matches #"a.\*z") "abx")
[{:value "abx", :error-id :re-matches, :params [#"a.\*z"]}]

user> ((c/re-matches #"a.\*z") 4)
[{:value 4, :error-id :fschema.constraints/string?}]

user> ((c/re-matches #"a.\*z") "abcz")
"abcz"
```

### Numeric Range
The `=`, `<`, `>`, `<=`, and `>=` constraint constructors are
available to validate the range of numeric values. (*Note: the*
`number?` *constraint is invoked implicitly when any of the numeric
range functions are used.*)

*Note:  these functions intentionally mirror Clojure's comparison
operators.  The *`fschema.constraints`* namespace must never be loaded
with *`:use`* (which is bad practice anyway).*

```clojure
user> ((c/> 3) 2)
[{:value 2, :error-id :fschema.constraints/>, :params [3]}]

user> ((c/> 3) "abc")
[{:value "abc", :error-id :fschema.constraints/number?}]

user> ((c/> 3) 4)
4
```

### String and Sequence Length

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
