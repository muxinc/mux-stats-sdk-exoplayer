package com.mux.exoplayeradapter.double

import android.app.Activity
import android.graphics.Point
import android.view.View
import io.mockk.every
import io.mockk.mockk

object UiDelegateMocks {

  const val MOCK_SCREEN_WIDTH = 2400
  const val MOCK_SCREEN_HEIGHT = 1080

  const val MOCK_PLAYER_WIDTH = 1080
  const val MOCK_PLAYER_HEIGHT = 700

  /**
   * Mocks a View of constant size
   */
  fun mockView() = mockk<View> {
    every { width } returns MOCK_PLAYER_WIDTH
    every { height } returns MOCK_PLAYER_HEIGHT
  }

  /**
   * Mocks the path we call to get the size of the screen
   */
  fun mockActivity() = mockk<Activity> {
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
