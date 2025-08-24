//> using scala 3.7.2
//> using dep org.typelevel::cats-effect:3.6.3

package example2

import cats.effect.IO
import cats.syntax.all.*

type ChallengeId = String
type UserId = String
trait Challenge
trait AcceptedChallenge
trait Game

case class NotFound(id: ChallengeId)

enum AcceptError:
  case NotForUser(id: ChallengeId, userId: UserId)
  case IsCanceled(id: ChallengeId)

case class CreateGameError(message: String)

type Error = NotFound | AcceptError | CreateGameError

def find(id: ChallengeId): IO[Either[NotFound, Challenge]] = ???
def accept(challenge: Challenge, userId: UserId): IO[Either[AcceptError, AcceptedChallenge]] = ???
def create(challenge: AcceptedChallenge): IO[Either[CreateGameError, Game]] = ???

// def acceptChallenge(id: ChallengeId, userId: UserId): IO[Either[Error, Unit]] =
//   for
//     challenge <- find(id)
//     result <- accept(challenge, userId)
//     game <- create(result)
//   yield game

def acceptChallenge(id: ChallengeId, userId: UserId): IO[Either[Error, Game]] =
  for
    challengeOrError <- find(id)
    acceptedOrError <- challengeOrError match
      case Left(error) => IO.pure(Left[Error, AcceptedChallenge](error))
      case Right(challenge) => accept(challenge, userId)
    game <- acceptedOrError match
      case Left(error) => IO.pure(Left[Error, Game](error))
      case Right(accepted) => create(accepted)
  yield game

def acceptChallenge2(id: ChallengeId, userId: UserId): IO[Either[Error, Game]] =
    find(id).flatMap:
      case Left(error) => IO.pure(Left(error))
      case Right(challenge) =>
        accept(challenge, userId).flatMap:
          case Left(error) => IO.pure(Left(error))
          case Right(acceptedChallenge) =>
            create(acceptedChallenge).map:
              case Left(error) => Left(error)
              case Right(game) => Right(game)

import AcceptError.*
val x =
acceptChallenge("challenge", "user")
  .flatMap:
    case Right(game) =>
      IO.println(s"Challenge accepted, game created: $game")
    case Left(NotFound(id)) => IO.unit
    case Left(NotForUser(challenge, userId)) => IO.unit
    case Left(IsCanceled(id)) => IO.unit
    case Left(CreateGameError(message)) => IO.unit
