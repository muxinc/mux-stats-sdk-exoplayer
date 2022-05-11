package com.mux.exoplayeradapter

import com.mux.exoplayeradapter.double.FakeEventDispatcher
import com.mux.stats.sdk.core.events.playback.*
import com.mux.stats.sdk.muxstats.MuxPlayerState
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.MuxStats
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class StateCollectorTests : AbsRobolectricTest() {

  private lateinit var stateCollector: MuxStateCollector
  private lateinit var eventDispatcher: FakeEventDispatcher

  @Before
  fun setUpCollector() {
    eventDispatcher = FakeEventDispatcher()
    val stats = mockk<MuxStats>(relaxed = true)
    stateCollector = MuxStateCollector(stats, eventDispatcher)
  }

  @Test
  fun testBuffering() {
    stateCollector.buffering()
    stateCollector.buffering() // The buffering state is re-entrant

    assertEquals(
      "init -> buffering => buffering",
      stateCollector.muxPlayerState,
      MuxPlayerState.BUFFERING
    )
    eventDispatcher.assertOnlyThese(listOf(TimeUpdateEvent(null)))
  }

  @Test
  fun testRebuffering() {
    stateCollector.playing() // initial condition: play then buffering

    stateCollector.buffering() // Buffering events after play are Rebuffering
    stateCollector.buffering() // Rebuffering/buffering state is re-entrant
    assertEquals(
      "playing -> buffering => rebuffering",
      stateCollector.muxPlayerState,
      MuxPlayerState.REBUFFERING
    )
    eventDispatcher.assertHasOneOf(RebufferStartEvent(null))

    stateCollector.playing()
    eventDispatcher.assertHasOneOf(RebufferEndEvent(null))
  }

  @Test
  fun testPlay() {
    // Initial case: play() from any state
    stateCollector.play()
    assertEquals("init -> play = play", stateCollector.muxPlayerState, MuxPlayerState.PLAY)
    eventDispatcher.assertHasOneOf(PlayEvent(null))

    // The first play() while seeking should be recorded
    setUpCollector()
    stateCollector.seeking()
    stateCollector.play()
    assertEquals(
      "the first play event should always be recorded",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAY
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
    // The first play() while seeked should be recorded
    setUpCollector()
    stateCollector.seeking()
    stateCollector.seeked(false)
    stateCollector.play()
    assertEquals(
      "the first play event should always be recorded",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAY
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))

    // subsequent play() while rebuffering should be ignored
    setUpCollector()
    stateCollector.play()
    stateCollector.playing()
    stateCollector.buffering()
    stateCollector.play()
    assertNotEquals(
      "play -> buffering -> play = buffering",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAY
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
    // subsequent play() while seeked should be ignored
    setUpCollector()
    stateCollector.play()
    stateCollector.seeking()
    stateCollector.seeked(false)
    stateCollector.play()
    assertNotEquals(
      "play -> buffering -> play = buffering",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAY
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
    // subsequent play() while seeking should be ignored
    setUpCollector()
    stateCollector.play()
    stateCollector.seeking()
    stateCollector.play()
    assertNotEquals(
      "play -> buffering -> play = buffering",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAY
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
  }

  @Test
  fun testPlaying() {
    // playing() ignored if seeking.
    stateCollector.seeking()
    stateCollector.playing()
    assertNotEquals(
      "seeking -> playing = seeking",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAYING
    )
    eventDispatcher.assertHasNoneOf(PlayingEvent(null))

    // if paused, should go through PLAY and into PLAYING
    setUpCollector()
    stateCollector.pause()
    stateCollector.playing()
    assertEquals(
      "pause -> playing() = play",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAYING
    )
    eventDispatcher.assertHasOneOf(PlayEvent(null))
    eventDispatcher.assertHasOneOf(PlayingEvent(null))

    // if rebuffering, should signal rebuffering is ended and then go to PLAYING
    setUpCollector()
    stateCollector.play()
    stateCollector.playing()
    stateCollector.buffering()
    stateCollector.playing()
    assertEquals(
      "pause -> playing() = play",
      stateCollector.muxPlayerState,
      MuxPlayerState.PLAYING
    )
    eventDispatcher.assertHasOneOf(RebufferEndEvent(null))
    // should be 2, but only the second playing() call is under test
    eventDispatcher.assertHasOneOrMoreOf(PlayingEvent(null))
  }

  @Test
  fun testPause() {
    // if already paused, skip
    stateCollector.pause()
    stateCollector.pause()
    assertEquals("")
  }
}
