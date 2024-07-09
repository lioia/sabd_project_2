package models

import org.apache.flink.streaming.api.datastream.DataStream

case class QueryReturn(
    val window: DataStream[QueryOutput],
    val prefix: String
)

case class QueryOutput(tuples: Long, ts: Long, output: String)
