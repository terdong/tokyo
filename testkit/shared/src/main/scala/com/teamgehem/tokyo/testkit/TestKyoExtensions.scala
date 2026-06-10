package com.teamgehem.tokyo.testkit

import kyo.*

/**
 * Test utilities to easily verify Kyo programs in test suites.
 *
 * WARNING: The synchronous execution methods in this object (e.g. `runSync`)
 * are intended strictly for unit and integration testing.
 * Do not use them in production code under any circumstances.
 */
object TestKyoExtensions:
  extension [E, A](program: A < (Abort[E] & Memo & Sync))
    /**
     * Runs a Kyo program synchronously, returning an Either containing the success value or error/panic.
     */
    def runSync(using ConcreteTag[E]): Either[E | Throwable, A] =
      import kyo.AllowUnsafe.embrace.danger
      try
        Sync.Unsafe.evalOrThrow(Abort.run(Memo.run(program))) match
          case Result.Success(v) => Right(v)
          case Result.Failure(e) => Left(e)
          case Result.Panic(ex)  => Left(ex)
      catch
        case ex: Throwable => Left(ex)

  extension [A](program: A < (Memo & Sync))
    /**
     * Runs a Kyo program containing no Abort effects synchronously, returning the pure success value.
     * Throws any uncaught Throwable/Panic.
     */
    def runSyncPure: A =
      import kyo.AllowUnsafe.embrace.danger
      Sync.Unsafe.evalOrThrow(Memo.run(program))

  extension [E, A](program: A < (Abort[E] & Memo & Async))
    /**
     * Runs a Kyo program asynchronously, returning a Future containing an Either with the success value or error/panic.
     */
    def runAsync(using ConcreteTag[E]): scala.concurrent.Future[Either[E | Throwable, A]] =
      import kyo.AllowUnsafe.embrace.danger
      val programWithAbort = Abort.run(Memo.run(program))
      val fiberSync = Fiber.initUnscoped(programWithAbort)
      val fiber = Sync.Unsafe.evalOrThrow(fiberSync)
      val futureSync = fiber.toFuture
      val future = Sync.Unsafe.evalOrThrow(futureSync)
      import scala.concurrent.ExecutionContext.Implicits.global
      future.map {
        case Result.Success(v) => Right(v)
        case Result.Failure(e) => Left(e)
        case Result.Panic(ex)  => Left(ex)
      }.recover {
        case ex: Throwable => Left(ex)
      }

  extension [A](program: A < (Memo & Async))
    /**
     * Runs a Kyo program containing no Abort effects asynchronously, returning a Future containing the success value.
     * The Future will fail with any uncaught Throwable/Panic.
     */
    def runAsyncPure: scala.concurrent.Future[A] =
      import kyo.AllowUnsafe.embrace.danger
      val fiberSync = Fiber.initUnscoped(Memo.run(program))
      val fiber = Sync.Unsafe.evalOrThrow(fiberSync)
      val futureSync = fiber.toFuture
      Sync.Unsafe.evalOrThrow(futureSync)
