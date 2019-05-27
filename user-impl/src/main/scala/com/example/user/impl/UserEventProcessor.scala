package com.example.user.impl

import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, EventStreamElement, ReadSideProcessor}

import scala.concurrent.Future

class UserEventProcessor(mongoRepo: MongoRepo) extends ReadSideProcessor[UserEvent] {

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[UserEvent] = {
    new MongoDbReadSideProcessor[UserEvent] {

      override var offsetId: String = "UserEventReadProcessor"

      override def globalPrepare(): Future[Done] =
        mongoRepo.createTables(offsetId)


      override def prepare(tag: AggregateEventTag[UserEvent]): Future[Offset] =
        mongoRepo.loadOffset(tag)

      override def handle(): Flow[EventStreamElement[UserEvent], Done, NotUsed] = {
        Flow[EventStreamElement[UserEvent]]
          .mapAsync(1) { eventElement =>
            mongoRepo.handleEvent(eventElement.event, eventElement.offset)
          }
      }
    }
  }

  override def aggregateTags: Set[AggregateEventTag[UserEvent]] = UserEvent.UserEventTag.allTags

  // TODO return the tag for the events

}