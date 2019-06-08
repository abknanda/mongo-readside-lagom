package com.example.user.impl

import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import reactivemongo.bson.{BSONDocument, BSONDocumentReader, BSONDocumentWriter}

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