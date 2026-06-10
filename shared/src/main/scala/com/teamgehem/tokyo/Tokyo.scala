package com.teamgehem.tokyo

import kyo.*
import scala.util.{Try, Success, Failure}

// --- A. Error Handling Extensions (Abort) ---

extension [R, E, S](effect: R < (Abort[E] & S))
  /**
   * Maps (transforms) the Abort error type into another error type.
   * 
   * Example:
   * {{{
   * val effect: Int < Abort[String] = Abort.fail("error")
   * val mapped: Int < Abort[Int] = effect.mapAbort(_.length)
   * }}}
   */
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  inline def mapAbort[E2](inline f: E => E2)(using Tag[E]): R < (Abort[E2] & S) =
    Abort.run[E](effect).map {
      case Result.Success(r) => r
      case Result.Failure(e) => Abort.fail(f(e))
      case Result.Panic(t)   => Abort.panic(t)
    }

  /**
   * Executes a side effect when an error occurs, while keeping the error unchanged.
   * 
   * Example:
   * {{{
   * val effect: Int < Abort[String] = Abort.fail("DB Error")
   * val tapped = effect.tapError(err => Console.printLine(s"Failed: $err"))
   * }}}
   */
  inline def tapError[S2](inline f: E => Unit < S2)(using Tag[E]): R < (Abort[E] & S & S2) =
    Abort.run[E](effect).map {
      case Result.Failure(e) => f(e).andThen(Abort.fail(e))
      case other             => Abort.get(other)
    }

  /**
   * Recovers from an error by returning a pure fallback value, discharging the Abort effect.
   * 
   * Example:
   * {{{
   * val effect: Int < Abort[String] = Abort.fail("error")
   * val recovered: Int < Any = effect.recover(_ => 0) // Returns 0 on error
   * }}}
   */
  inline def recover(inline f: E => R)(using Tag[E]): R < S =
    Abort.run[E](effect).map {
      case Result.Success(r) => r
      case Result.Failure(e) => f(e)
      case Result.Panic(t)   => throw t // Re-throw fatal panics
    }

  /**
   * Recovers from an error by executing another effect (e.g., fallback DB query or alternative action).
   * 
   * Example:
   * {{{
   * val effect: Int < Abort[String] = Abort.fail("Primary DB Down")
   * val recovered = effect.recoverWith(err => queryBackupDatabase())
   * }}}
   */
  inline def recoverWith[R2, S2](inline f: E => R2 < S2)(using Tag[E]): (R | R2) < (S & S2) =
    Abort.run[E](effect).map {
      case Result.Success(r) => r
      case Result.Failure(e) => f(e)
      case Result.Panic(t)   => throw t
    }

  /**
   * Fallback to an alternative effect if the current effect fails with an Abort.
   * 
   * Example:
   * {{{
   * val mainTask: Int < Abort[String] = Abort.fail("Failed")
   * val backupTask: Int < Abort[String] = 42
   * val result = mainTask.orElse(backupTask) // Returns 42
   * }}}
   */
  inline def orElse[R2, E2, S2](inline alternative: => R2 < (Abort[E2] & S2))(using Tag[E]): (R | R2) < (Abort[E2] & S & S2) =
    Abort.run[E](effect).map {
      case Result.Success(r) => r
      case Result.Failure(_) => alternative
      case Result.Panic(t)   => throw t
    }

  /**
   * Catches JVM runtime exceptions (Throwable / Panic) and safely translates them into controlled domain errors (Abort).
   * 
   * Example:
   * {{{
   * val unsafeTask: Int < Abort[String] = Abort.panic(new RuntimeException("Crash"))
   * val safeTask = unsafeTask.catchPanic(ex => ex.getMessage) // Translates to Abort.fail("Crash")
   * }}}
   */
  inline def catchPanic(inline f: Throwable => E)(using Tag[E]): R < (Abort[E] & S) =
    Abort.run[E](effect).map {
      case Result.Success(r) => r
      case Result.Failure(e) => Abort.fail(e)
      case Result.Panic(t)   => Abort.fail(f(t))
    }

  /**
   * Executes a side effect with the successful result value. The successful result value is kept unchanged.
   * 
   * Example:
   * {{{
   * val task: Int < Abort[String] = 42
   * val tapped = task.tap(v => Console.printLine(s"Finished with: $v"))
   * }}}
   */
  inline def tap[S2](inline f: R => Unit < S2): R < (Abort[E] & S & S2) =
    effect.map(r => f(r).andThen(r))

  /**
   * Guarantees that a finalizer (cleanup action) is executed whether the effect succeeds, fails (Abort), or panics (Panic). (Like a finally block)
   * 
   * Example:
   * {{{
   * useConnection(conn).ensuring(conn.close())
   * }}}
   */
  inline def ensuring[S2](inline finalizer: => Unit < S2)(using Tag[E]): R < (Abort[E] & S & S2) =
    Abort.run[E](effect).map {
      case Result.Success(r) => finalizer.andThen(r)
      case Result.Failure(e) => finalizer.andThen(Abort.fail(e))
      case Result.Panic(t)   => finalizer.andThen(throw t)
    }


// --- B. Standard Type Bridging (Lifting) ---

extension [A](opt: Option[A])
  /**
   * Converts a pure Option to an Abort effect that fails with the specified error when the Option is None.
   */
  inline def toAbort[E](inline err: => E): A < Abort[E] =
    opt match
      case Some(v) => v
      case None    => Abort.fail(err)

