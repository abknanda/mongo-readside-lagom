package com.example.user.impl

import java.util.UUID

import akka.Done
import akka.persistence.query.{NoOffset, Offset, TimeBasedUUID}
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import javax.inject.Inject
import play.api.libs.json.{Json, OFormat}
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.commands.LastError
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

import scala.concurrent.{ExecutionContext, Future}

case class OffsetDocument(_id: String, value: Option[UUID])

object OffsetDocument {
  implicit val format: OFormat[OffsetDocument] = Json.format[OffsetDocument]

  implicit val linkReader: BSONDocumentReader[OffsetDocument] =
    BSONDocumentReader[OffsetDocument] { doc: BSONDocument =>
      OffsetDocument(
        doc.getAs[String]("_id").getOrElse(""),
        doc.getAs[String]("value").map { value =>
          UUID.fromString(value)
        })
    }

  implicit val linkWriter: BSONDocumentWriter[OffsetDocument] =
    BSONDocumentWriter[OffsetDocument] { offset: OffsetDocument =>
      offset.value match {
        case Some(_) => BSONDocument(
          "_id" -> offset._id,
          "value" -> offset.value.get.toString)

        case None => BSONDocument(
          "_id" -> offset._id)
      }

    }


}

trait MongoReadHandler {


  val offsetId: String

  /**
    * Create the tables needed for this read side if not already created.
    */
  def createOffsetCollection: Future[Done]

  /**
    * Load the offset of the last event processed.
    */
  def loadOffset(tag: AggregateEventTag[UserEvent]): Future[Offset]

  /**
    * Handle the post added event.
    */
  def handleEvent(event: UserEvent, offset: Offset)(eventHandler: UserEvent => Future[Done]): Future[Done]
}


class MongoReadHandlerImpl @Inject()(val offsetId: String)(implicit ec: ExecutionContext, val reactiveMongoApi: ReactiveMongoApi) extends MongoReadHandler with ReactiveMongoComponents {

  import reactivemongo.play.json._
  import collection.{JSONCollection, _}


  val offsetCollectionName: String = "offset";

  def offsetCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection(offsetCollectionName))


  def insertDefault(offsetDocument: OffsetDocument) = offsetCollection.flatMap(_.insert(offsetDocument))
    .recover { case err: LastError =>
      if (err.getMessage().contains("duplicate key error collection")) Done
      else throw err
    } //Inserting Duplicate Handling

  override def createOffsetCollection: Future[Done] = {
    val initDoc = OffsetDocument(offsetId, None)
    offsetCollection.flatMap(_ => insertDefault(initDoc)).map(_ => Done)
  }

  /**
    * Load the offset of the last event processed.
    */
  override def loadOffset(tag: AggregateEventTag[UserEvent]): Future[Offset] = {
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
  override def handleEvent(event: UserEvent, offset: Offset)(eventHandler: UserEvent => Future[Done]): Future[Done] = {
    eventHandler(event)
      .flatMap {
      _ =>
        offsetCollection.flatMap(_.update(BSONDocument("_id" -> offsetId),
          BSONDocument("$set" -> BSONDocument("value" -> offset.asInstanceOf[TimeBasedUUID].value.toString))))
    }.map(_ => Done)
  }
}
