package com.example.user.impl

import java.util.UUID

import akka.Done
import com.example.user.impl.JsonFormats._
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AggregateEventTagger, PersistentEntity}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json.{Format, Json, OFormat}

class UserEntity extends PersistentEntity {
  override type Command = UserCommand
  override type Event = UserEvent
  override type State = Option[User]

  override def initialState = None

  override def behavior: Behavior = {
    case Some(user) =>
      Actions().onReadOnlyCommand[GetUser.type, Option[User]] {
        case (GetUser, ctx, state) => ctx.reply(state)
      }.onReadOnlyCommand[CreateUser, Done] {
        case (CreateUser(id, name), ctx, state) => ctx.invalidCommand("User already exists")
      }
    case None =>
      Actions().onReadOnlyCommand[GetUser.type, Option[User]] {
        case (GetUser, ctx, state) => ctx.reply(state)
      }.onCommand[CreateUser, Done] {
        case (CreateUser(id, name), ctx, state) =>
          ctx.thenPersist(UserCreated(id, name))(_ => ctx.reply(Done))
      }.onEvent {
        case (UserCreated(id, name), state) => Some(User(id, name))
      }
  }
}

case class User(id: UUID, name: String)

object User {
  implicit val format: Format[User] = Json.format
}

sealed trait UserEvent extends AggregateEvent[UserEvent] {
  override def aggregateTag: AggregateEventTagger[UserEvent] =
    UserEvent.UserEventTag
}

object UserEvent {
  val numberOfShards = 4
  val UserEventTag = AggregateEventTag.sharded[UserEvent](numberOfShards)
}

case class UserCreated(_id: UUID, name: String) extends UserEvent

object UserCreated {
  implicit val format: Format[UserCreated] = Json.format
  implicit val format2: OFormat[UserCreated] = Json.format
}

sealed trait UserCommand

case class CreateUser(id: UUID, name: String) extends UserCommand with ReplyType[Done]

object CreateUser {
  implicit val format: Format[CreateUser] = Json.format
}

case object GetUser extends UserCommand with ReplyType[Option[User]] {
  implicit val format: Format[GetUser.type] = singletonFormat(GetUser)
}



object UserSerializerRegistry extends JsonSerializerRegistry {
  override def serializers = List(
    JsonSerializer[User],
    JsonSerializer[UserCreated],
    JsonSerializer[CreateUser],
    JsonSerializer[GetUser.type]
  )
}