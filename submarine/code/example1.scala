//> using scala 3.7.2
//> using dep org.typelevel::cats-effect:3.6.3

package example1

import cats.effect.IO
import cats.syntax.all.*

type ChallengeId = String
type UserId = String
trait Challenge
trait AcceptedChallenge
trait Game

case class ChallengeNotFound(id: ChallengeId) extends RuntimeException(s"Challenge with id $id not found")

case class ChallengeNotForUser(id: Challenge, userId: UserId) extends RuntimeException(s"Challenge $id is not for user $userId")
case class ChallengeIsCanceled(id: ChallengeId) extends RuntimeException(s"Challenge $id is canceled")

case class CreateGameError(message: String) extends RuntimeException(message)

def find(id: ChallengeId): IO[Challenge] = ???
def accept(challenge: Challenge, userId: UserId): IO[AcceptedChallenge] = ???
def create(challenge: AcceptedChallenge): IO[Game] = ???

def acceptChallenge(id: ChallengeId, userId: UserId): IO[Game] =
  for
    challenge <- find(id)
    accepted <- accept(challenge, userId)
    game <- create(accepted)
  yield game


val x =
acceptChallenge("challenge", "user")
  .flatMap: game =>
    IO.println(s"Challenge accepted, game created: $game")
  .recoverWith:
    case ChallengeNotFound(id) => IO.unit
    case ChallengeNotForUser(challenge, userId) => IO.unit
    case ChallengeIsCanceled(id) => IO.unit
    case CreateGameError(message) => IO.unit
