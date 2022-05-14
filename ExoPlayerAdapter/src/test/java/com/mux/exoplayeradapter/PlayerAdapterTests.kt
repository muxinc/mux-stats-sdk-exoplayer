package com.mux.exoplayeradapter

import android.view.View
import com.mux.exoplayeradapter.double.FakePlayerBinding
import com.mux.exoplayeradapter.double.UiDelegateMocks
import com.mux.stats.sdk.muxstats.MuxPlayerAdapter
import com.mux.stats.sdk.muxstats.MuxStateCollector
import com.mux.stats.sdk.muxstats.MuxUiDelegate
import com.mux.stats.sdk.muxstats.muxUiDelegate
import io.mockk.*
import org.junit.Test

class PlayerAdapterTests : AbsRobolectricTest() {

  @Test
  fun testBindOnCreate() {
    val basicBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } throws AssertionError("unbind shouldn't be called")
    }
    val extraBinding = mockk<MuxPlayerAdapter.PlayerBinding<Any>> {
      every { bindPlayer(any(), any()) } just runs
      every { unbindPlayer(any(), any()) } throws AssertionError("unbind shouldn't be called")
    }

    @Suppress("UNUSED_VARIABLE")
    val playerAdapter = playerAdapter(basicBinding, extraBinding)
    // ctors should invoke the bindings
    verify {
      basicBinding.bindPlayer(any(), any())
      extraBinding.bindPlayer(any(), any())
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

  private fun playerAdapter(
    basicMetrics: MuxPlayerAdapter.PlayerBinding<Any> = FakePlayerBinding("basic metrics"),
    extraMetrics: MuxPlayerAdapter.PlayerBinding<Any> = FakePlayerBinding("extra metrics")
  ): MuxPlayerAdapter<View, Any, Any> {
    val fakePlayer: Any = Object()
    val fakeExtraPlayer: Any = Object()
    val mockUiDelegate: MuxUiDelegate<View> =
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
