package com.mux.exoplayeradapter

import androidx.test.core.app.ApplicationProvider
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.mux.exoplayeradapter.double.shadowOf
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class ExoPlayerBindingTests : AbsRobolectricTest() {

  private var exoPlayer: ExoPlayer? = null

  @Suppress("DEPRECATION") // must be tested for versions up to r2.15.1
  private var simpleExoPlayer: SimpleExoPlayer? = null

  @Before
  fun constructExoPlayer() {
    exoPlayer = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    @Suppress("DEPRECATION")
    simpleExoPlayer = SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
  }

  @Test
  fun messAroundWithShadows() {
    val simplePlayer = simpleExoPlayer
    val player = exoPlayer
    val shadowSimplePlayer = shadowOf(simplePlayer!!)
    val shadowPlayer = shadowOf(player!!)

    assertNotEquals(
      "player and shadowPlayer are different objects",
      simplePlayer,
      shadowSimplePlayer
    )
    assertNotEquals("player and shadowPlayer are different objects", player, shadowPlayer)
  }
}