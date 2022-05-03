package com.mux.exoplayeradapter

import com.mux.exoplayeradapter.double.ShadowExoPlayer
import com.mux.exoplayeradapter.double.ShadowSimpleExoPlayer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(manifest = Config.NONE, shadows = [ShadowExoPlayer::class, ShadowSimpleExoPlayer::class])
@RunWith(RobolectricTestRunner::class)
abstract class AbsRobolectricTest {
}
