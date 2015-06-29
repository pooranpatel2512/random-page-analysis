package com.pooranpatel

import akka.actor.Actor
import com.pooranpatel.RandomPageAnalyzer.LocationHeaderDoesNotExist
import com.pooranpatel.RandomPageAnalyzer.Model.RandomPageUrlAndResponseCode
import com.pooranpatel.RandomPageAnalyzer.Protocol.AnalyzeRandomPages
import spray.client.pipelining._
import spray.http.{ HttpHeader, HttpHeaders, StatusCode, StatusCodes }

import scala.concurrent.Future
import scala.util.Success

class RandomPageAnalyzer extends Actor {

  import context.dispatcher

  val pipeline = sendReceive
  val randomPageProvider = "http://www.randomwebsite.com/cgi-bin/random.pl"

  var numberOfPagesToAnalyze = 0
  var analyzedPageResults: List[RandomPageUrlAndResponseCode] = List.empty[RandomPageUrlAndResponseCode]

  override def receive: Receive = {
    case AnalyzeRandomPages(count) =>
      numberOfPagesToAnalyze = count
      for(i <- 1 to numberOfPagesToAnalyze) {
        val result = for {
          randomPage <- getRandomPage
          pageUrl <- extractPageUrl(randomPage.headers)
          httpResponse <- makeHttpRequest(pageUrl)
        } yield (pageUrl, httpResponse.status)

        result.onComplete {
          case Success(r) =>
            storeResult(r._1, r._2)
            printResultsIfAllHttpResponsesAreReceived
          case _ =>
            printResultsIfAllHttpResponsesAreReceived
        }
      }
  }

  private def getRandomPage = pipeline(Get(randomPageProvider))

  private def extractPageUrl(responseHeaders: List[HttpHeader]): Future[String] = {
    val locationHeader = responseHeaders.find({
      case h: HttpHeaders.Location => true
      case _ => false
    })
    locationHeader match {
      case Some(h) => Future.successful(h.value)
      case None => Future.failed(LocationHeaderDoesNotExist)
    }
  }

  private def makeHttpRequest(pageUrl: String) = pipeline(Get(pageUrl))

  private def printResult(result: RandomPageUrlAndResponseCode) = {
    if(result.statusCode != StatusCodes.OK) {
      println(s"URL: ${result.pageDomainName} , HTTP Status code: ${result.statusCode.intValue}")
    } else {
      println(s"URL: ${result.pageDomainName}")
    }
  }

  private def printResultsIfAllHttpResponsesAreReceived = {
    numberOfPagesToAnalyze -= 1
    if(numberOfPagesToAnalyze == 0) {
      println("----------- Results -------------")
      val sortedResults = analyzedPageResults.sortWith(_.pageDomainName.toLowerCase < _.pageDomainName.toLowerCase)
      sortedResults.foreach( result => printResult(result))
      println("----------- Results -------------")
    }
  }

  private def storeResult(pageUrl: String, statusCode: StatusCode) = {
    val pageDomainName = pageUrl.replaceFirst("http://", "").replaceFirst("www.", "")
    val result: RandomPageUrlAndResponseCode = RandomPageUrlAndResponseCode(pageDomainName, statusCode)
    analyzedPageResults = result :: analyzedPageResults
  }
}

object RandomPageAnalyzer {

  object Protocol {
    case class AnalyzeRandomPages(count: Int)
  }

  object Model {
    case class RandomPageUrlAndResponseCode(pageDomainName: String, statusCode: StatusCode)
  }

  object LocationHeaderDoesNotExist extends Throwable
}
