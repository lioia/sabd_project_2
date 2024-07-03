package models

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream

class QueryReturn(
    val window: DataStream[String],
    val prefix: String
)
