package com.example.user.impl

import akka.Done
import akka.persistence.query.{NoOffset, Offset, TimeBasedUUID}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag}
import play.modules.reactivemongo.ReactiveMongoApi
import reactivemongo.api.commands.{LastError, WriteResult}
import reactivemongo.bson.BSONDocument

import scala.concurrent.{ExecutionContext, Future}


trait MongoReadHandler[Event <: AggregateEvent[Event]] {

  import reactivemongo.play.json._
  import collection.JSONCollection

  def offsetId: String

  def reactiveMongo: ReactiveMongoApi

  private val offsetCollectionName: String = "offsetstore";

  private def insertDefault(offsetDocument: OffsetDocument)(implicit ec: ExecutionContext) = offsetCollection.flatMap(_.insert(offsetDocument))
    .recover { case WriteResult.Code(11000) => Done } //Inserting Duplicate Handling

  def offsetCollection(implicit ec: ExecutionContext): Future[JSONCollection] = reactiveMongo.database.map(_.collection(offsetCollectionName))

  /**
    * Create the collection needed for this read side if not already created.
    */
  def createOffsetCollection(implicit ec: ExecutionContext): Future[Done] = {
    val initDoc = OffsetDocument(offsetId, None)
    offsetCollection.flatMap(_ => insertDefault(initDoc)).map(_ => Done)
  }

  /**
    * Load the offset of the last event processed.
    */
  def loadOffset(tag: AggregateEventTag[Event])(implicit ec: ExecutionContext): Future[Offset] = {
    offsetCollection.flatMap(_.find(
      selector = BSONDocument("_id" -> offsetId), projection = Option.empty[BSONDocument])
      .one[OffsetDocument])
      .map {
        case Some(offsetDocument) => offsetDocument.value match {
          case None => NoOffset
          case Some(value) => Offset.timeBasedUUID(value)
        }
        case None => NoOffset
      }
  }

  /**
    * Handle the post added event.
    */
  def handleEvent(event: Event, offset: Offset)(eventHandler: Event => Future[Done])(implicit ec: ExecutionContext): Future[Done] =
    eventHandler(event)
      .flatMap {
        _ =>
          offsetCollection.flatMap(_.update(BSONDocument("_id" -> offsetId),
            BSONDocument("$set" -> BSONDocument("value" -> offset.asInstanceOf[TimeBasedUUID].value.toString))))
      }.map(_ => Done)
}
