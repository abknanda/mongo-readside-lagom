package com.example.user.impl

import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.LastError

import scala.concurrent.{ExecutionContext, Future}

class UserEventProcessor(implicit ec: ExecutionContext, reactiveMongoApi: ReactiveMongoApi) extends ReadSideProcessor[UserEvent] {

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[UserEvent] = {
    new MongoDbReadSideProcessor[UserEvent] {

      override val mongoRepo: MongoReadHandler = new MongoReadHandlerImpl("users-offset")

      override def globalPrepare(): Future[Done] =
        mongoRepo.createOffsetCollection

      override def prepare(tag: AggregateEventTag[UserEvent]): Future[Offset] = mongoRepo.loadOffset(tag)

      override def handle(): Flow[EventStreamElement[UserEvent], Done, NotUsed] = {
        Flow[EventStreamElement[UserEvent]]
          .mapAsync(1) { eventElement =>
            mongoRepo.handleEvent(eventElement.event, eventElement.offset)(insertUser)
          }
      }


      private def insertUser: UserEvent => Future[Done] = userEvent => {
        import reactivemongo.play.json._
        import collection.{JSONCollection, _}

        userEvent match {
          case userCreated: UserCreated =>
            println(userEvent)
            val usersCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection("users"))
            usersCollection.flatMap(_.insert(userCreated))
              .recover { case err: LastError =>
                if (err.getMessage().contains("duplicate key error collection")) Done
                else throw err
              } //Inserting Duplicate Handling
              .map(_ => Done)
            //TODO - Create upsert statement
        }

      }

    }
  }

  override def aggregateTags: Set[AggregateEventTag[UserEvent]] = UserEvent.UserEventTag.allTags

}
