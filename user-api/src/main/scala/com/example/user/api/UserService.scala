package com.example.user.api

import java.util.UUID

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}

trait UserService extends Service {
  def createUser: ServiceCall[CreateUser, User]
  def getUser(userId: UUID): ServiceCall[NotUsed, User]

  // Remove once we have a proper user service
  def getUsers: ServiceCall[NotUsed, Seq[User]]

  def descriptor = {
    import Service._
    named("user").withCalls(
      restCall(Method.POST,"/api/user", createUser),
      restCall(Method.GET, "/api/user/:id", getUser _),
      restCall(Method.GET, "/api/user", getUsers)
    ).withAutoAcl(true)
  }
}

case class User(id: UUID, name: String)

object User {
  implicit val format: Format[User] = Json.format
}

case class CreateUser(name: String)

object CreateUser {
  implicit val format: Format[CreateUser] = Json.format
}