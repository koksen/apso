package com.velocidi.apso.json

import java.net.URI

import scala.concurrent.duration._

import com.typesafe.config.{ Config, ConfigFactory }
import io.circe._
import io.circe.literal._
import io.circe.parser._
import io.circe.syntax._
import org.joda.time.{ DateTime, Interval, LocalDate, Period }
import org.specs2.mutable.Specification
import spray.json.DefaultJsonProtocol._
import spray.json._
import squants.market._

class ExtraJsonProtocolSpec extends Specification {

  "The object ExtraJsonProtocol" should {
    import ExtraJsonProtocol._

    "provide a JsonFormat for FiniteDuration" in {
      val duration = 10.seconds
      val durationJsonString = """{"milliseconds":10000}"""

      duration.toJson.compactPrint mustEqual durationJsonString
      durationJsonString.parseJson.convertTo[FiniteDuration] mustEqual duration

      """{"seconds": 2}""".parseJson.convertTo[FiniteDuration] mustEqual 2.seconds
      """{"minutes": 2}""".parseJson.convertTo[FiniteDuration] mustEqual 2.minutes
      """{"hours":   2}""".parseJson.convertTo[FiniteDuration] mustEqual 2.hours
      """{"days":    2}""".parseJson.convertTo[FiniteDuration] mustEqual 2.days
      """{"meters":  2}""".parseJson.convertTo[FiniteDuration] must throwA[DeserializationException]

      """2""".parseJson.convertTo[FiniteDuration] mustEqual 2.milliseconds
      """"2s"""".parseJson.convertTo[FiniteDuration] mustEqual 2.seconds
      """"2m"""".parseJson.convertTo[FiniteDuration] mustEqual 2.minutes
      """"2h"""".parseJson.convertTo[FiniteDuration] mustEqual 2.hours
      """"2d"""".parseJson.convertTo[FiniteDuration] mustEqual 2.days
      """"garbagio"""".parseJson.convertTo[FiniteDuration] must throwA[DeserializationException]

      "true".parseJson.convertTo[FiniteDuration] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for FiniteDuration" in {
      val duration = 10.seconds
      val durationJson = json"""{"milliseconds":10000}"""

      duration.asJson mustEqual durationJson
      durationJson.as[FiniteDuration] must beRight(duration)

      decode[FiniteDuration]("""{"seconds": 2}""") must beRight(2.seconds)
      decode[FiniteDuration]("""{"minutes": 2}""") must beRight(2.minutes)
      decode[FiniteDuration]("""{"hours":   2}""") must beRight(2.hours)
      decode[FiniteDuration]("""{"days":    2}""") must beRight(2.days)
      decode[FiniteDuration]("""{"meters":  2}""") must beLeft

      decode[FiniteDuration]("""2""") must beRight(2.milliseconds)
      decode[FiniteDuration](""""2s"""") must beRight(2.seconds)
      decode[FiniteDuration](""""2m"""") must beRight(2.minutes)
      decode[FiniteDuration](""""2h"""") must beRight(2.hours)
      decode[FiniteDuration](""""2d"""") must beRight(2.days)
      decode[FiniteDuration](""""garbagio"""") must beLeft

      decode[FiniteDuration]("true") must beLeft
    }

    "provide a JsonFormat for Interval" in {
      val interval = new Interval(1000, 2000)
      val intervalJsonString = """{"startMillis":1000,"endMillis":2000}"""

      interval.toJson.compactPrint mustEqual intervalJsonString
      intervalJsonString.parseJson.convertTo[Interval] mustEqual interval
      """{"invalidObject":true}""".parseJson.convertTo[Interval] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for Interval" in {
      val interval = new Interval(1000, 2000)
      val intervalJson = json"""{"startMillis":1000,"endMillis":2000}"""

      interval.asJson mustEqual intervalJson
      intervalJson.as[Interval] must beRight(interval)
    }

    "provide a JsonFormat for Period" in {
      val pStrings = Seq("P1D", "P1M2D", "P1M2DT10H30M")
      pStrings.forall { s =>
        val period = new Period(s)
        period.toJson mustEqual JsString(s)
        s""""$s"""".parseJson.convertTo[Period] mustEqual period
      }

      """"garbage"""".parseJson.convertTo[Period] must throwA[DeserializationException]
      """"PXD"""".parseJson.convertTo[Period] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for Period" in {
      val pStrings = Seq("P1D", "P1M2D", "P1M2DT10H30M")
      pStrings.forall { s =>
        val period = new Period(s)
        period.asJson.pretty(Printer.noSpaces) mustEqual s""""$s""""
        decode[Period](s""""$s"""") must beRight(period)
      }

      decode[Period](""""garbage"""") must beLeft
      decode[Period](""""PXD"""") must beLeft
    }

    "provide a JsonFormat for URI" in {
      val uri = new URI("http://example.com")
      val uriJsonString = """"http://example.com""""

      uri.toJson.compactPrint mustEqual uriJsonString
      uriJsonString.parseJson.convertTo[URI] mustEqual uri
      "true".parseJson.convertTo[URI] must throwA[DeserializationException]
      """"{invalidUri}"""".parseJson.convertTo[URI] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for URI" in {
      val uri = new URI("http://example.com")
      val uriJsonString = json""""http://example.com""""

      uri.asJson mustEqual uriJsonString
      uriJsonString.as[URI] must beRight(uri)
      decode[URI]("true") must beLeft
      json""""{invalidUri}"""".as[URI] must beLeft
    }

    "provide a JsonFormat for Config" in {
      val config = ConfigFactory.parseString("""
        |a = 123
        |b {
        |  x = 1d
        |  y = "string"
        |}
      """.stripMargin)
      val configJsonString = """{"a":123,"b":{"x":"1d","y":"string"}}"""

      config.toJson.compactPrint mustEqual configJsonString
      configJsonString.parseJson.convertTo[Config] mustEqual config
      "true".parseJson.convertTo[Config] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for Config" in {
      val config = ConfigFactory.parseString("""
                                               |a = 123
                                               |b {
                                               |  x = 1d
                                               |  y = "string"
                                               |}
                                             """.stripMargin)
      val configJsonString = json"""{"a":123,"b":{"x":"1d","y":"string"}}"""

      config.asJson mustEqual configJsonString
      configJsonString.as[Config] must beRight(config)
      decode[Config]("true") must beLeft
    }

    "provide a JsonFormat for DateTime" in {
      val dateTime = new DateTime("2016-01-01")
      val dateTimeJsonString = """"2016-01-01T00:00:00.000Z""""

      dateTime.toJson.compactPrint mustEqual dateTimeJsonString
      dateTimeJsonString.parseJson.convertTo[DateTime] mustEqual dateTime
      "true".parseJson.convertTo[DateTime] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for DateTime" in {
      val dateTime = new DateTime("2016-01-01")
      val dateTimeJsonString = json""""2016-01-01T00:00:00.000Z""""

      dateTime.asJson mustEqual dateTimeJsonString
      dateTimeJsonString.as[DateTime] must beRight(dateTime)
      decode[DateTime]("true") must beLeft
    }

    "provide a JsonFormat for LocalDate" in {
      val localDate = new LocalDate("2016-01-01")
      val localDateJsonString = """"2016-01-01""""

      localDate.toJson.compactPrint mustEqual localDateJsonString
      localDateJsonString.parseJson.convertTo[LocalDate] mustEqual localDate
      "true".parseJson.convertTo[LocalDate] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for LocalDate" in {
      val localDate = new LocalDate("2016-01-01")
      val localDateJsonString = """"2016-01-01""""

      localDate.asJson.pretty(Printer.noSpaces) mustEqual localDateJsonString
      decode[LocalDate](localDateJsonString) must beRight(localDate)
      decode[LocalDate]("true") must beLeft
    }

    "provide an Encoder and Decoder for Currency" in {
      implicit val moneyContext: MoneyContext = MoneyContext(EUR, defaultCurrencySet, Nil)
      val currency: Currency = USD
      val currencyString = """"USD""""

      currency.asJson.noSpaces mustEqual currencyString
      decode[Currency](currencyString) must beRight(currency)
      decode[Currency]("EU") must beLeft
    }

    "provide a JsonFormat for a Map as a JsArray of json objects" in {
      implicit val mapFormat: RootJsonFormat[Map[Option[Int], String]] = mapJsArrayFormat[Option[Int], String]

      val map = Map(None -> "none", Some(1) -> "one", Some(2) -> "two")
      val mapJsonString = """[{"key":null,"value":"none"},{"key":1,"value":"one"},{"key":2,"value":"two"}]"""

      map.toJson.compactPrint mustEqual mapJsonString
      mapJsonString.parseJson.convertTo[Map[Option[Int], String]] mustEqual map
      """{"key":1,"value":"one"}""".parseJson.convertTo[Map[Option[Int], String]] must throwA[DeserializationException]
      """[{"invalid":1,"value":"one"}]""".parseJson.convertTo[Map[Option[Int], String]] must throwA[DeserializationException]
      """[{"key":1,"invalid":"one"}]""".parseJson.convertTo[Map[Option[Int], String]] must throwA[DeserializationException]
    }

    "provide an Encoder and Decoder for a Map as an array of json objects" in {
      implicit val mapEncoder: Encoder[Map[Option[Int], String]] = mapJsonArrayEncoder[Option[Int], String]
      implicit val mapDecoder: Decoder[Map[Option[Int], String]] = mapJsonArrayDecoder[Option[Int], String]

      val map = Map(None -> "none", Some(1) -> "one", Some(2) -> "two")
      val mapJson = json"""[{"key":null,"value":"none"},{"key":1,"value":"one"},{"key":2,"value":"two"}]"""

      map.asJson mustEqual mapJson
      mapJson.as[Map[Option[Int], String]] must beRight(map)
      json"""{"key":1,"value":"one"}""".as[Map[Option[Int], String]] must beLeft
      json"""[{"invalid":1,"value":"one"}]""".as[Map[Option[Int], String]] must beLeft
      json"""[{"key":1,"invalid":"one"}]""".as[Map[Option[Int], String]] must beLeft
    }
  }
}
