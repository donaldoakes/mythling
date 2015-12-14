/**
 * Copyright 2015 Donald Oakes
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
package com.oakesville.mythling;

import java.net.URLDecoder;

import com.oakesville.mythling.app.AppSettings;
import com.oakesville.mythling.app.Localizer;
import com.oakesville.mythling.media.MediaPlayer;
import com.oakesville.mythling.media.MediaPlayer.MediaPlayerEvent;
import com.oakesville.mythling.media.MediaPlayer.MediaPlayerEventListener;
import com.oakesville.mythling.media.MediaPlayer.MediaPlayerLayoutChangeListener;
import com.oakesville.mythling.util.Reporter;
import com.oakesville.mythling.util.TextBuilder;
import com.oakesville.mythling.vlc.VlcMediaPlayer;

import android.app.Activity;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class VideoPlayerActivity extends Activity {

    public static final String EXTRA_ITEM_LENGTH = "com.oakesville.mythling.EXTRA_ITEM_LENGTH";

    private static final String TAG = VideoPlayerActivity.class.getSimpleName();

    private AppSettings appSettings;
    private Uri videoUri;
    private int itemLength;

    private ProgressBar progressBar;
    private SurfaceView surface;
    // private LibVLC libvlc;
    private MediaPlayer mediaPlayer;
    private int videoWidth;
    private int videoHeight;

    // seek
    private TextView curPosText;
    private SeekBar seekBar;
    private ImageButton playCtrl;
    private ImageButton pauseCtrl;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player);

        appSettings = new AppSettings(getApplicationContext());
        if (!Localizer.isInitialized())
            Localizer.initialize(appSettings);

        surface = (SurfaceView) findViewById(R.id.surface);

        createProgressBar();

        try {
            videoUri = Uri.parse(URLDecoder.decode(getIntent().getDataString(), "UTF-8"));
            itemLength = getIntent().getExtras().getInt(EXTRA_ITEM_LENGTH);

            if (itemLength > 0) {
                curPosText = (TextView) findViewById(R.id.current_pos);
                curPosText.setText(new TextBuilder().appendDuration(0).toString());
                TextView totalLenText = (TextView) findViewById(R.id.total_len);
                totalLenText.setText(new TextBuilder().appendDuration(itemLength).toString());

                seekBar = (SeekBar) findViewById(R.id.player_seek);
                seekBar.setMax(itemLength); // max is length in seconds
                seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) {
                            mediaPlayer.setSeconds(progress);
                            curPosText.setText(new TextBuilder().appendDuration(progress).toString());
                        }
                    }
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
            }

            playCtrl = (ImageButton) findViewById(R.id.ctrl_play);
            playCtrl.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    mediaPlayer.play();
                    showPause();
                }
            });

            pauseCtrl = (ImageButton) findViewById(R.id.ctrl_pause);
            pauseCtrl.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    mediaPlayer.pause();
                    showPlay();
                }
            });

            ImageButton fastBack = (ImageButton) findViewById(R.id.ctrl_jump_back);
            fastBack.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    seek(-600);
                }
            });

            ImageButton skipBack = (ImageButton) findViewById(R.id.ctrl_skip_back);
            skipBack.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    seek(-10);
                }
            });

            ImageButton rewind = (ImageButton) findViewById(R.id.ctrl_rewind);
            rewind.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    int playRate = mediaPlayer.stepUpRewind();
                    showPlay();
                    Toast.makeText(getApplicationContext(), "<< " + (-playRate) + "x", Toast.LENGTH_SHORT).show();
                }
            });

            ImageButton ffwd = (ImageButton) findViewById(R.id.ctrl_ffwd);
            ffwd.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    int playRate = mediaPlayer.stepUpFastForward();
                    if (playRate == 1)
                        showPause();
                    else
                        showPlay();
                    Toast.makeText(getApplicationContext(), ">> " + playRate + "x", Toast.LENGTH_SHORT).show();
                }
            });

            ImageButton skipFwd = (ImageButton) findViewById(R.id.ctrl_skip_fwd);
            skipFwd.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    seek(+30);
                }
            });

            ImageButton fastFwd = (ImageButton) findViewById(R.id.ctrl_fast_fwd);
            fastFwd.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    seek(+600);
                }
            });
        }
        catch (Exception ex) {
            progressBar.setVisibility(View.GONE);
            Log.e(TAG, ex.getMessage(), ex);
            if (appSettings.isErrorReportingEnabled())
                new Reporter(ex).send();
            Toast.makeText(getApplicationContext(), "Error: " + ex.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void showPlay() {
        pauseCtrl.setVisibility(View.GONE);
        playCtrl.setVisibility(View.VISIBLE);
    }

    private void showPause() {
        playCtrl.setVisibility(View.GONE);
        pauseCtrl.setVisibility(View.VISIBLE);
    }

    private void seek(int delta) {
        int newPos = mediaPlayer.seek(delta);
        curPosText.setText(new TextBuilder().appendDuration(newPos).toString());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSize(videoWidth, videoHeight);
    }

    @Override
    protected void onResume() {
        super.onResume();
        createPlayer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    private void setSize(int width, int height) {
        videoWidth = width;
        videoHeight = height;
        if (videoWidth * videoHeight <= 1)
            return;
        if (surface == null || surface.getHolder() == null)
            return;

        // get screen size
        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into account, so correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) videoWidth / (float) videoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        // force surface buffer size
        surface.getHolder().setFixedSize(videoWidth, videoHeight);

        // set display size
        LayoutParams lp = surface.getLayoutParams();
        lp.width = w;
        lp.height = h;
        surface.setLayoutParams(lp);
        surface.invalidate();
    }

    private void createPlayer() {
        releasePlayer();
        try {
            Log.i(TAG, "Playing video: " + videoUri);

            mediaPlayer = new VlcMediaPlayer(surface, null); // TODO subtitles
            mediaPlayer.setLayoutChangeListener(new MediaPlayerLayoutChangeListener() {
                public void onLayoutChange(int width, int height) {
                    if (width * height == 0)
                        return;
                    // store video size
                    videoWidth = width;
                    videoHeight = height;
                    setSize(videoWidth, videoHeight);
                }
            });
            mediaPlayer.setMediaPlayerEventListener(new MediaPlayerEventListener() {
                public void onEvent(MediaPlayerEvent event) {
                    if (event == MediaPlayerEvent.playing) {
                        progressBar.setVisibility(View.GONE);
                        seekBarHandler.postDelayed(updateSeekBarAction, 100);
                    }
                    else if (event == MediaPlayerEvent.end) {
                        finish();
                    }
                    else if (event == MediaPlayerEvent.error) {
                        String msg = "Media player error";
                        Log.e(TAG, msg);
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                        if (appSettings.isErrorReportingEnabled())
                            new Reporter(msg).send();
                        finish();
                    }
                }
            });

            progressBar.setVisibility(View.VISIBLE);
            mediaPlayer.playMedia(videoUri, itemLength);
        }
        catch (Exception ex) {
            Log.e(TAG, ex.getMessage(), ex);
            progressBar.setVisibility(View.GONE);
            if (appSettings.isErrorReportingEnabled())
                new Reporter(ex).send();
            Toast.makeText(getApplicationContext(), "Error creating player: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null)
            mediaPlayer.doRelease();

        videoWidth = 0;
        videoHeight = 0;
    }

    private Handler seekBarHandler = new Handler();
    private Runnable updateSeekBarAction = new Runnable() {
        public void run() {
            if (!mediaPlayer.isReleased()) {
                int curPos = mediaPlayer.getSeconds();
                curPosText.setText(new TextBuilder().appendDuration(curPos).toString());
                seekBar.setProgress(curPos);
                seekBarHandler.postDelayed(this, 100);
            }
        }
    };

    protected ProgressBar createProgressBar() {
        progressBar = (ProgressBar) findViewById(R.id.progress);
        progressBar.setScaleX(0.20f);
        progressBar.setScaleY(0.20f);
        return progressBar;
    }
}
