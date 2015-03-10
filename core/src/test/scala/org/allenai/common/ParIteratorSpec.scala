package org.allenai.common

import java.util.concurrent.atomic.AtomicInteger

import org.allenai.common.testkit.UnitSpec
import org.allenai.common.ParIterator.ParIteratorEnrichment

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

class ParIteratorSpec extends UnitSpec {
  "ParForeachIterator" should "do things concurrently" in {
    val successes = synchronized(collection.mutable.Set[Int]())

    val scale = 200

    val iter = Iterator(3, 1, 2)
    val time = Timing.time {
      iter.parForeach { i =>
        Thread.sleep(i * scale)
        successes.add(i)
      }
    }

    assert(successes === Set(1, 2, 3))
    assert(time < (((3 * scale) + (scale / 2)) millis))
  }

  it should "do a great many things concurrently" in {
    val successes = synchronized(collection.mutable.Set[Int]())

    val max = 2000
    val iter = Range(0, max).toIterator
    iter.parForeach { i =>
      Thread.sleep((max - i) % 10)
      successes += i
    }
    val expected = Range(0, max).toSet

    assert((successes -- expected) === Set.empty)
    assert((expected -- successes) === Set.empty)
  }

  it should "nest properly" in {
    val count = new AtomicInteger()
    val max = 13
    Range(0, max).toIterator.parForeach { _ =>
      Range(0, max).toIterator.parForeach { _ =>
        val successes = synchronized(collection.mutable.Set[Int]())

        val iter = Range(0, max).toIterator
        iter.parForeach { i =>
          Thread.sleep((i * max * max) % 10)
          successes += i
          count.incrementAndGet()
        }
        val expected = Range(0, max).toSet

        assert((successes -- expected) === Set.empty)
        assert((expected -- successes) === Set.empty)
      }
    }

    assert(count.get() === max * max * max)
  }

  it should "map things concurrently" in {
    val max = 5
    val values = Range(0, max).reverse
    val iter = values.toIterator
    val expected = values.map { i => s"$i" }
    val time = Timing.time {
      val result = iter.parMap { i =>
        Thread.sleep(i * 100)
        s"$i"
      }
      assert(expected === result.toSeq)
    }

    assert(time < ((max * 100) millis) + (50 millis))
  }

  it should "map lots of things concurrently" in {
    val max = 50000
    val values = Range(0, max).reverse
    val iter = values.toIterator
    val expected = values.map { i => s"$i" }
    val result = iter.parMap { i => s"$i" }
    assert(expected === result.toSeq)
  }

  it should "return exceptions from foreach functions" in {
    val successes = synchronized(collection.mutable.Set[Int]())
    intercept[ArithmeticException] {
      Range(-20, 20).toIterator.parForeach { i => successes.add(10000 / i) }
    }
  }

  it should "return the first exception from foreach functions" in {
    intercept[NotImplementedError] {
      Iterator(new NotImplementedError(), new ArithmeticException()).zipWithIndex.foreach {
        case (e, index) =>
          Thread.sleep((1 - index) * 1000)
          throw e
      }
    }
  }

  it should "return exceptions from map" in {
    intercept[ArithmeticException] {
      Range(-20, 20).toIterator.map(10000 / _).toList
    }
  }
}
