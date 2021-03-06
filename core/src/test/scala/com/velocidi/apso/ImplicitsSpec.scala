package com.velocidi.apso

import scala.concurrent.Future
import scala.util.{ Random, Try }

import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.specs2.ScalaCheck
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._

import com.velocidi.apso.Implicits._

@deprecated("Some of classes tested here will be removed later", "2017/07/13")
class ImplicitsSpec(implicit ee: ExecutionEnv) extends Specification with ScalaCheck with FutureExtraMatchers {

  "An ApsoAny" should {

    "wrap itself as a Some" in {
      1.some === Some(1)
      "A".some === Some("A")
      List(1, 2, 3).some === Some(List(1, 2, 3))
    }

  }

  "An ApsoString" should {

    "enumerate all strings of a given length using the inner string alphabet" in {
      "".enumerate(-1) must throwAn[IllegalArgumentException]
      "".enumerate(0) must beEmpty
      "".enumerate(5) must beEmpty
      "aba".enumerate(0) must beEmpty
      "aba".enumerate(1).toSet === Set("a", "b")
      "aba".enumerate(3).toSet === Set("aaa", "aab", "aba", "abb", "baa", "bab", "bba", "bbb")
    }

    "pad a string with a certain character to the left" in {
      "".padLeft(-1, ' ') must throwAn[IllegalArgumentException]
      "".padLeft(0, ' ') === ""
      "".padLeft(3, ' ') === "   "
      "".padLeft(3, 'x') === "xxx"
      "abc".padLeft(0, ' ') === "abc"
      "abc".padLeft(1, ' ') === "abc"
      "abc".padLeft(6, ' ') === "   abc"
      "abc".padLeft(6, 'x') === "xxxabc"
    }

    "return itself as a null-terminated byte array" in {
      val emptyString = "".getBytesWithNullTerminator
      emptyString.length === 1
      emptyString(0) === 0.toByte
      val normalString = "abc".getBytesWithNullTerminator
      normalString.length === 4
      normalString(0) === 'a'.toByte
      normalString(1) === 'b'.toByte
      normalString(2) === 'c'.toByte
      normalString(3) === 0.toByte
    }

  }

  "An ApsoSeq" should {

    "split itself into subsequences" in {
      List.empty.split(3) === Vector(Nil, Nil, Nil)
      List(1, 2, 3, 4).split(-1) must throwAn[IllegalArgumentException]
      List(1, 2, 3, 4).split(0) must beEmpty
      List(1, 2, 3, 4).split(1) === Vector(List(1, 2, 3, 4))
      List(1, 2, 3, 4).split(2) === Vector(List(1, 2), List(3, 4))
      List(1, 2, 3, 4).split(3) === Vector(List(1, 2), List(3), List(4))
      List(1, 2, 3, 4).split(4) === Vector(List(1), List(2), List(3), List(4))
      List(1, 2, 3, 4).split(5) === Vector(List(1), List(2), List(3), List(4), Nil)
    }

    "sample a percentage of itself" ! prop {
      (list: List[Int], percentage: Double) =>
        if (percentage < 0.0 || percentage > 1.0) {
          Try(list.sample(percentage)).isFailure
        } else {
          val sample = list.sample(percentage)
          sample.size == list.size * percentage
          sample.toSet.subsetOf(list.toSet)
        }
    }.setGen2(frequency(4 -> choose(0.0, 1.0), 1 -> arbDouble.arbitrary))

    "merge two sorted collections" in {
      List(1, 5, 6).mergeSorted[Int, List[Int]](List.empty[Int]) === List(1, 5, 6)
      List.empty[Int].mergeSorted(List(2, 4)) === List(2, 4)
      Seq(2).mergeSorted(Seq(5)) === List(2, 5)
      Seq(5).mergeSorted(Seq(2)) === List(2, 5)

      List(1, 3, 5).mergeSorted(Stream(2, 4)) === List(1, 2, 3, 4, 5)
      List(1, 3, 5).mergeSorted(Stream(2, 4)) must beAnInstanceOf[List[_]]

      Stream(1, 3, 5).mergeSorted(List(2, 4)) === Stream(1, 2, 3, 4, 5)
      Stream(1, 3, 5).mergeSorted(List(2, 4)) must beAnInstanceOf[Stream[_]]

      trait Base { val x: Int }
      case class Impl1(x: Int) extends Base
      case class Impl2(x: Int) extends Base

      implicit def ord[T <: Base] = new Ordering[T] {
        override def compare(a: T, b: T): Int = a.x - b.x
      }

      Seq(Impl1(3)).mergeSorted(Seq(Impl2(5))) === Seq(Impl1(3), Impl2(5))
      Seq(Impl1(3)).mergeSorted(Seq(Impl2(5))) must beAnInstanceOf[Seq[Base]]
    }

    "support taking the n smallest/largest values" ! prop {
      (s: List[Int], t: Int) =>
        s.takeSmallest(t).sorted == s.sorted.take(t) &&
          s.takeLargest(t).sorted.reverse == s.sorted.reverse.take(t)
    }
  }

