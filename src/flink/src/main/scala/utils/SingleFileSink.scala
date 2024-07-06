package utils

import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.api.connector.sink2.{SinkWriter, WriterInitContext}
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption

class SingleFileSink(val path: String) extends Sink[String] {
  // implemented for backward compatibility (as specified in the docs)
  override def createWriter(context: Sink.InitContext): SinkWriter[String] =
    createWriter(context)

  override def createWriter(context: WriterInitContext): SinkWriter[String] =
    new SinkWriter[String]() {
      // create new writer
      private val writer = Files.newBufferedWriter(
        Paths.get(path),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
      )

      // write as string
      override def write(element: String, context: SinkWriter.Context): Unit = {
        writer.write(s"$element\n")
        writer.flush()
      }

      override def flush(endOfInput: Boolean): Unit = writer.flush()

      override def close(): Unit = writer.close()
    }
}
