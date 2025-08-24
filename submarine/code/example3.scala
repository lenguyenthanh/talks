//> using scala 3.7.2
//> using dep org.typelevel::cats-effect:3.6.3

package example3

import cats.effect.IO
import cats.syntax.all.*
import cats.data.EitherT

type ChallengeId = String
type UserId = String
trait Challenge
trait AcceptedChallenge
trait Game

case class NotFound(id: ChallengeId) extends RuntimeException(s"Challenge with id $id not found")

enum AcceptError:
  case NotForUser(challenge: Challenge, userId: UserId)
  case IsCanceled(id: ChallengeId)

case class CreateGameError(message: String) extends RuntimeException(message)

type Error = AcceptError | NotFound | CreateGameError

def find(id: ChallengeId): IO[Either[NotFound, Challenge]] = ???
def accept(challenge: Challenge, userId: UserId): IO[Either[Error, AcceptedChallenge]] = ???
def create(challenge: AcceptedChallenge): IO[Either[CreateGameError, Game]] = ???

def acceptChallenge(id: ChallengeId, userId: UserId): IO[Either[Error, Game]] =
  (for
    challenge <- EitherT(find(id))
    accepted <- EitherT(accept(challenge, userId))
    game <- EitherT(create(accepted))
  yield game).value

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
