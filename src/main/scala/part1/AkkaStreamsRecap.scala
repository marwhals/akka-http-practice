package part1

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

/**
 * Notes:
 * Akka HTTP is based on Akka Streams
 */

object AkkaStreamsRecap extends App {

  implicit val system = ActorSystem("AkkaStreamsRecap")
  implicit val materializer = ActorMaterializer() // allocates right resources for constructing akka streams components
  import system.dispatcher

  val source = Source(1 to 100)
  val sink = Sink.foreach[Int](println)
  val flow = Flow[Int].map(x => x + 1)

  val runnableGraph = source.via(flow).to(sink)
  val simpleMaterializedValue = runnableGraph.run()

  // MATERIALIZED VALUE
  val sumSink = Sink.fold[Int, Int](0)((currentSum, element) => currentSum + element)
  val sumFuture = source.runWith(sumSink)

  sumFuture.onComplete {
    case Success(sum) => println(s"The sum of all the numbers from the simple source is: $sum")
    case Failure(ex) => println(s"Summing up all the numbers from the simple source FAILED: $ex")
  }

  val anotherMaterializedValue = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.left).run()
  /*
  1 - materializing a graph means materializing ALL the components
  2 - a materialized value can be anything at all
   */

  /*
  Backpressure actions
  - buffer elements
  - apply a strategy in case the buffer overflows
  - fail the entire stream
   */

  val bufferedFlow = Flow[Int].buffer(10, OverflowStrategy.dropHead)

  source.async
    .via(bufferedFlow).async
    .runForeach { e =>
      // a slow consumer
      Thread.sleep(100)
      println(e)
    }

}
