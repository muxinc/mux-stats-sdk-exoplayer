package com.mux.exoplayeradapter

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.view.View
import com.mux.stats.sdk.muxstats.muxUiDelegate
import com.mux.stats.sdk.muxstats.noUiDelegate
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UiDelegateTests : AbsRobolectricTest() {

  companion object {
    const val MOCK_SCREEN_WIDTH = 2400
    const val MOCK_SCREEN_HEIGHT = 1080

    const val MOCK_PLAYER_WIDTH = 1080
    const val MOCK_PLAYER_HEIGHT = 700
  }

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
    val context = mockk<Context> {}
    val uiDelegate = context.noUiDelegate()

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

  private fun mockView() = mockk<View> {
    every { width } returns MOCK_PLAYER_WIDTH
    every { height } returns MOCK_PLAYER_HEIGHT
  }

  /**
   * Mocks the path we call to get the size of the screen
   */
  private fun mockActivity() = mockk<Activity> {
    every { windowManager } returns mockk {
      every { defaultDisplay } returns mockk {
        every { getSize(Point()) } answers {
          arg<Point>(0).apply {
            x = MOCK_SCREEN_WIDTH
            y = MOCK_SCREEN_HEIGHT
          }
        }
      }
    }
  }
}