  "An ApsoTraversableOnce" should {

    "calculate correctly the average of a list of integral values" in {
      List[Byte](1, 4, 3, 2, 5).average === 3
      List[Short](2, 8, 6, 4, 10).average === 6
      List[Int](3, 12, 9, 6, 15).average === 9
      List[Long](4, 16, 12, 8, 20).average === 12
      List[Byte](1, 2).average === 1
      List[Int](1, 2).average === 1
      List[Short](1, 2).average === 1
      List[Long](1, 2).average === 1
    }

    "calculate correctly the average of a list of fractional values" in {
      List[Float](1, 4, 3, 2, 5).average === 3.0
      List[Double](2, 8, 6, 4, 10).average === 6.0
      List[Float](1, 2).average === 1.5
      List[Double](1, 2).average === 1.5
    }

    "throw an exception when the average is called on an empty traversable" in {
      List[Int]().average must throwA[IllegalArgumentException]
    }
  }

  "An ApsoBufferedIterator" should {

    "merge sorted iterators" in {
      List(1, 5, 6).iterator.buffered.mergeSorted(List.empty[Int].iterator.buffered).toList === List(1, 5, 6)
      List.empty[Int].iterator.buffered.mergeSorted(List(2, 4).iterator.buffered).toList === List(2, 4)
      Seq(2).iterator.buffered.mergeSorted(Seq(5).iterator.buffered).toList === List(2, 5)
      Seq(5).iterator.buffered.mergeSorted(Seq(2).iterator.buffered).toList === List(2, 5)

      List(1, 3, 5).iterator.buffered.mergeSorted(Stream(2, 4).iterator.buffered).toList === List(1, 2, 3, 4, 5)

      Stream(1, 3, 5).iterator.buffered.mergeSorted(List(2, 4).iterator.buffered).toStream === Stream(1, 2, 3, 4, 5)

      trait Base { val x: Int }
      case class Impl1(x: Int) extends Base
      case class Impl2(x: Int) extends Base

      implicit def ord[T <: Base] = new Ordering[T] {
        override def compare(a: T, b: T): Int = a.x - b.x
      }

      Seq(Impl1(3)).iterator.buffered.mergeSorted(Seq(Impl2(5)).iterator.buffered).toSeq === Seq(Impl1(3), Impl2(5))
      Seq(Impl1(3)).iterator.buffered.mergeSorted(Seq(Impl2(5)).iterator.buffered).toSeq must beAnInstanceOf[Seq[Base]]

      List(1, 4, 7).iterator.buffered.mergeSorted(List(2, 5, 8).iterator.buffered).mergeSorted(List(3, 6, 9).iterator.buffered).toList ===
        List(1, 2, 3, 4, 5, 6, 7, 8, 9)
    }

    "have a bufferedTakeWhile operation" in {
      val iter = List(5, 6, 7, 8, 9, 10).iterator.buffered
      iter.bufferedTakeWhile(_ < 4).toList === List()
      iter.toList == List(5, 6, 7, 8, 9, 10)

      val iter2 = List(5, 6, 7, 8, 9, 10).iterator.buffered
      iter2.bufferedTakeWhile(_ < 7).toList === List(5, 6)
      iter2.bufferedTakeWhile(_ < 10).toList === List(7, 8, 9)
      iter2.toList === List(10)
    }
  }

