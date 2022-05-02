package com.mux.exoplayeradapter

import com.mux.exoplayeradapter.double.FakePlayerBinding
import com.mux.exoplayeradapter.double.UiDelegateMocks
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.muxUiDelegate
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerAdapterTests : AbsRobolectricTest() {

  @Test
  fun testPlayerReachability() {
    var fakePlayer: Any? = Object()
    var fakeExtraPlayer: Any? = Object()
    val mockUiDelegate = UiDelegateMocks.mockView().muxUiDelegate(UiDelegateMocks.mockActivity())
    val mockCollector = mockk<MuxStateCollector> {}

    val playerAdapter = MuxPlayerAdapter(
      collector = mockCollector,
      player = fakePlayer,
      uiDelegate = mockUiDelegate,
      basicMetrics = FakePlayerBinding(),
      extraMetrics = MuxPlayerAdapter.ExtraPlayerBindings(
        fakeExtraPlayer, listOf(FakePlayerBinding())
      )
    )

    // Null out *our* references
    @Suppress("UNUSED_VALUE")
    fakePlayer = null
    @Suppress("UNUSED_VALUE")
    fakeExtraPlayer = null

    // For luck
    System.gc()

    runBlocking { delay(5 * 1000) }

    assertNull("player view should be weakly reachable", playerAdapter.uiDelegate.view)
    assertNull("player should be weakly reachable", playerAdapter.basicPlayer)
    assertNull("extra player should be weakly reachable", playerAdapter.extraPlayer)
  }
}