/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.JsonReader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.demo.Sample.DrmInfo;
import com.google.android.exoplayer2.demo.Sample.PlaylistSample;
import com.google.android.exoplayer2.demo.Sample.UriSample;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** An activity for selecting from a list of media samples. */
public class SampleChooserActivity extends AppCompatActivity
    implements DownloadTracker.Listener, OnChildClickListener {

  private static final String TAG = "SampleChooserActivity";

  private boolean useExtensionRenderers;
  private DownloadTracker downloadTracker;
  private SampleAdapter sampleAdapter;
  private MenuItem preferExtensionDecodersMenuItem;
  private MenuItem randomAbrMenuItem;
  private MenuItem tunnelingMenuItem;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);
    sampleAdapter = new SampleAdapter();
    ExpandableListView sampleListView = findViewById(R.id.sample_list);
    sampleListView.setAdapter(sampleAdapter);
    sampleListView.setOnChildClickListener(this);

    Intent intent = getIntent();
    String dataUri = intent.getDataString();
    String[] uris;
    if (dataUri != null) {
      uris = new String[] {dataUri};
    } else {
      ArrayList<String> uriList = new ArrayList<>();
      AssetManager assetManager = getAssets();
      try {
        for (String asset : assetManager.list("")) {
          if (asset.endsWith(".exolist.json")) {
            uriList.add("asset:///" + asset);
          }
        }
      } catch (IOException e) {
        Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
            .show();
      }
      uris = new String[uriList.size()];
      uriList.toArray(uris);
      Arrays.sort(uris);
    }

    DemoApplication application = (DemoApplication) getApplication();
    useExtensionRenderers = application.useExtensionRenderers();
    downloadTracker = application.getDownloadTracker();
    SampleListLoader loaderTask = new SampleListLoader();
    loaderTask.execute(uris);

    // Start the download service if it should be running but it's not currently.
    // Starting the service in the foreground causes notification flicker if there is no scheduled
    // action. Starting it in the background throws an exception if the app is in the background too
    // (e.g. if device screen is locked).
    try {
      DownloadService.start(this, DemoDownloadService.class);
    } catch (IllegalStateException e) {
      DownloadService.startForeground(this, DemoDownloadService.class);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.sample_chooser_menu, menu);
    preferExtensionDecodersMenuItem = menu.findItem(R.id.prefer_extension_decoders);
    preferExtensionDecodersMenuItem.setVisible(useExtensionRenderers);
    randomAbrMenuItem = menu.findItem(R.id.random_abr);
    tunnelingMenuItem = menu.findItem(R.id.tunneling);
    if (Util.SDK_INT < 21) {
      tunnelingMenuItem.setEnabled(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    item.setChecked(!item.isChecked());
    return true;
  }

  @Override
  public void onStart() {
    super.onStart();
    downloadTracker.addListener(this);
    sampleAdapter.notifyDataSetChanged();
  }

  @Override
  public void onStop() {
    downloadTracker.removeListener(this);
    super.onStop();
  }

  @Override
  public void onDownloadsChanged() {
    sampleAdapter.notifyDataSetChanged();
  }

  private void onSampleGroups(final List<SampleGroup> groups, boolean sawError) {
    if (sawError) {
      Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
          .show();
    }
    sampleAdapter.setSampleGroups(groups);
  }

  @Override
  public boolean onChildClick(
      ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
    Sample sample = (Sample) view.getTag();
    Intent intent = new Intent(this, PlayerActivity.class);
    intent.putExtra(
        PlayerActivity.PREFER_EXTENSION_DECODERS_EXTRA,
        isNonNullAndChecked(preferExtensionDecodersMenuItem));
    String abrAlgorithm =
        isNonNullAndChecked(randomAbrMenuItem)
            ? PlayerActivity.ABR_ALGORITHM_RANDOM
            : PlayerActivity.ABR_ALGORITHM_DEFAULT;
    intent.putExtra(PlayerActivity.ABR_ALGORITHM_EXTRA, abrAlgorithm);
    intent.putExtra(PlayerActivity.TUNNELING_EXTRA, isNonNullAndChecked(tunnelingMenuItem));
    sample.addToIntent(intent);
    startActivity(intent);
    return true;
  }

  private void onSampleDownloadButtonClicked(Sample sample) {
    int downloadUnsupportedStringId = getDownloadUnsupportedStringId(sample);
    if (downloadUnsupportedStringId != 0) {
      Toast.makeText(getApplicationContext(), downloadUnsupportedStringId, Toast.LENGTH_LONG)
          .show();
    } else {
      UriSample uriSample = (UriSample) sample;
      RenderersFactory renderersFactory =
          ((DemoApplication) getApplication())
              .buildRenderersFactory(isNonNullAndChecked(preferExtensionDecodersMenuItem));
      downloadTracker.toggleDownload(
          getSupportFragmentManager(),
          sample.name,
          uriSample.uri,
          uriSample.extension,
          renderersFactory);
    }
  }

  private int getDownloadUnsupportedStringId(Sample sample) {
    if (sample instanceof PlaylistSample) {
      return R.string.download_playlist_unsupported;
    }
    UriSample uriSample = (UriSample) sample;
    if (uriSample.drmInfo != null) {
      return R.string.download_drm_unsupported;
    }
    if (uriSample.isLive) {
      return R.string.download_live_unsupported;
    }
    if (uriSample.adTagUri != null) {
      return R.string.download_ads_unsupported;
    }
    String scheme = uriSample.uri.getScheme();
    if (!("http".equals(scheme) || "https".equals(scheme))) {
      return R.string.download_scheme_unsupported;
    }
    return 0;
  }

  private static boolean isNonNullAndChecked(@Nullable MenuItem menuItem) {
    // Temporary workaround for layouts that do not inflate the options menu.
    return menuItem != null && menuItem.isChecked();
  }

  private final class SampleListLoader extends AsyncTask<String, Void, List<SampleGroup>> {

    private boolean sawError;

    @Override
    protected List<SampleGroup> doInBackground(String... uris) {
      List<SampleGroup> result = new ArrayList<>();
      Context context = getApplicationContext();
      String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
      DataSource dataSource =
          new DefaultDataSource(context, userAgent, /* allowCrossProtocolRedirects= */ false);
      for (String uri : uris) {
        DataSpec dataSpec = new DataSpec(Uri.parse(uri));
        InputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
        try {
          readSampleGroups(new JsonReader(new InputStreamReader(inputStream, "UTF-8")), result);
        } catch (Exception e) {
          Log.e(TAG, "Error loading sample list: " + uri, e);
          sawError = true;
        } finally {
          Util.closeQuietly(dataSource);
        }
      }
      return result;
    }

    @Override
    protected void onPostExecute(List<SampleGroup> result) {
      onSampleGroups(result, sawError);
    }

    private void readSampleGroups(JsonReader reader, List<SampleGroup> groups) throws IOException {
      reader.beginArray();
      while (reader.hasNext()) {
        readSampleGroup(reader, groups);
      }
      reader.endArray();
    }

    private void readSampleGroup(JsonReader reader, List<SampleGroup> groups) throws IOException {
      String groupName = "";
      ArrayList<Sample> samples = new ArrayList<>();

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            groupName = reader.nextString();
            break;
          case "samples":
            reader.beginArray();
            while (reader.hasNext()) {
              samples.add(readEntry(reader, false));
            }
            reader.endArray();
            break;
          case "_comment":
            reader.nextString(); // Ignore.
            break;
          default:
            throw new ParserException("Unsupported name: " + name);
        }
      }
      reader.endObject();

      SampleGroup group = getGroup(groupName, groups);
      group.samples.addAll(samples);
    }

    private Sample readEntry(JsonReader reader, boolean insidePlaylist) throws IOException {
      String sampleName = null;
      Uri uri = null;
      String extension = null;
      boolean isLive = false;
      String drmScheme = null;
      String drmLicenseUrl = null;
      String[] drmKeyRequestProperties = null;
      boolean drmMultiSession = false;
      ArrayList<UriSample> playlistSamples = null;
      String adTagUri = null;
      String sphericalStereoMode = null;
      List<Sample.SubtitleInfo> subtitleInfos = new ArrayList<>();
      Uri subtitleUri = null;
      String subtitleMimeType = null;
      String subtitleLanguage = null;

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            sampleName = reader.nextString();
            break;
          case "uri":
            uri = Uri.parse(reader.nextString());
            break;
          case "extension":
            extension = reader.nextString();
            break;
          case "drm_scheme":
            drmScheme = reader.nextString();
            break;
          case "is_live":
            isLive = reader.nextBoolean();
            break;
          case "drm_license_url":
            drmLicenseUrl = reader.nextString();
            break;
          case "drm_key_request_properties":
            ArrayList<String> drmKeyRequestPropertiesList = new ArrayList<>();
            reader.beginObject();
            while (reader.hasNext()) {
              drmKeyRequestPropertiesList.add(reader.nextName());
              drmKeyRequestPropertiesList.add(reader.nextString());
            }
            reader.endObject();
            drmKeyRequestProperties = drmKeyRequestPropertiesList.toArray(new String[0]);
            break;
          case "drm_multi_session":
            drmMultiSession = reader.nextBoolean();
            break;
          case "playlist":
            Assertions.checkState(!insidePlaylist, "Invalid nesting of playlists");
            playlistSamples = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
              playlistSamples.add((UriSample) readEntry(reader, /* insidePlaylist= */ true));
            }
            reader.endArray();
            break;
          case "ad_tag_uri":
            adTagUri = reader.nextString();
            break;
          case "spherical_stereo_mode":
            Assertions.checkState(
                !insidePlaylist, "Invalid attribute on nested item: spherical_stereo_mode");
            sphericalStereoMode = reader.nextString();
            break;
          case "subtitle_uri":
            subtitleUri = Uri.parse(reader.nextString());
            break;
          case "subtitle_mime_type":
            subtitleMimeType = reader.nextString();
            break;
          case "subtitle_language":
            subtitleLanguage = reader.nextString();
            break;
          default:
            throw new ParserException("Unsupported attribute name: " + name);
        }
      }
      reader.endObject();
      DrmInfo drmInfo =
          drmScheme == null
              ? null
              : new DrmInfo(
                  Util.getDrmUuid(drmScheme),
                  drmLicenseUrl,
                  drmKeyRequestProperties,
                  drmMultiSession);
      Sample.SubtitleInfo subtitleInfo =
          subtitleUri == null
              ? null
              : new Sample.SubtitleInfo(
                  subtitleUri,
                  Assertions.checkNotNull(
                      subtitleMimeType, "subtitle_mime_type is required if subtitle_uri is set."),
                  subtitleLanguage);
      if (playlistSamples != null) {
        UriSample[] playlistSamplesArray = playlistSamples.toArray(new UriSample[0]);
        return new PlaylistSample(sampleName, playlistSamplesArray);
      } else {
        return new UriSample(
            sampleName,
            uri,
            extension,
            isLive,
            drmInfo,
            adTagUri != null ? Uri.parse(adTagUri) : null,
            sphericalStereoMode,
            subtitleInfo);
      }
    }

    private SampleGroup getGroup(String groupName, List<SampleGroup> groups) {
      for (int i = 0; i < groups.size(); i++) {
        if (Util.areEqual(groupName, groups.get(i).title)) {
          return groups.get(i);
        }
      }
      SampleGroup group = new SampleGroup(groupName);
      groups.add(group);
      return group;
    }

  }

  private final class SampleAdapter extends BaseExpandableListAdapter implements OnClickListener {

    private List<SampleGroup> sampleGroups;

    public SampleAdapter() {
      sampleGroups = Collections.emptyList();
    }

    public void setSampleGroups(List<SampleGroup> sampleGroups) {
      this.sampleGroups = sampleGroups;
      notifyDataSetChanged();
    }

    @Override
    public Sample getChild(int groupPosition, int childPosition) {
      return getGroup(groupPosition).samples.get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
      return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
        View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view = getLayoutInflater().inflate(R.layout.sample_list_item, parent, false);
        View downloadButton = view.findViewById(R.id.download_button);
        downloadButton.setOnClickListener(this);
        downloadButton.setFocusable(false);
      }
      initializeChildView(view, getChild(groupPosition, childPosition));
      return view;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
      return getGroup(groupPosition).samples.size();
    }

    @Override
    public SampleGroup getGroup(int groupPosition) {
      return sampleGroups.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
      return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
        ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view =
            getLayoutInflater()
                .inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
      }
      ((TextView) view).setText(getGroup(groupPosition).title);
      return view;
    }

    @Override
    public int getGroupCount() {
      return sampleGroups.size();
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
      return true;
    }

    @Override
    public void onClick(View view) {
      onSampleDownloadButtonClicked((Sample) view.getTag());
    }

    private void initializeChildView(View view, Sample sample) {
      view.setTag(sample);
      TextView sampleTitle = view.findViewById(R.id.sample_title);
      sampleTitle.setText(sample.name);

      boolean canDownload = getDownloadUnsupportedStringId(sample) == 0;
      boolean isDownloaded = canDownload && downloadTracker.isDownloaded(((UriSample) sample).uri);
      ImageButton downloadButton = view.findViewById(R.id.download_button);
      downloadButton.setTag(sample);
      downloadButton.setColorFilter(
          canDownload ? (isDownloaded ? 0xFF42A5F5 : 0xFFBDBDBD) : 0xFF666666);
      downloadButton.setImageResource(
          isDownloaded ? R.drawable.ic_download_done : R.drawable.ic_download);
    }
  }

  private static final class SampleGroup {

    public final String title;
    public final List<Sample> samples;

    public SampleGroup(String title) {
      this.title = title;
      this.samples = new ArrayList<>();
    }

  }
}
