/**
 * Copyright 2016 Donald Oakes
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
package com.oakesville.mythling.media;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.oakesville.mythling.R;
import com.oakesville.mythling.app.AppResources;
import com.oakesville.mythling.app.AppSettings;

import android.net.Uri;
import android.util.Log;
import io.oakesville.media.MediaSettings.MediaType;

public class PlaybackOptions {
    private static final String TAG = PlaybackOptions.class.getSimpleName();

    public static final String NETWORK_INTERNAL = "internal";
    public static final String NETWORK_EXTERNAL = "external";
    public static final String NETWORK_DOWNLOAD = "download";
    public static final String PLAYER_LIBVLC = "libvlc";
    public static final String PLAYER_ANDROID = "android";
    private static final String PLAYER_APP = "app";
    public static final String STREAM_FILE = "file";
    public static final String STREAM_HLS = "hls";
    private static final String PROPERTY_ALWAYS = "always";
    public static final String PROPERTY_DEFAULT = "default";

    private final AppSettings appSettings;
    private JSONObject defaultOptionsJson;

    public PlaybackOptions(AppSettings appSettings) {
        this.appSettings = appSettings;
    }

    public PlaybackOption getOption(MediaType mediaType, String fileType, String network) throws IOException, JSONException {
        return getOption(mediaType, fileType, network, null);
    }

    /**
     * An existing option had better match streamMode.  Example:
     *  "recordings":
     *  {
     *    "mpg":
     *    {
     *      "internal":
     *      {
     *        "file": "libvlc",
     *        "always": true
     *      },
     *      "external":
     *      {
     *        "file": "libvlc",
     *        "hls": "android"
     *      },
     *   ...
     */
    public PlaybackOption getOption(MediaType mediaType, String fileType, String network, String streamMode) throws IOException, JSONException {
        JSONObject json = new JSONObject(appSettings.getPlaybackOptionsJson());
        PlaybackOption playbackOption = getOption(mediaType, fileType, network, streamMode, json);
        if (playbackOption == null) {
            Log.d(TAG, "No saved playback option: " + mediaType.name() + "/" + fileType + "/" + network + "/" + streamMode);
            playbackOption = getDefaultOption(mediaType, fileType, network, streamMode);
        }
        return playbackOption;
    }

    private PlaybackOption getDefaultOption(MediaType mediaType, String fileType, String network, String streamMode) throws IOException, JSONException {
        if (defaultOptionsJson == null) {
            AppResources appResources = new AppResources(appSettings.getAppContext());
            String jsonString = appResources.readJsonString(R.raw.default_playback_options);
            defaultOptionsJson = new JSONObject(jsonString);
        }
        PlaybackOption defaultOption = getOption(mediaType, fileType, network, streamMode, defaultOptionsJson);
        if (defaultOption == null) {
            throw new JSONException("No match in default_playback_options.json: " + mediaType.name() + "/" + fileType + "/" + network + "/" + streamMode);
        }

        // overrule default playback option where LIBVLC not supported by CPU
        if (PLAYER_LIBVLC.equals(defaultOption.getPlayer()) && !appSettings.isCpuCompatibleWithLibVlcPlayer())
            defaultOption.setPlayer(PLAYER_ANDROID);

        return defaultOption;
    }

    /**
     * Sets the one-and-only player for the stream type in the option parameter.
     */
    public void setOption(MediaType mediaType, String fileType, String network, PlaybackOption option) throws JSONException {
        JSONObject json = new JSONObject(appSettings.getPlaybackOptionsJson());
        JSONObject mt = null;
        if (json.has(mediaType.name())) {
            mt = json.getJSONObject(mediaType.name());
        }
        else {
            mt = new JSONObject();
            json.put(mediaType.name(), mt);
        }
        JSONObject ft = null;
        if (mt.has(fileType)) {
            ft = mt.getJSONObject(fileType);
        }
        else {
            ft = new JSONObject();
            mt.put(fileType, ft);
        }
        if (option.always) {
            appSettings.setPromptForPlaybackOptions(false);
            // obliterate any existing stream type since this itself is a pref
            JSONObject net = new JSONObject();
            net.put(option.stream, option.player);
            net.put(PROPERTY_ALWAYS, true);
            ft.put(network, net);
        }
        else {
            JSONObject net;
            if (ft.has(network)) {
                net = ft.getJSONObject(network);
                if (net.has(PROPERTY_ALWAYS))
                    net.remove(PROPERTY_ALWAYS);
            }
            else {
                net = new JSONObject();
                ft.put(network, net);
            }
            net.put(option.stream, option.player);
            // set this stream option as the default
            if (net.has(PROPERTY_DEFAULT))
                net.remove(PROPERTY_DEFAULT);
            JSONObject def = new JSONObject();
            def.put(option.stream, option.player);
            net.put(PROPERTY_DEFAULT, def);
        }
        appSettings.setPlaybackOptionsJson(json.toString());
    }

    public String getDefaultPlayer() {
        return PLAYER_LIBVLC;
    }

    public void clearAlwaysDoThisSettings() throws JSONException {
        JSONObject json = new JSONObject(appSettings.getPlaybackOptionsJson());
        Iterator<String> mtKeys = json.keys();
        while (mtKeys.hasNext()) {
            JSONObject mt = json.getJSONObject(mtKeys.next());
            Iterator<String> ftKeys = mt.keys();
            while (ftKeys.hasNext()) {
                JSONObject ft = mt.getJSONObject(ftKeys.next());
                Iterator<String> netKeys = ft.keys();
                while (netKeys.hasNext()) {
                    JSONObject net = ft.getJSONObject(netKeys.next());
                    if (net.has(PROPERTY_ALWAYS))
                        net.remove(PROPERTY_ALWAYS);
                }
            }
        }
        appSettings.setPlaybackOptionsJson(json.toString());
    }

    public void clearAll() {
        appSettings.setPlaybackOptionsJson("{}");
    }

    /**
     * Passing streamMode of null should return the one-and-only mode, or the default.
     */
    private PlaybackOption getOption(MediaType mediaType, String fileType, String network, String streamMode, JSONObject json) throws JSONException {
        PlaybackOption option = null;
        JSONObject mt = null;
        if (json.has(mediaType.name()))
            mt = json.getJSONObject(mediaType.name());
        else if (json.has(PROPERTY_DEFAULT))
            mt = json.getJSONObject(PROPERTY_DEFAULT);
        if (mt != null) {
            JSONObject ft = null;
            if (mt.has(fileType))
                ft = mt.getJSONObject(fileType);
            else if (mt.has(PROPERTY_DEFAULT))
                ft = mt.getJSONObject(PROPERTY_DEFAULT);
            if (ft != null) {
                JSONObject net = null;
                if (ft.has(network))
                    net = ft.getJSONObject(network);
                else if (ft.has(PROPERTY_DEFAULT))
                    net = ft.getJSONObject(PROPERTY_DEFAULT);
                if (net != null) {
                    String player = null;
                    if (streamMode == null) {
                        if (net.has(PROPERTY_DEFAULT)) {
                            JSONObject def = net.getJSONObject(PROPERTY_DEFAULT);
                            streamMode = def.keys().next();
                            player = def.getString(streamMode);
                        }
                        else {
                            // return any match (should be only one -- ie: download in default_playback_options)
                            Iterator<String> keys = net.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                if (!key.equals(PROPERTY_ALWAYS)) {
                                    streamMode = key;
                                    player = net.getString(key);
                                }
                            }
                        }
                    }
                    else if (net.has(streamMode)) {
                        player = net.getString(streamMode);
                    }
                    if (player != null) {
                        option = new PlaybackOption(streamMode, player);
                        if (net.has(PROPERTY_ALWAYS))
                            option.setAlways(net.getBoolean(PROPERTY_ALWAYS));
                    }
                }
            }
        }

        return option;
    }

    public List<String> getMediaOptions(String player) {
        List<String> options = new ArrayList<>();
        if (player.equals(PLAYER_LIBVLC))
            options = appSettings.getVlcMediaOptions();
        else if (player.equals(PLAYER_ANDROID)) {
            if (appSettings.isProxyAndroicAuthenticatedPlayback())
                options.add(AppSettings.PROXY_ANDROID_AUTHENTICATED_PLAYBACK);
        }
        return options;
    }

    public static boolean isHls(Uri videoUri) {
        String lastPathSeg = videoUri.getLastPathSegment();
        if (lastPathSeg != null) {
            int lastDot = lastPathSeg.lastIndexOf('.');
            if (lastDot > 0)
                return "m3u8".equals(lastPathSeg.substring(lastDot + 1));
        }
        return false;
    }

    public static class PlaybackOption {

        public PlaybackOption(String stream , String player) {
            this(stream, player, false);
        }

        PlaybackOption(String stream, String player, boolean always) {
            this.stream = stream;
            this.player = player;
            this.always = always;
        }

        private String stream;
        public String getStream() { return stream; }
        public void setStream(String stream) { this.stream = stream; }
        public boolean isHls() { return STREAM_HLS.equals(stream); }

        private String player;
        public String getPlayer() { return player; }
        public void setPlayer(String player) { this.player = player; }
        public boolean isAppPlayer() { return PLAYER_APP.equals(player); }

        private boolean always;
        public boolean isAlways() { return always; }
        public void setAlways(boolean always) { this.always = always; }

        public String toString() {
            return stream + ": " + player + (always ? ", always: true" : "");
        }
    }
}
