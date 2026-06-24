package com.teamgehem.tokyo

import kyo.*
import kyo.AllowUnsafe.embrace.danger
import munit.FunSuite

import scala.util.Failure
import scala.util.Success
import scala.util.Try

class TokyoSpec extends FunSuite {

  test("mapAbort - Success path") {
    val effect: Int < Abort[String] = 42
    val mapped: Int < Abort[Int] = effect.mapAbort(_.length)
    val result: Result[Int, Int] = Abort.run(mapped).eval
    assertEquals(result, Result.Success(42))
  }

  test("mapAbort - Failure path") {
    val effect: Int < Abort[String] = Abort.fail("error")
    val mapped: Int < Abort[Int] = effect.mapAbort(_.length)
    val result: Result[Int, Int] = Abort.run(mapped).eval
    assertEquals(result, Result.Failure(5))
  }

  test("mapAbort - Panic path") {
    val exception = new RuntimeException("panic")
    val effect: Int < Abort[String] = Abort.panic(exception)
    val mapped: Int < Abort[Int] = effect.mapAbort(_.length)
    val result: Result[Int, Int] = Abort.run(mapped).eval
    assertEquals(result, Result.Panic(exception))
  }

  test("tapError - Success path") {
    var sideEffectRun = false
    val effect: Int < Abort[String] = 42
    val tapped = effect.tapError(err => Sync.defer { sideEffectRun = true })

    val result: Result[String, Int] = Sync.Unsafe.evalOrThrow(Abort.run(tapped))
    assertEquals(result, Result.Success(42))
    assertEquals(sideEffectRun, false)
  }

  test("tapError - Failure path") {
    var sideEffectValue: Option[String] = None
    val effect: Int < Abort[String] = Abort.fail("error")
    val tapped =
      effect.tapError(err => Sync.defer { sideEffectValue = Some(err) })

    val result: Result[String, Int] = Sync.Unsafe.evalOrThrow(Abort.run(tapped))
    assertEquals(result, Result.Failure("error"))
    assertEquals(sideEffectValue, Some("error"))
  }

  test("recover - Success path") {
    val effect: Int < Abort[String] = 42
    val recovered: Int < Any = effect.recover(_.length)
    val result = recovered.eval
    assertEquals(result, 42)
  }

  test("recover - Failure path") {
    val effect: Int < Abort[String] = Abort.fail("error")
    val recovered: Int < Any = effect.recover(_.length)
    val result = recovered.eval
    assertEquals(result, 5)
  }

  test("recover - Panic path") {
    try {
      val exception = new RuntimeException("panic")
      val effect: Int < Abort[String] = Abort.panic(exception)
      val recovered: Int < Any = effect.recover(_.length)
      recovered.eval
      fail("Expected RuntimeException to be thrown")
    } catch {
      case ex: RuntimeException =>
        assertEquals(ex.getMessage, "panic")
    }
  }

  test("Option.toAbort - Some") {
    val opt: Option[Int] = Some(42)
    val effect: Int < Abort[String] = opt.toAbort("error")
    val result = Abort.run(effect).eval
    assertEquals(result, Result.Success(42))
  }