  "An ApsoMap" should {

    "support the merge method" in {
      val m1 = Map(1 -> 1, 2 -> 2, 3 -> 3)
      val m2 = Map(3 -> 3, 4 -> 4, 5 -> 5)

      m1.merge(m2)(_ + _) ===
        Map(1 -> 1, 2 -> 2, 3 -> 6)
    }

    "support the twoWayMerge method" in {
      val m1 = Map(1 -> 1, 2 -> 2, 3 -> 3)
      val m2 = Map(3 -> 3, 4 -> 4, 5 -> 5)

      m1.twoWayMerge(m2)(_ + _) ===
        Map(1 -> 1, 2 -> 2, 3 -> 6, 4 -> 4, 5 -> 5)
    }

    "support the mapKeys method" in {
      val m = Map(1 -> 2, 3 -> 4, 5 -> 6, 7 -> 8)
      m.mapKeys(_ * 2) ===
        Map(2 -> 2, 6 -> 4, 10 -> 6, 14 -> 8)
    }
  }

  "An ApsoListMap" should {

    "convert correctly a list of maps into a map of lists" in {
      List[Map[Int, Int]]().sequenceOnMap() === Map[Int, List[Int]]()

      List(Map(1 -> 2), Map(1 -> 3), Map(2 -> 3)).sequenceOnMap() ===
        Map(1 -> List(2, 3), 2 -> List(3))

      List(Map(1 -> "c", 2 -> "b", 3 -> "a")).sequenceOnMap() ===
        Map(1 -> List("c"), 2 -> List("b"), 3 -> List("a"))

      List(Map(1 -> "c", 2 -> "b", 3 -> "a"), Map(1 -> "c2", 4 -> "aa")).sequenceOnMap() ===
        Map(1 -> List("c", "c2"), 2 -> List("b"), 3 -> List("a"), 4 -> List("aa"))
    }

    "convert correctly a list of maps into a map of lists with a zero value" in {
      List[Map[Int, Int]]().sequenceOnMap(Some(0)) === Map[Int, List[Int]]()

      List(Map(1 -> 2), Map(1 -> 3), Map(2 -> 3)).sequenceOnMap(Some(0)) ===
        Map(1 -> List(2, 3, 0), 2 -> List(0, 0, 3))

      List(Map(1 -> "c", 2 -> "b", 3 -> "a")).sequenceOnMap(Some("")) ===
        Map(1 -> List("c"), 2 -> List("b"), 3 -> List("a"))

      List(Map(1 -> "c", 2 -> "b", 3 -> "a"), Map(1 -> "c2", 4 -> "aa")).sequenceOnMap(Some("")) ===
        Map(1 -> List("c", "c2"), 2 -> List("b", ""), 3 -> List("a", ""), 4 -> List("", "aa"))
    }
  }

  "An ApsoOptionalFuture" should {

    "fallback on None" in {
      val f = Future.successful(None).ifNoneOrErrorFallbackTo(Future.successful(Some(())))
      f must beSome.await.eventually
    }

    "fallback on Exception" in {
      val f = Future.failed(new Exception).ifNoneOrErrorFallbackTo(Future.successful(Some(())))
      f must beSome.await.eventually
    }

    "don't fallback on Some" in {
      val f = Future.successful(Some(1)).ifNoneOrErrorFallbackTo(Future.successful(Some(2)))
      f must beSome(1).await.eventually
    }

  }

