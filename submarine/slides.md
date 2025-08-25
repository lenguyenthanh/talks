### Rethinking Monad Transformers with Raise Capability

Thanh Le

20-08-2025

---

## Disclaimer

- Credits go to the Daniel Spiewak
- Scala 3 syntax, but Scala 2 is also supported
- Focus IO (and Future) effect

---

## About me

- Born and raised in Vietnam
- Live in Sweden and work at Recorded Future
- Love functional programming and performance optimization
- Maintainer of some open source projects, most notably lichess.org
- Chess & calisthenics

---

## Outline

- Motivation
- Introduce our case study
- Different techniques of error handling in Scala
  - Untyped Errors
  - Typed Errors with nested Either
  - EitherT monad transformer
  - Monadic embedding of capabilities using cats-mtl
- Under the Hood (briefly)

---

## Errors and exceptions

There are three kinds of errors

- Domain specific errors
- System errors
- Bugs

---

## Motivation

- Safe
- Simple & concise
- Performant

---

## Case study: accept a game challenge

![accept challenge use case](./images/accept-challenge.png)

--

### Case study: accept a game challenge

![lila pr](./images/lila-pr.png)

--

### Case study: accept a game challenge

```scala [1-100]
def acceptChallenge(id: ChallengeId): IO[Game] =
  for
    challenge <- find(id)
    _ <- accept(challenge)
    game <- create(challenge)
  yield game

// Challenge must exist
def find(id: ChallengeId)       : IO[Challenge]
// challenge must be active
def accept(challenge: Challenge): IO[Unit]
// Can only play one game at a time
def create(challenge: Challenge): IO[Game]
```

--

### Case study: accept a game challenge

```scala [10-12]
// business logic
def acceptChallenge(id: ChallengeId): IO[Game] =
  for
    challenge <- find(id)
    _ <- accept(challenge)
    game <- create(challenge)
  yield game

// Usage, for example in service/API layer
acceptChallenge("challenge", "user")
  .flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
```

---

### Untyped Errors

```scala [1-2, 5-8, 11-12]
case class NotFound(id: ChallengeId)
    extends RuntimeException(s"Challenge with id $id not found")
def find(id: ChallengeId): IO[Challenge]

case class IsDeclined(id: Challenge)
    extends RuntimeException(s"Challenge $id is not for user $playerId")
case class IsCancelled(id: ChallengeId)
    extends RuntimeException(s"Challenge $id is canceled")
def accept(challenge: Challenge): IO[Challenge]

case class CreateGameError(message: String)
    extends RuntimeException(message)
def create(challenge: Challenge): IO[Game]
```

--

### Untyped Errors

```scala [4-12]
acceptChallenge("challenge", "user")
  .flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")

acceptChallenge("challenge", "user")
  .flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
  .recoverWith:
    case NotFound(id) => IO.unit
    case IsDeclined(challenge) => IO.unit
    case CreateGameError(message) => IO.unit
```

--

### Untyped Errors

```scala [7]
acceptChallenge("challenge", "user")
  .flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
  .recoverWith:
    case NotFound(id) => IO.unit
    case IsDeclined(challenge) => IO.unit
    case IsCancelled(id) => IO.unit
    case CreateGameError(message) => IO.unit
```

--

### Untyped Errors

```scala [9]
acceptChallenge("challenge", "user")
  .flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
  .recoverWith:
    case NotFound(id) => IO.unit
    case IsDeclined(challenge) => IO.unit
    case IsCancelled(id) => IO.unit
    case CreateGameError(message) => IO.unit
    case _ => IO.unit // catch all other exceptions
```

--

### Untyped Errors

- There is no exhaustive check for pattern matching
- We cannot distinguish between different kinds of errors
- Exceptions are hidden from the function signature

--

### How do we know what exceptions can be thrown?

- Read documentation
- Read the implementation and figure it out.
- Run the code and see what it actually throws (or our users)

--

### Conclusion for Untyped Errors

- ~Safe~
- ~Simple and concise~
- Performant

---

#### Typed errors with nested Either

```scala [1-8, 10]
// Look Ma, no more RuntimeException here!
case class NotFound(id: ChallengeId)

enum AcceptError:
  case IsDeclined(challenge: Challenge)
  case IsCanceled(id: ChallengeId)

case class CreateGameError(message: String)

type Error = NotFound | AcceptError | CreateGameError
```

--

#### Typed errors with nested Either

```scala [1-3, 12-19]
def find(id: ChallengeId):        IO[Either[NotFound, Challenge]]
def accept(challenge: Challenge): IO[Either[AcceptError, Unit]]
def create(challenge: Challenge): IO[Either[CreateGameError, Game]]

def acceptChallenge(id: ChallengeId): IO[Either[Error, Game]] =
  for
    challenge <- find(id)
    result <- accept(challenge)
    game <- create(result)
  yield game

acceptChallenge("challenge", "user")
  .flatMap:
    case Right(game) =>
      IO.println(s"Challenge accepted, game created: $game")
    case Left(NotFound(id)) => IO.unit
    case Left(IsDeclined(challenge)) => IO.unit
    case Left(IsCanceled(id)) => IO.unit
    case Left(CreateGameError(message)) => IO.unit
```

--

#### this does not compile :cry:

```scala [1-100]
// We can't compose many IO[Either[A, B]] together
def acceptChallenge(id: ChallengeId): IO[Either[Error, Game]] =
  for
    challenge <- find(id)
    result <- accept(challenge)
    game <- create(result)
  yield game
```

--

### Let's fix it

