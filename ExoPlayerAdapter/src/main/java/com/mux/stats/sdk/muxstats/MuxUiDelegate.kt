package com.mux.stats.sdk.muxstats

import android.app.Activity
import android.graphics.Point
import android.view.View
import android.widget.TextView
import com.mux.stats.sdk.muxstats.internal.weak

/**
 * Allows implementers to supply data about the view and screen being used for playback
 */
abstract class MuxUiDelegate<PlayerView>(view: PlayerView) {
  var view by weak(view)

  /**
   * Gets the size of the player view in px as a pair of (width, height)
   * If {@link #view} is non-null returns the size of the player view
   * It (@link #view} is null, returns size of 0
   */
  abstract fun getPlayerViewSize(): Point

  /**
   * Gets the sie of the entire screen in px, not including nav/statusbar/window insets
   * If the View is null, returns a size of 0
   */
  abstract fun getScreenSize(): Point
}

/**
 * MuxViewDelegate for an Android View.
 */
private class AndroidUiDelegate<PlayerView : View>(activity: Activity?, view: PlayerView)
  : MuxUiDelegate<PlayerView>(view) {

  private val _screenSize: Point = Point().let { size ->
    @Suppress("DEPRECATION") // fullscreen considers screen size, less system decoration
    activity?.windowManager?.defaultDisplay?.getSize(size)
    size
  }

  override fun getPlayerViewSize(): Point = view?.let { view ->
    Point().apply {
      x = view.width
      y = view.height
    }
  } ?: Point()

  override fun getScreenSize(): Point = _screenSize
}

/**
 * Create a MuxUiDelegate based on a View
 */
@JvmSynthetic
internal fun <PlayerView : View> PlayerView.muxUiDelegate(activity: Activity?)
        : MuxUiDelegate<PlayerView> = AndroidUiDelegate(activity, this)