extension [E, A](either: Either[E, A])
  /**
   * Converts a pure Either to an Abort effect, shifting Left to Abort failure and Right to success.
   */
  inline def toAbort: A < Abort[E] =
    Abort.get(either)

extension [A](t: Try[A])
  /**
   * Converts a pure Try to an Abort[Throwable] effect, shifting Failure to Abort failure.
   */
  inline def toAbort: A < Abort[Throwable] =
    t match
      case Success(v) => v
      case Failure(e) => Abort.fail(e)

extension [A](maybe: Maybe[A])
  /**
   * Converts a pure Maybe to an Abort effect that fails with the specified error when the Maybe is Absent.
   */
  inline def toAbort[E](inline err: => E): A < Abort[E] =
    maybe match
      case Present(v) => v
      case Absent     => Abort.fail(err)

extension [E, A](result: Result[E, A])
  /**
   * Converts a pure Result to an Abort effect.
   */
  inline def toAbort: A < Abort[E] =
    Abort.get(result)

extension [A, S](effect: Option[A] < S)
  /**
   * Converts an effectful Option (`Option[A] < S`) to an Abort effect that fails with the specified error when it is None.
   * 
   * Example:
   * {{{
   * val userOpt: Option[User] < Env[Db] = database.findUser(42)
   * val userEffect: User < (Abort[String] & Env[Db]) = userOpt.toAbort("User not found")
   * }}}
   */
  inline def toAbort[E](inline err: => E): A < (Abort[E] & S) =
    effect.map {
      case Some(v) => v
      case None    => Abort.fail(err)
    }

extension [E, A, S](effect: Either[E, A] < S)
  /**
   * Converts an effectful Either (`Either[E, A] < S`) to an Abort effect, shifting Left to Abort failure.
   */
  inline def toAbort: A < (Abort[E] & S) =
    effect.map(Abort.get)

extension [A, S](effect: Try[A] < S)
  /**
   * Converts an effectful Try (`Try[A] < S`) to an Abort[Throwable] effect, shifting Failure to Abort failure.
   */
  inline def toAbort: A < (Abort[Throwable] & S) =
    effect.map {
      case Success(v) => v
      case Failure(e) => Abort.fail(e)
    }

extension [A, S](effect: Maybe[A] < S)
  /**
   * Converts an effectful Maybe (`Maybe[A] < S`) to an Abort effect that fails with the specified error when it is Absent.
   */
  inline def toAbort[E](inline err: => E): A < (Abort[E] & S) =
    effect.map {
      case Present(v) => v
      case Absent     => Abort.fail(err)
    }

extension [E, A, S](effect: Result[E, A] < S)
  /**
   * Converts an effectful Result (`Result[E, A] < S`) to an Abort effect.
   */
  inline def toAbort: A < (Abort[E] & S) =
    effect.map(Abort.get)


// --- C. Dependency Injection Extensions (Env) ---

extension [R, A, S](effect: A < (Env[R] & S))
  /**
   * Directly provides a concrete dependency value/instance to satisfy the Env requirement. (Useful for dynamic/request-scoped values)
   * 
   * Example:
   * {{{
   * programRequiringConfig.provide(Config(maxOrders = 10))
   * }}}
   */
  inline def provide(dependency: R)(using Tag[R]): A < S =
    Env.run(dependency)(effect)

  /**
   * Provides a dependency Layer to satisfy the Env requirement. (Allows compile-time graph wiring and memoization)
   * 
   * Example:
   * {{{
   * programRequiringRepo.provideLayer(UserRepository.liveLayer)
   * }}}
   */
  transparent inline def provideLayer[S2](inline layer: Layer[?, S2]) =
    Env.runLayer(layer)(effect)


// --- D. Testing Utilities (Result) ---

extension [E, A](result: Result[E, A])
  /**
   * Converts a Kyo Result into a standard Scala Either. (Extremely useful for unit test assertions)
   */
  def toEither: Either[E, A] = result match
    case Result.Success(v) => Right(v)
    case Result.Failure(e) => Left(e)
    case Result.Panic(ex)  => throw ex




// --- E. Practical Usage Examples ---

/**
 * A collection of simple examples to help developers get started with these utilities.
 */
object TokyoExamples:
  import kyo.*

  case class User(id: Int, name: String)
  case class AppConfig(dbUrl: String)

  // 1. Lifting an effectful Option into Abort
  def fetchUserFromDb(id: Int): Option[User] < Any =
    if id == 42 then Some(User(42, "Tokyo User")) else None

  // Converts Option[User] < Any returned by fetchUserFromDb into User < Abort[String]
  def getUserOrError(id: Int): User < Abort[String] =
    fetchUserFromDb(id).toAbort(s"User with ID $id not found.")

  // 2. Dependency Injection (provide vs provideLayer)
  def myProgram: String < Env[AppConfig] =
    Env.use[AppConfig](cfg => s"App config db is connected to: ${cfg.dbUrl}")

  // 2-1. Using provide (directly injects a runtime/dynamic instance)
  def runWithProvide: String < Any =
    val runtimeConfig = AppConfig("jdbc:mysql://localhost:3306/production")
    myProgram.provide(runtimeConfig)

  // 2-2. Using provideLayer (injects a Layer blueprint which handles lifecycles/dependencies)
  val configLayer: Layer[AppConfig, Any] = Layer(AppConfig("jdbc:postgresql://localhost:5432/development"))
  
  def runWithProvideLayer: String < Memo =
    myProgram.provideLayer(configLayer)
