package com.mux.stats.sdk.muxstats;

import android.app.Activity;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.ViewInteraction;
import androidx.test.runner.AndroidJUnit4;

import com.mux.stats.sdk.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MuxStatsLateInitInstrumentationTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        ViewInteraction interaction = onView(withId(R.id.player_view));
        interaction.perform(click());

        assertEquals("com.example.hotmicsyncproto", appContext.getPackageName());
    }
}