```scala [1-100]
def acceptChallenge(id: ChallengeId): IO[Either[Error, Game]] =
  for
    challengeOrError <- find(id)
    acceptedOrError <- challengeOrError match
      case Left(error) => IO.pure(Left[Error, Unit](error))
      case Right(challenge) => accept(challenge).map(_.as(challenge))
    game <- acceptedOrError match
      case Left(error) => IO.pure(Left[Error, Game](error))
      case Right(challenge) => create(challenge)
  yield game
```

--

### Or we can do it with nested flatMap

```scala [1-100]
def acceptChallenge(id: ChallengeId): IO[Either[Error, Game]] =
    find(id).flatMap:
      case Left(error) => IO.pure(Left(error))
      case Right(challenge) =>
        accept(challenge).flatMap:
          case Left(error) => IO.pure(Left(error))
          case Right(_) =>
            create(challenge).map:
              case Left(error) => Left(error)
              case Right(game) => Right(game)
```

--

### Conclusion for typed error with nested Either

- Safe
- ~Simple and concise~
- Performant

---

### EitherT (monad transformers)

```scala [2-9]
import cats.data.EitherT
def accept(id: ChallengeId): IO[Either[Error, Game]] =
  val eitherT: EitherT[IO, Error, Game] =
    for
      challenge <- EitherT(find(id))
      _ <- EitherT(accept(challenge))
      game <- EitherT(create(challenge))
    yield game
  eitherT.value
```

--

### Few words about EitherT

```scala
// EitherT.apply(IO[Either[A, B]]):  EitherT[IO, A, B]
// EitherT[IO, A, B].value: IO[Either[A, B]
class EitherT[F[_], A, B] private (val value: F[Either[A, B]]):
  def flatMap[C](f: B => EitherT[F, A, C]): EitherT[F, A, C] = ???
  def map[C](f: B => C): EitherT[F, A, C] = ???
object EitherT:
  def apply[F[_], A, B](value: F[Either[A, B]]): EitherT[F, A, B] =
    new EitherT(value)
```

--

### Conclusion for EitherT

- Safe
- ~Simple and concise~
- ~Performant~

--

### Notes on EitherT and cats-effect

 - There are some limitation with concurrent code based
    - https://github.com/typelevel/fs2/issues/3199
    - https://github.com/typelevel/cats/issues/4308
    - https://github.com/typelevel/cats-effect/discussions/3765
    - https://github.com/typelevel/fs2/pull/2895
    - https://github.com/typelevel/cats-effect/issues/2448

---

### cats-mtl with Raise capability

```scala [4-17]
//> using dep org.typelevel::cats-mtl:1.6.0
import cats.mtl.Raise

def find(id: ChallengeId)(using Raise[IO, NotFound]): IO[Challenge]
def accept(challenge: Challenge)(using Raise[IO, AcceptError]): IO[Unit]
def create(challenge: Challenge)(using Raise[IO, CreateGameError]): IO[Game]

// type Error = NotFound | AcceptError | CreateGameError
def acceptChallenge(id: ChallengeId)(using Raise[IO, Error]): IO[Game] =
  for
    challenge <- find(id)
    _ <- accept(challenge)
    game <- create(challenge)
  yield game
```

--

### cats-mtl with Raise capability

```scala [2-9]
import cats.mtl.Handle
Handle.allow[Error]:
  acceptChallenge("challenge", "user").flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
.rescue:
  case NotFound(id) => IO.unit
  case IsDeclined(challenge) => IO.unit
  case IsCanceled(id) => IO.unit
  case CreateGameError(message) => IO.unit
```

--

### cats-mtl with Raise capability

```scala [2,3]
Handle.allow[Error]:
  acceptChallenge("challenge", "user").flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
.rescue:
  case NotFound(id) => IO.unit
  case IsDeclined(challenge) => IO.unit
  case IsCanceled(id) => IO.unit
  case CreateGameError(message) => IO.unit
```

--

### cats-mtl with Raise capability

```scala [1-14]
// context function
type IORaise[E, A] = Raise[IO, E] ?=> IO[A]

def find(id: ChallengeId):        IORaise[NotFound, Challenge]
def accept(challenge: Challenge): IORaise[AcceptChallengeError, Unit]
def create(challenge: Challenge): IORaise[CreateGameError, Game]

def acceptChallenge(id: ChallengeId): IORaise[Error, Game] =
  for
    challenge <- findChallenge(id)
    _ <- accept(challenge)
    game <- createGame(challenge)
  yield game
```

--

### cats-mtl with Raise capability

```scala [1-9]
// scala 2
allowF[IO, Error] { implicit h =>
  acceptChallenge("challenge", "user").flatMap{ game =>
    IO.println(s"Challenge accepted, game created: $game")
 }
}.rescue {
  case NotFound(id) => IO.unit
  case IsDeclined(challenge) => IO.unit
  case IsCanceled(id) => IO.unit
  case CreateGameError(message) => IO.unit
}
```

--

### Conclusion for cats-mtl

- Safe
- Simple and concise
- Performant

--

### Some caveats

- Additional dependency on `cats-mtl`
- a bit of new concepts to learn (e.g. `Raise`, `allow`, `rescue`)
- Compilation error messages can be cryptic

---

### Under the hood

- Re-use existing abstraction from cats and cats-mtl
- Context functions (the A ?=> B syntax)
- inline functions

---

### links

- The cats-mtl pr: https://github.com/typelevel/cats-mtl/pull/619
- PRs of using cats-mtl in lichess
  - https://github.com/lichess-org/lila-search/pull/542
  - https://github.com/lichess-org/lila/pull/17944

---

### thank you