  "An ApsoRandom" should {

    class MockRandom(elems: Double*) extends Random {
      private[this] var nextElems = elems.toList
      override def nextInt() = { val e = nextElems.head.toInt; nextElems = nextElems.tail; e }
      override def nextInt(n: Int) = { val e = nextElems.head.toInt % n; nextElems = nextElems.tail; e }
      override def nextDouble() = { val e = nextElems.head; nextElems = nextElems.tail; e }
    }

    def centralMoment(n: Int, xs: Iterable[Double]) = {
      val avg = xs.sum / xs.size
      val ys = xs map { x: Double => math.pow(x - avg, n.toDouble) }
      ys.sum / ys.size
    }

    "select correctly an element from an indexed sequence" in {
      val rand = new MockRandom(1, 4, 3, 2)
      val seq = Vector("a", "b", "c", "d", "e")

      rand.choose(seq) === Some("b")
      rand.choose(seq) === Some("e")
      rand.choose(seq) === Some("d")
      rand.choose(Vector()) === None
    }

    "select correctly multiple elements from a sequence" in {
      val rand = Random
      rand.setSeed(0)
      val runs = 10000
      val n = 5
      val elems = Stream.iterate(0, 50) { _ + 1 }

      val tests = (1 to runs).map { _ => rand.chooseN(elems, n) }

      tests.map(_.size).toSet === Set(n)
      val elementCounts: Map[Int, Int] = tests.flatten.groupBy(identity).mapValues(_.size)

      elementCounts.keys.size must beCloseTo(elems.size +/- 5)

      // Expected probability of a number being picked
      val prob = (1 to n).foldLeft((elems.size, 0.0)) {
        case ((rem, acc), next) =>
          val newAcc = acc + (1.0 - acc) * (1.0 / (rem - 1))
          (rem - 1, newAcc)
      }._2

      elementCounts.values.sum.toDouble / (elementCounts.size * runs) must beCloseTo(prob +/- 0.05)
    }

    "select correctly an element from a sequence using weights" in {
      val rand = new MockRandom(0.2, 0.8, 0.7, 0.1)
      val map = Map("a" -> 0.5, "b" -> 0.25, "c" -> 0.25)

      rand.monteCarlo(map) === Some("a")
      rand.monteCarlo(map) === Some("c")
      rand.monteCarlo(map) === Some("b")
      rand.monteCarlo(Map.empty[String, Double]) === None
    }

    "select correctly an element from a sequence using weights" in {
      val rand = new MockRandom(0.2, 0.8, 0.7)
      val map = Map("a" -> 0.5, "b" -> 0.25, "c" -> 0.25)

      rand.monteCarlo(map) === Some("a")
      rand.monteCarlo(map) === Some("c")
      rand.monteCarlo(map) === Some("b")
    }

    "select correctly an element from a sequence in case of underweighting" in {
      val rand = new MockRandom(0.2, 0.8, 0.7)
      val map = Map("a" -> 0.5, "b" -> 0.25)

      rand.monteCarlo(map) === Some("a")
      rand.monteCarlo(map) === None
      rand.monteCarlo(map) === Some("b")
    }

    "select an element from a sequence with a custom weight range" in {
      val rand = new MockRandom(0.2, 0.8, 0.7)
      val map = Map("a" -> 0.5, "b" -> 0.5, "c" -> 0.5)

      rand.monteCarlo(map, rand.nextDouble() * 1.5) === Some("a")
      rand.monteCarlo(map, rand.nextDouble() * 1.5) === Some("c")
      rand.monteCarlo(map, rand.nextDouble() * 1.5) === Some("c")
    }

    "select a random element using reservoir sampling" in {
      val rand = new MockRandom(0.4, 0.2, 0.8, 0.3, 0.7, 0.3, 0.9, 0.45, 0.1, 0.35, 0.9, 0.9)
      val list = List("a", "b", "c")

      rand.reservoirSample(list) === Some("b") // 0.2 < 1/2 in (a, b), 0.8 > 1/3 in (b, c)
      rand.reservoirSample(list) === Some("c") // 0.7 > 1/2 in (a, b), 0.3 < 1/3 in (a, c)
      rand.reservoirSample(list) === Some("c") // 0.45 < 1/2 in (a, b), 0.1 < 1/3 in (b, c)
      rand.reservoirSample(list) === Some("a") // 0.9 > 1/2 in (a, b), 0.9 > 1/3 in (a, c)
      rand.reservoirSample(Nil) === None
    }

    "provide a stream of samples with a given distribution" in {
      val rand = Random
      val map = Map("a" -> 0.2, "b" -> 0.3, "c" -> 0.5)
      val runs = 10000

      rand.samples(Map.empty[String, Double]) must beEmpty

      val sampleDistr = rand.samples(map).take(runs).
        foldLeft(Map.empty[String, Int]) { case (acc, k) => acc.updated(k, acc.getOrElse(k, 0) + 1) }

      forall(map) {
        case (k, prob) => sampleDistr(k).toDouble must beCloseTo(runs * prob, runs * prob * 0.1)
      }
    }

    "provide an ordered uniform distribution as a stream" in {
      val rand = Random
      rand.setSeed(0)
      val runs = 1000000
      val epsilon = 0.005
      val stream = rand.increasingUniformStream(runs).toList

      "the results must be ordered" in {
        stream.sorted === stream
      }

      "the average must be close to 0.5" in {
        stream.sum / runs must beCloseTo(0.5 +/- epsilon)
      }

      "the variance must be close to 1/12" in {
        centralMoment(2, stream) must beCloseTo(1.0 / 12 +/- epsilon)
      }

      "the skewness must be close to 0" in {
        centralMoment(3, stream) must beCloseTo(0.0 +/- epsilon)
      }
    }
  }
}
