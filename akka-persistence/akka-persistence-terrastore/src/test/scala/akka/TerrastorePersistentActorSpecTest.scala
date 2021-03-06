package akka.persistence.terrastore

import akka.actor.{ Actor, ActorRef }
import Actor._
import akka.stm._

import org.junit.Test
import org.junit.Assert._

import org.scalatest.junit.JUnitSuite
import org.scalatest.BeforeAndAfterAll

case class GetMapState(key: String)
case object GetVectorState
case object GetVectorSize
case object GetRefState

case class SetMapState(key: String, value: String)
case class SetVectorState(key: String)
case class SetRefState(key: String)
case class Success(key: String, value: String)
case class Failure(key: String, value: String)

case class SetMapStateOneWay(key: String, value: String)
case class SetVectorStateOneWay(key: String)
case class SetRefStateOneWay(key: String)
case class SuccessOneWay(key: String, value: String)
case class FailureOneWay(key: String, value: String)

class TerrastorePersistentActor extends Actor {
  self.timeout = 100000

  private val mapState = TerrastoreStorage.newMap
  private val vectorState = TerrastoreStorage.newVector
  private val refState = TerrastoreStorage.newRef

  def receive = { case message => atomic { atomicReceive(message) } }

  def atomicReceive: Receive = {
    case GetMapState(key) =>
      self.reply(mapState.get(key.getBytes("UTF-8")).get)
    case GetVectorSize =>
      self.reply(vectorState.length.asInstanceOf[AnyRef])
    case GetRefState =>
      self.reply(refState.get.get)
    case SetMapState(key, msg) =>
      mapState.put(key.getBytes("UTF-8"), msg.getBytes("UTF-8"))
      self.reply(msg)
    case SetVectorState(msg) =>
      vectorState.add(msg.getBytes("UTF-8"))
      self.reply(msg)
    case SetRefState(msg) =>
      refState.swap(msg.getBytes("UTF-8"))
      self.reply(msg)
    case Success(key, msg) =>
      mapState.put(key.getBytes("UTF-8"), msg.getBytes("UTF-8"))
      vectorState.add(msg.getBytes("UTF-8"))
      refState.swap(msg.getBytes("UTF-8"))
      self.reply(msg)
    case Failure(key, msg) =>
      mapState.put(key.getBytes("UTF-8"), msg.getBytes("UTF-8"))
      vectorState.add(msg.getBytes("UTF-8"))
      refState.swap(msg.getBytes("UTF-8"))
      fail
      self.reply(msg)
  }

  def fail = throw new RuntimeException("Expected exception; to test fault-tolerance")
}

class TerrastorePersistentActorSpecTest extends JUnitSuite with BeforeAndAfterAll with EmbeddedTerrastore {

  @Test
  def testMapShouldNotRollbackStateForStatefulServerInCaseOfSuccess = {
    val stateful = actorOf[TerrastorePersistentActor]
    stateful.start
    stateful !! SetMapState("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "init") // set init state
    stateful !! Success("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "new state") // transactionrequired
    val result = (stateful !! GetMapState("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess")).as[Array[Byte]].get
    assertEquals("new state", new String(result, 0, result.length, "UTF-8"))
  }

  @Test
  def testMapShouldRollbackStateForStatefulServerInCaseOfFailure = {
    val stateful = actorOf[TerrastorePersistentActor]
    stateful.start
    stateful !! SetMapState("testShouldRollbackStateForStatefulServerInCaseOfFailure", "init") // set init state
    try {
      stateful !! Failure("testShouldRollbackStateForStatefulServerInCaseOfFailure", "new state") // call failing transactionrequired method
      fail("should have thrown an exception")
    } catch { case e: RuntimeException => {} }
    val result = (stateful !! GetMapState("testShouldRollbackStateForStatefulServerInCaseOfFailure")).as[Array[Byte]].get
    assertEquals("init", new String(result, 0, result.length, "UTF-8")) // check that state is == init state
  }

  @Test
  def testVectorShouldNotRollbackStateForStatefulServerInCaseOfSuccess = {
    val stateful = actorOf[TerrastorePersistentActor]
    stateful.start
    stateful !! SetVectorState("init") // set init state
    stateful !! Success("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "new state") // transactionrequired
    assertEquals(2, (stateful !! GetVectorSize).get.asInstanceOf[java.lang.Integer].intValue)
  }

  @Test
  def testVectorShouldRollbackStateForStatefulServerInCaseOfFailure = {
    val stateful = actorOf[TerrastorePersistentActor]
    stateful.start
    stateful !! SetVectorState("init") // set init state
    try {
      stateful !! Failure("testShouldRollbackStateForStatefulServerInCaseOfFailure", "new state") // call failing transactionrequired method
      fail("should have thrown an exception")
    } catch { case e: RuntimeException => {} }
    assertEquals(1, (stateful !! GetVectorSize).get.asInstanceOf[java.lang.Integer].intValue)
  }

  @Test
  def testRefShouldNotRollbackStateForStatefulServerInCaseOfSuccess = {
    val stateful = actorOf[TerrastorePersistentActor]
    stateful.start
    stateful !! SetRefState("init") // set init state
    stateful !! Success("testShouldNotRollbackStateForStatefulServerInCaseOfSuccess", "new state") // transactionrequired
    val result = (stateful !! GetRefState).as[Array[Byte]].get
    assertEquals("new state", new String(result, 0, result.length, "UTF-8"))
  }

  @Test
  def testRefShouldRollbackStateForStatefulServerInCaseOfFailure = {
    val stateful = actorOf[TerrastorePersistentActor]
    stateful.start
    stateful !! SetRefState("init") // set init state
    try {
      stateful !! Failure("testShouldRollbackStateForStatefulServerInCaseOfFailure", "new state") // call failing transactionrequired method
      fail("should have thrown an exception")
    } catch { case e: RuntimeException => {} }
    val result = (stateful !! GetRefState).as[Array[Byte]].get
    assertEquals("init", new String(result, 0, result.length, "UTF-8")) // check that state is == init state
  }

}