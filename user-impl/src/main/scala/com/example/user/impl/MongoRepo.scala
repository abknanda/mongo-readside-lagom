package com.example.user.impl

import java.util.UUID

import akka.Done
import akka.persistence.query.{NoOffset, Offset, TimeBasedUUID}
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import javax.inject.Inject
import play.api.libs.json.{Json, OFormat}
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.commands.LastError
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter, BSONObjectID}

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

trait MongoRepo {


  /**
    * Create the tables needed for this read side if not already created.
    */
  def createTables(offsetTable: String): Future[Done]

  /**
    * Load the offset of the last event processed.
    */
  def loadOffset(tag: AggregateEventTag[UserEvent]): Future[Offset]

  /**
    * Handle the post added event.
    */
  def handleEvent(event: UserEvent, offset: Offset): Future[Done]
}


class MongoRepoImpl @Inject()(implicit ec: ExecutionContext, val reactiveMongoApi: ReactiveMongoApi) extends MongoRepo with ReactiveMongoComponents {

  import reactivemongo.play.json._
  import collection.{JSONCollection, _}


  def usersCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection("users"))

  def offsetCollection: Future[JSONCollection] = reactiveMongoApi.database.map(_.collection("offset"))

  var offsetTableName = BSONObjectID.generate().toString()

  //val aa = UUID.fromString("8a9cf030-7e5d-11e9-b475-0800200c9a66")


  def insertDefault(offsetDocument: OffsetDocument) = offsetCollection.flatMap(_.insert(offsetDocument))
    .recover { case err: LastError => Done }

  override def createTables(offsetTable: String): Future[Done] = {
    offsetTableName = offsetTable
    val initDoc = OffsetDocument(offsetTable, None)
    usersCollection.flatMap(_ => offsetCollection).flatMap(_ => insertDefault(initDoc)).map(_ => Done)
  }

  /**
    * Load the offset of the last event processed.
    */
  override def loadOffset(tag: AggregateEventTag[UserEvent]): Future[Offset] = {
    offsetCollection.flatMap(_.find(
      selector = BSONDocument("_id" -> offsetTableName), projection = Option.empty[BSONDocument])
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
  override def handleEvent(event: UserEvent, offset: Offset): Future[Done] = {
    println(event)
    event match {
      case created: UserCreated => usersCollection.flatMap(_.insert(created)).
        flatMap {
          _ =>
            offsetCollection.flatMap(_.update(BSONDocument("_id" -> offsetTableName),
              BSONDocument("$set" -> BSONDocument("value" -> offset.asInstanceOf[TimeBasedUUID].value.toString))))
        }.map(_ => Done)
    }

  }
}


