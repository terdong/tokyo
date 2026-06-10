# Tokyo

[![JitPack](https://jitpack.io/v/terdong/tokyo.svg)](https://jitpack.io/#terdong/tokyo)

Tokyo (Tool + Kyo) is a set of ergonomic utility and extension methods for the **[Kyo](https://github.com/getkyo/kyo)** effect system in Scala 3, with out-of-the-box support for both **JVM** and **Scala.js**.

## Features

- **Ergonomic Aborts**: Streamline error handling with fluent, left-to-right combinators (`mapAbort`, `tapError`, `recover`, `recoverWith`, `orElse`, `catchPanic`).
- **Standard & Kyo Type Bridging**: Easily lift standard Scala types (`Option`, `Either`, `Try`) as well as Kyo's native zero-allocation types (`Maybe`, `Result`) into Kyo's `Abort` effect row (`toAbort`). Works on both pure values and values wrapped in an effect row (`A < S`).
- **Dependency Injection**: Streamline environment provisioning using a ZIO-like syntax (`provide`, `provideLayer`).
- **Resource Safety**: Ensure finalizer execution across success, failure, and panic channels (`ensuring`).
- **Testing Utilities**: Bridge Kyo's `Result` to Scala's standard `Either` for easier test assertions (`toEither`).

## Installation

Add the JitPack resolver to your `build.sbt`:

```scala
resolvers += "jitpack" at "https://jitpack.io"
```

Then, add the library dependency to your `build.sbt`:

```scala
// For JVM-only projects
libraryDependencies += "com.github.terdong.tokyo" %% "tokyo" % "0.1.0"

// For Scala.js or cross-platform/shared projects
libraryDependencies += "com.github.terdong.tokyo" %%% "tokyo" % "0.1.0"
```

*Note: This library is experimental and has strict version compatibility requirements. It is compatible with **Scala 3.8.4 and newer** (compiled with Scala 3.8.4). This constraint exists to align with **Kyo 1.0.0-RC2** (which is compiled against Scala 3.8.3). Due to tracking these bleeding-edge releases, older Scala 3 versions are not supported.*

## Usage

### 1. Error Handling & Recovery Extensions (`Abort`)

Add fluent combinators to any computation containing an `Abort[E]` effect:

```scala
import kyo.*
import com.teamgehem.tokyo.*

val effect: Int < Abort[String] = Abort.fail("error")

// mapAbort: Transform the error channel while preserving Panics
val mapped: Int < Abort[Int] = effect.mapAbort(_.length)

// tapError: Perform an effectful side-effect on error (e.g., logging) without modifying the error
val tapped: Int < Abort[String] = effect.tapError(err => Console.printLine(s"Error: $err"))

// recover: Recover from a failure by providing a pure fallback value
val recovered: Int < Any = effect.recover(err => -1)

// recoverWith: Recover from a failure by running another effectful operation (e.g. backup database query)
val recoveredEffect: Int < Env[Db] = effect.recoverWith(err => queryBackup(err))

// orElse: Fallback to an alternative effect if the current one fails with an Abort
val fallbackResult = effect.orElse(Abort.fail("hard error"))

// catchPanic: Safely translate unhandled JVM runtime exceptions (Panics) into expected domain errors (Abort)
val safeTask = Abort.panic(new RuntimeException("Crash")).catchPanic(ex => ex.getMessage) // Abort.fail("Crash")

// tap: Intercept successful results to execute a side-effect (e.g., success logging)
val auditedTask = effect.tap(value => Console.printLine(s"Success: $value"))

// ensuring: Guarantee that a cleanup finalizer runs on success, failure, or panic (finally block)
val cleanedTask = useConnection(conn).ensuring(conn.close())
```

### 2. Type Bridging (Lifting to `Abort`)

Easily lift standard Scala types and Kyo's high-performance types (`Maybe`, `Result`) into Kyo's `Abort` effect row. This works on both pure types and effectful types:

```scala
import kyo.*
import com.teamgehem.tokyo.*
import scala.util.Try

// Pure Option & Either & Try
val opt: Option[Int] = Some(42)
val abortOpt: Int < Abort[String] = opt.toAbort("Value was None")

val either: Either[String, Int] = Right(42)
val abortEither: Int < Abort[String] = either.toAbort

val t: Try[Int] = Try(42)
val abortTry: Int < Abort[Throwable] = t.toAbort

// Kyo-native Maybe & Result (Zero-allocation replacements)
val maybe: Maybe[Int] = Present(42)
val abortMaybe: Int < Abort[String] = maybe.toAbort("Absent")

val result: Result[String, Int] = Result.Success(42)
val abortResult: Int < Abort[String] = result.toAbort

// Effectful versions (when values are wrapped in an effect row < S)
val optEffect: Option[Int] < Env[Db] = db.findUserOpt(1)
val user: User < (Abort[String] & Env[Db]) = optEffect.toAbort("User not found")

val maybeEffect: Maybe[Int] < Any = Present(42)
val userMaybe: Int < Abort[String] = maybeEffect.toAbort("Absent")

val resultEffect: Result[String, Int] < Any = Result.Success(42)
val userResult: Int < Abort[String] = resultEffect.toAbort
```

### 3. Dependency Injection Extensions (`Env`)

Streamline the provision of environments/dependencies using a ZIO-like syntax:

```scala
import kyo.*
import com.teamgehem.tokyo.*

trait Database:
  def query(q: String): String < IO

val queryEffect: String < (Env[Database] & IO) =
  for
    db <- Env.get[Database]
    res <- db.query("SELECT * FROM users")
  yield res

// 3-1. provide: Inject a concrete value/instance directly (useful for dynamic request context)
val runQuery: String < IO =
  queryEffect.provide(new Database {
    def query(q: String) = "result"
  })

// 3-2. provideLayer: Inject a Layer blueprint (handles compile-time auto-wiring and memoization)
val dbLayer: Layer[Database, Any] = Layer(new Database {
  def query(q: String) = "result"
})
val runQueryWithLayer: String < (IO & Memo) =
  queryEffect.provideLayer(dbLayer)
```

### 4. Testing Utilities (`Result`)

Bridge `Result` to standard `Either` for easier assertions in tests:

```scala
import kyo.*
import com.teamgehem.tokyo.*

val result: Result[String, Int] = Result.Success(42)
val either: Either[String, Int] = result.toEither // Right(42)
```

## License

This project is licensed under the Apache 2.0 License.