  test("Option.toAbort - None") {
    val opt: Option[Int] = None
    val effect: Int < Abort[String] = opt.toAbort("error")
    val result = Abort.run(effect).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("Either.toAbort - Right") {
    val either: Either[String, Int] = Right(42)
    val effect: Int < Abort[String] = either.toAbort
    val result = Abort.run(effect).eval
    assertEquals(result, Result.Success(42))
  }

  test("Either.toAbort - Left") {
    val either: Either[String, Int] = Left("error")
    val effect: Int < Abort[String] = either.toAbort
    val result = Abort.run(effect).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("Try.toAbort - Success") {
    val t: Try[Int] = Success(42)
    val effect: Int < Abort[Throwable] = t.toAbort
    val result = Abort.run(effect).eval
    assertEquals(result, Result.Success(42))
  }

  test("Try.toAbort - Failure") {
    val exception = new RuntimeException("error")
    val t: Try[Int] = Failure(exception)
    val effect: Int < Abort[Throwable] = t.toAbort
    val result = Abort.run(effect).eval
    assertEquals(result, Result.Failure(exception))
  }

  test("Env.provide") {
    val effect: String < Env[String] = Env.get[String]
    val provided: String < Any = effect.provide("dependency")
    val result = provided.eval
    assertEquals(result, "dependency")
  }

  test("Env.provideLayer") {
    case class Config(value: String)
    val layer: Layer[Config, Any] = Layer(Config("layer-dependency"))
    val effect: Config < Env[Config] = Env.get[Config]
    val provided = Memo.run(effect.provideLayer(layer))
    val result = provided.eval
    assertEquals(result, Config("layer-dependency"))
  }

  test("Result.toEither - Success") {
    val res: Result[String, Int] = Result.Success(42)
    assertEquals(res.toEither, Right(42))
  }

  test("Result.toEither - Failure") {
    val res: Result[String, Int] = Result.Failure("error")
    assertEquals(res.toEither, Left("error"))
  }

  test("Result.toEither - Panic") {
    val exception = new RuntimeException("panic")
    val res: Result[String, Int] = Result.Panic(exception)
    intercept[RuntimeException] {
      res.toEither
    }
  }

  test("recoverWith - Success path") {
    val effect: Int < Abort[String] = 42
    val recovered = effect.recoverWith(err => (err.length: Int < Any))
    val result = recovered.eval
    assertEquals(result, 42)
  }

  test("recoverWith - Failure path") {
    val effect: Int < Abort[String] = Abort.fail("error")
    val recovered = effect.recoverWith(err => (err.length: Int < Any))
    val result = recovered.eval
    assertEquals(result, 5)
  }

  test("orElse - Success path") {
    val effect: Int < Abort[String] = 42
    val fallback: Int < Abort[String] = 100
    val result = Abort.run(effect.orElse(fallback)).eval
    assertEquals(result, Result.Success(42))
  }

  test("orElse - Failure path") {
    val effect: Int < Abort[String] = Abort.fail("error")
    val fallback: Int < Abort[String] = 100
    val result = Abort.run(effect.orElse(fallback)).eval
    assertEquals(result, Result.Success(100))
  }

  test("catchPanic - Success path") {
    val effect: Int < Abort[String] = 42
    val caught = effect.catchPanic(ex => ex.getMessage)
    val result = Abort.run(caught).eval
    assertEquals(result, Result.Success(42))
  }

  test("catchPanic - Failure path") {
    val effect: Int < Abort[String] = Abort.fail("error")
    val caught = effect.catchPanic(ex => ex.getMessage)
    val result = Abort.run(caught).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("catchPanic - Panic path") {
    val exception = new RuntimeException("panic")
    val effect: Int < Abort[String] = Abort.panic(exception)
    val caught = effect.catchPanic(ex => ex.getMessage)
    val result = Abort.run(caught).eval
    assertEquals(result, Result.Failure("panic"))
  }

  test("tap - Success path") {
    var sideEffectRun = false
    val effect: Int < Abort[String] = 42
    val tapped = effect.tap(v => Sync.defer { sideEffectRun = true })
    val result = Sync.Unsafe.evalOrThrow(Abort.run(tapped))
    assertEquals(result, Result.Success(42))
    assertEquals(sideEffectRun, true)
  }

  test("tap - Failure path") {
    var sideEffectRun = false
    val effect: Int < Abort[String] = Abort.fail("error")
    val tapped = effect.tap(v => Sync.defer { sideEffectRun = true })
    val result = Sync.Unsafe.evalOrThrow(Abort.run(tapped))
    assertEquals(result, Result.Failure("error"))
    assertEquals(sideEffectRun, false)
  }

  test("ensuring - Success path") {
    var sideEffectRun = false
    val effect: Int < Abort[String] = 42
    val cleaned = effect.ensuring(Sync.defer { sideEffectRun = true })
    val result = Sync.Unsafe.evalOrThrow(Abort.run(cleaned))
    assertEquals(result, Result.Success(42))
    assertEquals(sideEffectRun, true)
  }

  test("ensuring - Failure path") {
    var sideEffectRun = false
    val effect: Int < Abort[String] = Abort.fail("error")
    val cleaned = effect.ensuring(Sync.defer { sideEffectRun = true })
    val result = Sync.Unsafe.evalOrThrow(Abort.run(cleaned))
    assertEquals(result, Result.Failure("error"))
    assertEquals(sideEffectRun, true)
  }

  test("ensuring - Panic path") {
    var sideEffectRun = false
    val exception = new RuntimeException("panic")
    val effect: Int < Abort[String] = Abort.panic(exception)
    val cleaned = effect.ensuring(Sync.defer { sideEffectRun = true })
    try {
      Sync.Unsafe.evalOrThrow(Abort.run(cleaned))
      fail("Expected RuntimeException")
    } catch {
      case ex: RuntimeException =>
        assertEquals(ex.getMessage, "panic")
        assertEquals(sideEffectRun, true)
    }
  }

  test("effectful Option.toAbort - Some") {
    val optEffect: Option[Int] < Any = Some(42)
    val result = Abort.run(optEffect.toAbort("error")).eval
    assertEquals(result, Result.Success(42))
  }

  test("effectful Option.toAbort - None") {
    val optEffect: Option[Int] < Any = None
    val result = Abort.run(optEffect.toAbort("error")).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("effectful Either.toAbort - Right") {
    val eitherEffect: Either[String, Int] < Any = Right(42)
    val result = Abort.run(eitherEffect.toAbort).eval
    assertEquals(result, Result.Success(42))
  }

  test("effectful Either.toAbort - Left") {
    val eitherEffect: Either[String, Int] < Any = Left("error")
    val result = Abort.run(eitherEffect.toAbort).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("effectful Try.toAbort - Success") {
    val tryEffect: Try[Int] < Any = Success(42)
    val result = Abort.run(tryEffect.toAbort).eval
    assertEquals(result, Result.Success(42))
  }

  test("effectful Try.toAbort - Failure") {
    val exception = new RuntimeException("error")
    val tryEffect: Try[Int] < Any = Failure(exception)
    val result = Abort.run(tryEffect.toAbort).eval
    assertEquals(result, Result.Failure(exception))
  }

  test("Maybe.toAbort - Present") {
    val m: Maybe[Int] = Present(42)
    val result = Abort.run(m.toAbort("error")).eval
    assertEquals(result, Result.Success(42))
  }

  test("Maybe.toAbort - Absent") {
    val m: Maybe[Int] = Absent
    val result = Abort.run(m.toAbort("error")).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("Result.toAbort - Success") {
    val r: Result[String, Int] = Result.Success(42)
    val result = Abort.run(r.toAbort).eval
    assertEquals(result, Result.Success(42))
  }

  test("Result.toAbort - Failure") {
    val r: Result[String, Int] = Result.Failure("error")
    val result = Abort.run(r.toAbort).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("effectful Maybe.toAbort - Present") {
    val m: Maybe[Int] < Any = Present(42)
    val result = Abort.run(m.toAbort("error")).eval
    assertEquals(result, Result.Success(42))
  }

  test("effectful Maybe.toAbort - Absent") {
    val m: Maybe[Int] < Any = Absent
    val result = Abort.run(m.toAbort("error")).eval
    assertEquals(result, Result.Failure("error"))
  }

  test("effectful Result.toAbort - Success") {
    val r: Result[String, Int] < Any = Result.Success(42)
    val result = Abort.run(r.toAbort).eval
    assertEquals(result, Result.Success(42))
  }

  test("effectful Result.toAbort - Failure") {
    val r: Result[String, Int] < Any = Result.Failure("error")
    val result = Abort.run(r.toAbort).eval
    assertEquals(result, Result.Failure("error"))
  }
}
