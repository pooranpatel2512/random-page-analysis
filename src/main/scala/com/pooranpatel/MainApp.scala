package com.pooranpatel

import akka.actor.{ ActorSystem, Props }
import com.pooranpatel.RandomPageAnalyzer.Protocol.AnalyzeRandomPages

object MainApp extends App {

  var count = 0

  val system = ActorSystem("random-page-analysis-system")
  val randomPageAnalyzer = system.actorOf(Props[RandomPageAnalyzer], "random-page-analyzer")

  randomPageAnalyzer ! AnalyzeRandomPages(100)

  sys addShutdownHook {
    system.shutdown()
    Thread.sleep(1000)
  }
}
