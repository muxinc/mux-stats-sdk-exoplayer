package com.mux.exoplayeradapter.double

import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.util.*

fun shadowOf(player: ExoPlayer) = Shadow.extract<ShadowExoPlayer>(player)

@Suppress("DEPRECATION") // Exo before r2.16.1 has different bindings for SimpleExoPlayer
fun shadowOf(player: SimpleExoPlayer) = Shadow.extract<ShadowSimpleExoPlayer>(player)

/**
 * Shadow of an ExoPlayer. The underlying implementation (ExoPlayerImpl vs SimpleExoPlayer) doesn't
 * matter
 */
@Implements(ExoPlayer::class)
open class ShadowExoPlayer {

  private val analyticsListeners = ArrayList<AnalyticsListener>()
  private val eventListeners = ArrayList<EventListener>()
  private val playerListeners = ArrayList<Player.Listener>()

  @Implementation
  @Suppress("ProtectedInFinal")
  protected fun removeAnalyticsListener(lis: AnalyticsListener) = analyticsListeners.remove(lis)

  @Implementation
  @Suppress("ProtectedInFinal")
  protected fun addAnalyticsListener(lis: AnalyticsListener) = analyticsListeners.add(lis)

  @Implementation
  @Suppress("ProtectedInFinal")
  protected fun addListener(lis: EventListener) = eventListeners.add(lis)

  @Implementation
  @Suppress("ProtectedInFinal")
  protected fun removeListener(lis: EventListener) = eventListeners.remove(lis)

  @Implementation
  @Suppress("ProtectedInFinal")
  protected fun addListener(lis: Player.Listener) = playerListeners.add(lis)

  @Implementation
  @Suppress("ProtectedInFinal")
  protected fun removeListener(lis: Player.Listener) = playerListeners.remove(lis)

  // TODO: Add method that provoke listener events that we need (ie, simple state transitions and
  //  also event sequences with delays)
}

/**
 * Shadow of SimpleExoPlayer, specifically
 */
@Implements(SimpleExoPlayer::class)
class ShadowSimpleExoPlayer : ShadowExoPlayer()
