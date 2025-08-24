//> using scala 3.7.2
//> using dep org.typelevel::cats-effect:3.6.3
//> using dep org.typelevel::cats-mtl:1.6.0

package example5

import cats.effect.IO
import cats.syntax.all.*
import cats.data.EitherT
import cats.mtl.Raise
import cats.mtl.Handle.*

type ChallengeId = String
trait Challenge
trait Game

case class NotFound(id: ChallengeId)

enum AcceptError:
  case IsDeclined(challenge: Challenge)
  case IsCanceled(id: ChallengeId)

case class CreateGameError(message: String)

type Error = AcceptError | NotFound | CreateGameError

def find(id: ChallengeId): IO[Option[Challenge]] = ???
def accept(challenge: Challenge)(using Raise[IO, AcceptError]): IO[Unit] = ???
def create(challenge: Challenge)(using Raise[IO, CreateGameError]): IO[Game] = ???

import cats.mtl.syntax.all.*
def acceptChallenge(id: ChallengeId)(using Raise[IO, Error]): IO[Game] =
  for
    maybeChallenge <- find(id)
    challenge <- maybeChallenge.fold(NotFound(id).raise)(IO.pure)
    _ <- accept(challenge)
    game <- create(challenge)
  yield game


import AcceptError.*
val x =
allow[Error]:
  acceptChallenge("challenge").flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
.rescue:
  case NotFound(id) => IO.unit
  case IsDeclined(challenge) => IO.unit
  case IsCanceled(id) => IO.unit
  case CreateGameError(message) => IO.unit
