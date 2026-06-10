package com.teamgehem.tokyo.testkit

import com.teamgehem.tokyo.testkit.TestKyoExtensions.*
import kyo.*
import munit.FunSuite

class TestKyoExtensionsSpec extends FunSuite {

  test("runSync - Success") {
    val effect: Int < Abort[String] = 42
    assertEquals(effect.runSync, Right(42))
  }

  test("runSync - Failure") {
    val effect: Int < Abort[String] = Abort.fail("error")
    assertEquals(effect.runSync, Left("error"))
  }

  test("runSync - Panic") {
    val exception = new RuntimeException("panic")
    val effect: Int < Abort[String] = Abort.panic(exception)
    assertEquals(effect.runSync, Left(exception))
  }

  test("runSyncPure - Success") {
    val effect: Int < Any = 42
    assertEquals(effect.runSyncPure, 42)
  }

  test("runAsync - Success") {
    val effect: Int < (Abort[String] & Async) = 42
    effect.runAsync.map { result =>
      assertEquals(result, Right(42))
    }(using scala.concurrent.ExecutionContext.global)
  }

  test("runAsync - Failure") {
    val effect: Int < (Abort[String] & Async) = Abort.fail("error")
    effect.runAsync.map { result =>
      assertEquals(result, Left("error"))
    }(using scala.concurrent.ExecutionContext.global)
  }

  test("runAsync - Panic") {
    val exception = new RuntimeException("panic")
    val effect: Int < (Abort[String] & Async) = Abort.panic(exception)
    effect.runAsync.map { result =>
      assertEquals(result, Left(exception))
    }(using scala.concurrent.ExecutionContext.global)
  }

  test("runAsyncPure - Success") {
    val effect: Int < Async = 42
    effect.runAsyncPure.map { result =>
      assertEquals(result, 42)
    }(using scala.concurrent.ExecutionContext.global)
  }
}
