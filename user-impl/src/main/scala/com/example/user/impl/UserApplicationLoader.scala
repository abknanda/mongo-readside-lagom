package com.example.user.impl

import com.example.user.api.UserService
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.rp.servicediscovery.lagom.scaladsl.LagomServiceLocatorComponents
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents
import play.modules.reactivemongo.{DefaultReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.MongoConnection

abstract class UserApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents
    with CassandraPersistenceComponents with ReactiveMongoComponents {


  override lazy val lagomServer = serverFor[UserService](wire[UserServiceImpl])
  override lazy val jsonSerializerRegistry = UserSerializerRegistry
  persistentEntityRegistry.register(wire[UserEntity])

  val mongodbUri: MongoConnection.ParsedURI = MongoConnection.parseURI(config.getString("mongodb.uri")).get
  implicit val reactiveMongoApi = new DefaultReactiveMongoApi(
    name = "default",
    parsedUri = mongodbUri,
    dbName = mongodbUri.db.getOrElse(sys.error("Could not parse database name from mongodb.uri: " + mongodbUri)),
    strictMode = false,
    configuration = configuration,
    applicationLifecycle = applicationLifecycle
  )
  val mongoRepo = wire[MongoRepoImpl]
  readSide.register(wire[UserEventProcessor])

}

class UserApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext) =
    new UserApplication(context) with LagomServiceLocatorComponents

  override def loadDevMode(context: LagomApplicationContext) =
    new UserApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[UserService])

}
