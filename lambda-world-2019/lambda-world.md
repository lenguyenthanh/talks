# Lambda World Conference

## What is Lambda World?

- One of the biggest functional programming conference.
- Many good talks (Math/Category Theory/Scala/Haskell/Rust/Kotlin/Racket/Nim/Idris)
- Cool and passionate people.
- Rock Party on a castle.
- Awesome stickers

## Z

- Happy path
- Dark path (Error Handling)

## Types of errors

- Recoverable errors

  - Http request failed
  - Bluetooth connect failed
  - IO action failed


- Unrecoverable errors

  - Defects
  - System errors

- Unrecoverable errors

  - Usually defects
  - System errors

### Current state-of-the-art

- Use dynamically typed errors (Exception/Error) like Java
- Use Ordinary values like C, Erlang
- Use `Result<Error, Value>` for recoverable errors and `panic` for unrecoverable errors in Rust
- Use `Either<Error, Result>` for recoverable errors and `Throwable` for unrecoverable errors in Kotlin with Arrow

## Case Study with Axkid and Beckon

- Observable<Either<Error, Result>>
- [RxArrow](https://github.com/lenguyenthanh/rxarrow)

## Conclusion

- Take care of Errors
- Use Result/Either to handle recoverable Errors
- Crash the app when Exceptions happen (it should rarely happen)
- Use RxArrow (if you use RxJava/Kotlin :p)
