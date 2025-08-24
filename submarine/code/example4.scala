//> using scala 3.7.2
//> using dep org.typelevel::cats-effect:3.6.3
//> using dep org.typelevel::cats-mtl:1.6.0

package example4

import cats.effect.IO
import cats.syntax.all.*
import cats.data.EitherT
import cats.mtl.Raise
import cats.mtl.Handle.*

type ChallengeId = String
type UserId = String
trait Challenge
trait AcceptedChallenge
trait Game

case class NotFound(id: ChallengeId)

enum AcceptError:
  case NotForUser(challenge: Challenge, userId: UserId)
  case IsCanceled(id: ChallengeId)

case class CreateGameError(message: String)

type Error = AcceptError | NotFound | CreateGameError

def find(id: ChallengeId)(using Raise[IO, NotFound]): IO[Challenge] = ???
def accept(challenge: Challenge, userId: UserId)(using Raise[IO, AcceptError]): IO[AcceptedChallenge] = ???
def create(challenge: AcceptedChallenge)(using Raise[IO, CreateGameError]): IO[Game] = ???

def acceptChallenge(id: ChallengeId, userId: UserId)(using Raise[IO, Error]): IO[Game] =
  for
    challenge <- find(id)
    accepted <- accept(challenge, userId)
    game <- create(accepted)
  yield game

import AcceptError.*

val x =
allow[Error]:
  acceptChallenge("challenge", "user").flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
.rescue:
  case NotFound(id) => IO.unit
  case NotForUser(challenge, userId) => IO.unit
  case IsCanceled(id) => IO.unit
  case CreateGameError(message) => IO.unit

val y =
allowF[IO, Error]{ implicit h =>
  acceptChallenge("challenge", "user").flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
}.rescue {
  case NotFound(id) => IO.unit
  case NotForUser(challenge, userId) => IO.unit
  case IsCanceled(id) => IO.unit
  case CreateGameError(message) => IO.unit
}
