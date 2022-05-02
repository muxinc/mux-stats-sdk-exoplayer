package com.mux.exoplayeradapter

import android.view.View
import com.mux.exoplayeradapter.double.FakePlayerBinding
import com.mux.exoplayeradapter.double.UiDelegateMocks
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.MuxUiDelegate
import com.mux.stats.sdk.muxstats.internal.logTag
import com.mux.stats.sdk.muxstats.internal.observableWeak
import com.mux.stats.sdk.muxstats.muxUiDelegate
import io.mockk.*
import org.junit.Test
import java.lang.AssertionError

class PlayerAdapterTests : AbsRobolectricTest() {

  @Test
  fun testBindOnCreate() {
    val basicBinding1 = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } throws AssertionError("unbind shouldn't be called")
    }
    val extraBinding1 = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } throws AssertionError("unbind shouldn't be called")
    }
    val playerAdapter = playerAdapter(basicBinding1, extraBinding1)
    // ctors should invoke the bindings
    verify {
      basicBinding1.bindPlayer(any(), any())
      extraBinding1.bindPlayer(any(), any())
    }
  }

  @Test
  fun testBindAndUnbind() {
    val basicBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } just runs
    }
    val extraBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } just runs
    }
    val playerAdapter = playerAdapter(basicBinding, extraBinding)
    val basicPlayer1 = playerAdapter.basicPlayer
    val basicPlayer2: Any = Object()
    val extraPlayer1 = playerAdapter.extraPlayer
    val extraPlayer2: Any = Object()

    playerAdapter.basicPlayer = basicPlayer2
    verifySequence {
      basicBinding.bindPlayer(any(), any())
      basicBinding.unbindPlayer(eq(basicPlayer1!!), any())
      basicBinding.bindPlayer(eq(basicPlayer2), any())
    }

    playerAdapter.extraPlayer = extraPlayer2
    verifySequence {
      extraBinding.bindPlayer(any(), any())
      extraBinding.unbindPlayer(eq(extraPlayer1!!), any())
      extraBinding.bindPlayer(eq(extraPlayer2), any())
    }
  }

  @Test
  fun testUnbindEverything() {
    val basicBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } just runs
    }
    val extraBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } just runs
    }
    val playerAdapter = playerAdapter(basicBinding, extraBinding)
    playerAdapter.unbindEverything()

    verify {
      basicBinding.unbindPlayer(any(), any())
      extraBinding.unbindPlayer(any(), any())
    }
  }

  // TODO: Untangle this. Probably need to run on a device so a heap dump can be obtained
//  @Test
//  fun reachability() {
//    var thing: Any? = Object()
//    val x = X(thing, "")
//    thing = null
//    System.gc()
//    assertNull(
//      "should be null",
//      x.delegated
//    )
//  }
//
//  @Test
//  fun testPlayerReachability() {
//    var fakePlayer: Any? = Object()
//    var fakeExtraPlayer: Any? = Object()
//    var mockUiDelegate: MuxUiDelegate<*>? =
//      UiDelegateMocks.mockView().muxUiDelegate(UiDelegateMocks.mockActivity())
//    val mockCollector = mockStateCollector()
//
//    val playerAdapter = MuxPlayerAdapter(
//      collector = mockCollector,
//      player = fakePlayer,
//      uiDelegate = mockUiDelegate!!,
//      basicMetrics = FakePlayerBinding("basic metrics"),
//      extraMetrics = MuxPlayerAdapter.ExtraPlayerBindings(
//        fakeExtraPlayer, listOf(FakePlayerBinding("extra metrics 0"))
//      )
//    )
//    // Make the system gc the player
//    @Suppress("UNUSED_VALUE")
//    fakePlayer = null
//    @Suppress("UNUSED_VALUE")
//    fakeExtraPlayer = null
//    @Suppress("UNUSED_VALUE")
//    mockUiDelegate = null
//    System.gc()
//
//    //runBlocking { delay(5 * 1000) }
//
//    assertNull("extra player should be weakly reachable", playerAdapter.extraPlayer)
//    assertNull("player should be weakly reachable", playerAdapter.basicPlayer)
//    assertNull("player view should be weakly reachable", playerAdapter.uiDelegate.view)
//  }

  private fun playerAdapter(
    basicMetrics: MuxPlayerAdapter.PlayerBinding<Any> = FakePlayerBinding("basic metrics"),
    extraMetrics: MuxPlayerAdapter.PlayerBinding<Any> = FakePlayerBinding("extra metrics")
  ): MuxPlayerAdapter<View, Any, Any> {
    var fakePlayer: Any = Object()
    var fakeExtraPlayer: Any = Object()
    var mockUiDelegate: MuxUiDelegate<View> =
      UiDelegateMocks.mockView().muxUiDelegate(UiDelegateMocks.mockActivity())
    val mockCollector = mockStateCollector()

    return MuxPlayerAdapter(
      player = fakePlayer,
      uiDelegate = mockUiDelegate,
      basicMetrics = basicMetrics,
      extraMetrics = MuxPlayerAdapter.ExtraPlayerBindings(
        fakeExtraPlayer,
        listOf(extraMetrics)
      ),
      collector = mockCollector
    )
  }

  private fun mockStateCollector() = mockk<MuxStateCollector>()

}

private class X<T>(t: T?, val anything: String) {
  //  private var t = WeakReference(t)
//  var prop: T?
//    get() = t.get()
//    set(value) {
//      t = WeakReference(value)
//    }
  //var delegated by observableWeak(t) { Log.d(logTag(), "$it")}
  var delegated by observableWeak(t) { something(it, anything) }

  private fun something(t: T?, anything: String) {
    t?.let {
      log(logTag(), "$it $anything")
    }
  }

  //fun get(): T? = t.get()
}
