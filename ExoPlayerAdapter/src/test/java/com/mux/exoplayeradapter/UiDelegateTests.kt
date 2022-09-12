package com.mux.exoplayeradapter

import android.app.Activity
import android.content.Context
import android.view.View
import com.mux.exoplayeradapter.double.UiDelegateMocks.MOCK_PLAYER_HEIGHT
import com.mux.exoplayeradapter.double.UiDelegateMocks.MOCK_PLAYER_WIDTH
import com.mux.exoplayeradapter.double.UiDelegateMocks.MOCK_SCREEN_HEIGHT
import com.mux.exoplayeradapter.double.UiDelegateMocks.MOCK_SCREEN_WIDTH
import com.mux.exoplayeradapter.double.UiDelegateMocks.mockActivity
import com.mux.exoplayeradapter.double.UiDelegateMocks.mockView
import com.mux.stats.sdk.muxstats.muxUiDelegate
import com.mux.stats.sdk.muxstats.noUiDelegate
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UiDelegateTests : AbsRobolectricTest() {

  lateinit var activity: Activity
  lateinit var view: View

  @Before
  fun initMocks() {
    activity = mockActivity()
    view = mockView()
  }

  @Test
  fun testPlayerSize() {
    val resizingView = mockk<View> {
      every { width } returnsMany listOf(
        MOCK_PLAYER_WIDTH, MOCK_PLAYER_WIDTH,
        MOCK_PLAYER_WIDTH / 2, MOCK_PLAYER_WIDTH / 2
      )
      every { height } returnsMany listOf(
        MOCK_PLAYER_HEIGHT, MOCK_PLAYER_HEIGHT,
        MOCK_PLAYER_HEIGHT / 2, MOCK_PLAYER_HEIGHT / 2
      )
    }
    val uiDelegate = resizingView.muxUiDelegate(activity)

    // basic case
    assertEquals(
      "player view size is reported",
      uiDelegate.getPlayerViewSize().x,
      MOCK_PLAYER_WIDTH
    )
    assertEquals(
      "player view size is reported",
      uiDelegate.getPlayerViewSize().y,
      MOCK_PLAYER_HEIGHT
    )

    // size changed
    assertEquals(
      "player view size changes are reported",
      uiDelegate.getPlayerViewSize().y,
      MOCK_PLAYER_HEIGHT / 2
    )
    assertEquals(
      "player view size changes are reported",
      uiDelegate.getPlayerViewSize().x,
      MOCK_PLAYER_WIDTH / 2
    )
  }

  @Test
  fun testScreenSize() {
    val uiDelegate = view.muxUiDelegate(activity)
    assertEquals(
      "screen size is reported",
      uiDelegate.getScreenSize().x,
      MOCK_SCREEN_WIDTH
    )
    assertEquals(
      "player view size is reported",
      uiDelegate.getScreenSize().y,
      MOCK_SCREEN_HEIGHT
    )
  }

  @Test
  fun testNoUi() {
    val uiDelegate = noUiDelegate()

    val screenSize = uiDelegate.getScreenSize()
    assertEquals(
      "no ui: screen size should be 0",
      screenSize.x,
      0
    )
    assertEquals(
      "no ui: screen size should be 0",
      screenSize.y,
      0
    )

    val playerSize = uiDelegate.getPlayerViewSize()
    assertEquals(
      "no ui: player size should be 0",
      playerSize.x,
      0
    )
    assertEquals(
      "no ui: player size should be 0",
      playerSize.y,
      0
    )
  }
}
