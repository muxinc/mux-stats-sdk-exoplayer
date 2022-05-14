package com.mux.exoplayeradapter.double

import com.mux.stats.sdk.core.events.IEvent
import com.mux.stats.sdk.core.events.IEventDispatcher
import org.junit.Assert.assertNotNull
import java.util.*

class FakeEventDispatcher : IEventDispatcher {
  private val _events = LinkedList<Record>()
  val captureRecords: List<Record> get() = _events
  val capturedEvents get() = _events.map { it.event }

  override fun dispatch(e: IEvent?) {
    assertNotNull("null events should not be dispatched to the bus", e)
    e?.let { _events.add(Record(it, System.currentTimeMillis())) }
  }

  fun assertHasNoneOf(event: IEvent) {
    assertHasNoneOf(listOf(event))
  }

  fun assertHasNoneOf(events: List<IEvent>) {
    val eventTypes = capturedEvents.map { it.type }
    events.map { it.type }.onEach {
      if (eventTypes.contains(it)) {
        throw AssertionError("events should have none of: $events\n\tin: $capturedEvents")
      }
    }
  }

  fun assertHas(event: IEvent) {
    assertHasAllOf(listOf(event))
  }

  fun assertHasOneOf(event: IEvent) {
    assertHasThisManyOf(event, 1)
  }

  fun assertHasThisManyOf(event: IEvent, count: Int) {
    val eventsOfType = capturedEvents.filter { it.type == event.type }
    if (eventsOfType.size != count) {
      failAssert("Wrong number of events of type ${event.type}", capturedEvents)
    }
  }

  fun assertHasOneOrMoreOf(event: IEvent) {
    val eventsOfType = capturedEvents.filter { it.type == event.type }.map { it.type }
    if (!eventsOfType.contains(event.type)) {
      throw AssertionError("Event $event was not present in:\n\t$capturedEvents")
    }
  }

  fun assertHasAllOf(expected: List<IEvent>) {
    val hasAll = capturedEvents.map { it.type }.containsAll(expected.map { it.type })
    if (!hasAll) failAssert("did not capture all expected events", expected, capturedEvents)
  }

  fun assertHasExactlyThese(expected: List<IEvent>) {
    if (captureRecords.isEmpty()) {
      failAssert("Expected events, but captured none", expected, capturedEvents)
    }

    if (expected.size != captureRecords.size) {
      failAssert("Captured the wrong number of events", expected, capturedEvents)
    }

    for (idx in expected.indices) {
      if (expected[idx].type != captureRecords[idx].event.type) {
        failAssert(
          "Captured events did not exactly match",
          expected,
          captureRecords.map { it.event })
      }
    }
  }

  private fun failAssert(message: String, actual: List<IEvent>) {
    throw AssertionError("$message:\nActual: $actual")
  }

  private fun failAssert(message: String, expected: List<IEvent>, actual: List<IEvent>) {
    throw AssertionError("$message:\nActual: $actual\nExpected: $expected")
  }

  data class Record(val event: IEvent, val systemTime: Long)
}
