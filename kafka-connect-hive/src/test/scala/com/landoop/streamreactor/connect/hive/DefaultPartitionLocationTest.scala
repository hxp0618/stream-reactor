package com.landoop.streamreactor.connect.hive

import cats.data.NonEmptyList
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DefaultPartitionLocationTest extends AnyFunSuite with Matchers {
  test("show should generate path using the standard metastore pattern") {
    val p1 = (PartitionKey("country"), "usa")
    val p2 = (PartitionKey("city"), "philly")
    DefaultPartitionLocation.show(Partition(NonEmptyList.of(p1, p2), None)) shouldBe "country=usa/city=philly"
  }
}
