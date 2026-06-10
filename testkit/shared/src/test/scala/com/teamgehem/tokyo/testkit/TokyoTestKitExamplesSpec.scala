package com.teamgehem.tokyo.testkit

import kyo.*
import com.teamgehem.tokyo.testkit.TestKyoExtensions.*
import munit.FunSuite
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// --- 1. Business Model and Service Definition ---

case class User(id: Long, name: String, email: String, isActive: Boolean)

class UserService:
  // In-memory mock database
  private val db = Map(
    1L -> User(1L, "Alice", "alice@example.com", isActive = true),
    2L -> User(2L, "Bob", "bob@example.com", isActive = false)
  )

  /**
   * 1. Example business logic for runSync
   * Finds a user in the database, aborts/fails if not found.
   */
  def findUser(id: Long): User < Abort[String] =
    db.get(id) match
      case Some(user) => user
      case None       => Abort.fail(s"User with ID $id not found")

  /**
   * 2. Example business logic for runSyncPure
   * Pure synchronous operation returning greeting string. (No abort/failure)
   */
  def greetUser(user: User): String < Any =
    s"Hello, ${user.name}!"

  /**
   * 3. Example business logic for runAsync
   * Asynchronously fetches multiple users in parallel. Aborts if any ID <= 0.
   */
  def fetchUsersAsync(ids: List[Long]): List[User] < (Abort[String] & Async & Sync & Scope) =
    if ids.exists(_ <= 0) then
      Abort.fail("Invalid ID requested")
    else
      // Asynchronously fetch each user by forking a Fiber for each task.
      // Let the compiler infer the types of the list of fibers.
      val fiberTasks = ids.map(id => Fiber.init(findUser(id)))
      
      // Sequence the Fiber initialization effects
      val fibersEffect = Kyo.collectAll(fiberTasks)

      // Join all forks to get the final list of results asynchronously
      fibersEffect.flatMap { fibers =>
        val joins = fibers.map(_.get)
        Kyo.collectAll(joins)
      }

  /**
   * 4. Example business logic for runAsyncPure
   * Pure asynchronous task without abort. (e.g. sleeps, then returns status)
   */
  def calculateDelayStatus(user: User): Boolean < Async =
    // Wait 50ms, then return user's active status
    Async.sleep(50.millis).andThen(user.isActive)


// --- 2. Test Suite Implementation ---

class TokyoTestKitExamplesSpec extends FunSuite:
  private val service = new UserService

  // 1. runSync Example
  // Verify synchronous business logic containing Abort effect as Either type.
  test("findUser - runSync Example") {
    // Verify success scenario
    val successResult = service.findUser(1L).runSync
    assertEquals(successResult, Right(User(1L, "Alice", "alice@example.com", isActive = true)))

    // Verify failure scenario
    val failureResult = service.findUser(99L).runSync
    assertEquals(failureResult, Left("User with ID 99 not found"))
  }

  // 2. runSyncPure Example
  // Verify pure synchronous logic directly as success value.
  test("greetUser - runSyncPure Example") {
    val user = User(1L, "Alice", "alice@example.com", isActive = true)
    val greeting = service.greetUser(user).runSyncPure
    assertEquals(greeting, "Hello, Alice!")
  }

  // 3. runAsync Example
  // Verify parallel asynchronous logic containing Abort effect as Future[Either].
  test("fetchUsersAsync - runAsync Example") {
    // Success scenario: Parallel asynchronous query validation.
    // We handle the Scope effect introduced by forking by using Scope.run before runAsync.
    val successFuture: Future[Either[String | Throwable, List[User]]] =
      Scope.run(service.fetchUsersAsync(List(1L, 2L))).runAsync

    val successAssertion = successFuture.map { result =>
      assertEquals(
        result,
        Right(List(
          User(1L, "Alice", "alice@example.com", isActive = true),
          User(2L, "Bob", "bob@example.com", isActive = false)
        ))
      )
    }(using scala.concurrent.ExecutionContext.global)

    // Failure scenario: Fails asynchronously if any ID <= 0
    val failureFuture = Scope.run(service.fetchUsersAsync(List(1L, -5L))).runAsync
    val failureAssertion = failureFuture.map { result =>
      assertEquals(result, Left("Invalid ID requested"))
    }(using scala.concurrent.ExecutionContext.global)

    // Sequence both asynchronous assertions to finish
    Future.sequence(List(successAssertion, failureAssertion))
  }

  // 4. runAsyncPure Example
  // Verify pure asynchronous logic containing only Async effect as Future[T].
  test("calculateDelayStatus - runAsyncPure Example") {
    val user = User(1L, "Alice", "alice@example.com", isActive = true)
    val statusFuture: Future[Boolean] = service.calculateDelayStatus(user).runAsyncPure

    statusFuture.map { isActive =>
      assertEquals(isActive, true)
    }(using scala.concurrent.ExecutionContext.global)
  }
