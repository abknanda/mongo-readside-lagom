package com.example.user.impl

import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler

trait MongoDbReadSideProcessor[Event <: AggregateEvent[Event]] extends ReadSideHandler[Event]{


  var offsetId: String;
}
