package com.mux.stats.sdk.muxstats.internal;

import static android.os.SystemClock.elapsedRealtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.mux.stats.sdk.core.util.MuxLogger;
import com.mux.stats.sdk.muxstats.BuildConfig;
import com.mux.stats.sdk.muxstats.IDevice;
import com.mux.stats.sdk.muxstats.LogPriority;
import com.mux.stats.sdk.muxstats.MuxStats;

import java.lang.ref.WeakReference;
import java.util.UUID;

/**
 * Basic device details such as OS version, vendor name and etc. Instances of this class
 * are used by {@link MuxStats} to interface with the device.
 */
public class MuxDevice implements IDevice {

  private static final String TAG = "MuxDevice";

  private static final String EXO_SOFTWARE = "ExoPlayer";

  static final String CONNECTION_TYPE_CELLULAR = "cellular";
  static final String CONNECTION_TYPE_WIFI = "wifi";
  static final String CONNECTION_TYPE_WIRED = "wired";
  static final String CONNECTION_TYPE_OTHER = "other";

  static final String MUX_DEVICE_ID = "MUX_DEVICE_ID";

  protected WeakReference<Context> contextRef;
  private String deviceId;
  private String appName = "";
  private String appVersion = "";
  /**
   * Use this value instead of auto detected name in case the value is different then null.
   */
  protected String metadataDeviceName = null;

  /**
   * Basic constructor.
   *
   * @param ctx activity context, we use this to access different system services, like
   *            {@link ConnectivityManager}, or {@link PackageInfo}.
   */
  public MuxDevice(Context ctx) {
    SharedPreferences sharedPreferences = ctx
        .getSharedPreferences(MUX_DEVICE_ID, Context.MODE_PRIVATE);
    deviceId = sharedPreferences.getString(MUX_DEVICE_ID, null);
    if (deviceId == null) {
      deviceId = UUID.randomUUID().toString();
      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putString(MUX_DEVICE_ID, deviceId);
      editor.commit();
    }
    contextRef = new WeakReference<>(ctx);
    try {
      PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
      appName = pi.packageName;
      appVersion = pi.versionName;
    } catch (PackageManager.NameNotFoundException e) {
      MuxLogger.d(TAG, "could not get package info");
    }
  }

  public void overwriteDeviceMetadata(String deviceName) {
    metadataDeviceName = deviceName;
  }

  @Override
  public String getHardwareArchitecture() {
    return Build.HARDWARE;
  }

  @Override
  public String getOSFamily() {
    return "Android";
  }

  @Override
  public String getOSVersion() {
    return Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")";
  }

  @Override
  public String getManufacturer() {
    return Build.MANUFACTURER;
  }

  @Override
  public String getModelName() {
    if (metadataDeviceName != null) {
      return metadataDeviceName;
    }
    return Build.MODEL;
  }

  @Override
  public String getPlayerVersion() {
    return ExoPlayerLibraryInfo.VERSION;
  }

  @Override
  public String getDeviceId() {
    return deviceId;
  }

  @Override
  public String getAppName() {
    return appName;
  }

  @Override
  public String getAppVersion() {
    return appVersion;
  }

  @Override
  public String getPluginName() {
    return BuildConfig.MUX_PLUGIN_NAME;
  }

  @Override
  public String getPluginVersion() {
    return BuildConfig.MUX_PLUGIN_VERSION;
  }

  @Override
  public String getPlayerSoftware() {
    return EXO_SOFTWARE;
  }

  /**
   * Determine the correct network connection type.
   *
   * @return the connection type name.
   */
  @Override
  public String getNetworkConnectionType() {
    // Checking internet connectivity
    Context context = contextRef.get();
    if (context == null) {
      return null;
    }
    ConnectivityManager connectivityMgr = (ConnectivityManager) context
        .getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = null;
    if (connectivityMgr != null) {
      activeNetwork = connectivityMgr.getActiveNetworkInfo();
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        NetworkCapabilities nc = connectivityMgr
            .getNetworkCapabilities(connectivityMgr.getActiveNetwork());
        if (nc == null) {
          MuxLogger.d(TAG, "ERROR: Failed to obtain NetworkCapabilities manager !!!");
          return null;
        }
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
          return CONNECTION_TYPE_WIRED;
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
          return CONNECTION_TYPE_WIFI;
        } else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
          return CONNECTION_TYPE_CELLULAR;
        } else {
          return CONNECTION_TYPE_OTHER;
        }
      } else {
        if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) {
          return CONNECTION_TYPE_WIRED;
        } else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
          return CONNECTION_TYPE_WIFI;
        } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
          return CONNECTION_TYPE_CELLULAR;
        } else {
          return CONNECTION_TYPE_OTHER;
        }
      }
    }
    return null;
  }

  @Override
  public long getElapsedRealtime() {
    return elapsedRealtime();
  }

  @Override
  public void outputLog(LogPriority logPriority, String tag, String msg) {
    switch (logPriority) {
      case ERROR:
        Log.e(tag, msg);
        break;
      case WARN:
        Log.w(tag, msg);
        break;
      case INFO:
        Log.i(tag, msg);
        break;
      case DEBUG:
        Log.d(tag, msg);
        break;
      case VERBOSE:
      default: // fall-through
        Log.v(tag, msg);
        break;
    }
  }

  /**
   * Print underlying {@link MuxStats} SDK messages on the logcat. This will only be
   * called if {@link com.mux.stats.sdk.muxstats.MuxStatsExoPlayer#enableMuxCoreDebug(boolean, boolean)} is called with first argument as true
   *
   * @param tag tag to be used.
   * @param msg message to be printed.
   */
  @Override
  public void outputLog(String tag, String msg) {
    Log.v(tag, msg);
  }
}
