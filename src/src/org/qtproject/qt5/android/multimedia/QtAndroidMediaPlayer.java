/****************************************************************************
**
** Copyright (C) 2016 The Qt Company Ltd.
** Contact: https://www.qt.io/licensing/
**
** This file is part of the QtMultimedia of the Qt Toolkit.
**
** $QT_BEGIN_LICENSE:LGPL$
** Commercial License Usage
** Licensees holding valid commercial Qt licenses may use this file in
** accordance with the commercial license agreement provided with the
** Software or, alternatively, in accordance with the terms contained in
** a written agreement between you and The Qt Company. For licensing terms
** and conditions see https://www.qt.io/terms-conditions. For further
** information use the contact form at https://www.qt.io/contact-us.
**
** GNU Lesser General Public License Usage
** Alternatively, this file may be used under the terms of the GNU Lesser
** General Public License version 3 as published by the Free Software
** Foundation and appearing in the file LICENSE.LGPL3 included in the
** packaging of this file. Please review the following information to
** ensure the GNU Lesser General Public License version 3 requirements
** will be met: https://www.gnu.org/licenses/lgpl-3.0.html.
**
** GNU General Public License Usage
** Alternatively, this file may be used under the terms of the GNU
** General Public License version 2.0 or (at your option) the GNU General
** Public license version 3 or any later version approved by the KDE Free
** Qt Foundation. The licenses are as published by the Free Software
** Foundation and appearing in the file LICENSE.GPL2 and LICENSE.GPL3
** included in the packaging of this file. Please review the following
** information to ensure the GNU General Public License requirements will
** be met: https://www.gnu.org/licenses/gpl-2.0.html and
** https://www.gnu.org/licenses/gpl-3.0.html.
**
** $QT_END_LICENSE$
**
****************************************************************************/

package org.qtproject.qt5.android.multimedia;

import java.io.IOException;
import java.lang.String;
import java.io.FileInputStream;

// API is level is < 9 unless marked otherwise.
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.FileDescriptor;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.view.SurfaceHolder;

// for LibVLC:
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;
import java.util.ArrayList;
import org.videolan.libvlc.util.VLCUtil;

// 26.12.2016 14:24
// Qt 5.7.1
// libVlc 1.7.5
public class QtAndroidMediaPlayer implements LibVLC.HardwareAccelerationError
{
    // Native callback functions for MediaPlayer
    native public void onErrorNative(int what, int extra, long id);
    native public void onBufferingUpdateNative(int percent, long id);
    native public void onProgressUpdateNative(int progress, long id);
    native public void onDurationChangedNative(int duration, long id);
    native public void onInfoNative(int what, int extra, long id);
    native public void onVideoSizeChangedNative(int width, int height, long id);
    native public void onStateChangedNative(int state, long id);

    private android.media.MediaPlayer mMediaPlayer = null;
    private Uri mUri = null;
    private final long mID;
    private final Context mContext;
    private boolean mMuted = false;
    private int mVolume = 100;
    private static final String TAG = "Qt MediaPlayer";
    private SurfaceHolder mSurfaceHolder = null;

    // for LibVLC:
    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayerVLC = null;
    private String mDataSource = "";
    private boolean mSurfaceAttached = false;
    private boolean mVlcModeOn = false;
    private static final String TAGVLC = "QtVlcMediaPlayer";
    private static final String mGithubURL = "http://github.com/mzafers/QtVlcMediaPlayer";

    private class State {
        final static int Uninitialized = 0x1 /* End */;
        final static int Idle = 0x2;
        final static int Preparing = 0x4;
        final static int Prepared = 0x8;
        final static int Initialized = 0x10;
        final static int Started = 0x20;
        final static int Stopped = 0x40;
        final static int Paused = 0x80;
        final static int PlaybackCompleted = 0x100;
        final static int Error = 0x200;
    }

    private volatile int mState = State.Uninitialized;

    /**
     * MediaPlayer OnErrorListener
     */
    private class MediaPlayerErrorListener
    implements android.media.MediaPlayer.OnErrorListener
    {
        @Override
        public boolean onError(final android.media.MediaPlayer mp,
                               final int what,
                               final int extra)
        {
            setState(State.Error);
            onErrorNative(what, extra, mID);
            return true;
        }

    }

    /**
     * MediaPlayer OnBufferingListener
     */
    private class MediaPlayerBufferingListener
    implements android.media.MediaPlayer.OnBufferingUpdateListener
    {
        private int mBufferPercent = -1;
        @Override
        public void onBufferingUpdate(final android.media.MediaPlayer mp,
                                      final int percent)
        {
            // Avoid updates when percent is unchanged.
            // E.g., we keep getting updates when percent == 100
            if (mBufferPercent == percent)
                return;

            onBufferingUpdateNative((mBufferPercent = percent), mID);
        }

    }

    /**
     * MediaPlayer OnCompletionListener
     */
    private class MediaPlayerCompletionListener
    implements android.media.MediaPlayer.OnCompletionListener
    {
        @Override
        public void onCompletion(final android.media.MediaPlayer mp)
        {
            setState(State.PlaybackCompleted);
        }

    }

    /**
     * MediaPlayer OnInfoListener
     */
    private class MediaPlayerInfoListener
    implements android.media.MediaPlayer.OnInfoListener
    {
        @Override
        public boolean onInfo(final android.media.MediaPlayer mp,
                              final int what,
                              final int extra)
        {
            onInfoNative(what, extra, mID);
            return true;
        }

    }

    /**
     * MediaPlayer OnPreparedListener
     */
    private class MediaPlayerPreparedListener
    implements android.media.MediaPlayer.OnPreparedListener
    {

        @Override
        public void onPrepared(final android.media.MediaPlayer mp)
        {
            setState(State.Prepared);
            onDurationChangedNative(getDuration(), mID);
        }

    }

    /**
     * MediaPlayer OnSeekCompleteListener
     */
    private class MediaPlayerSeekCompleteListener
    implements android.media.MediaPlayer.OnSeekCompleteListener
    {

        @Override
        public void onSeekComplete(final android.media.MediaPlayer mp)
        {
            onProgressUpdateNative(getCurrentPosition(), mID);
        }

    }

    /**
     * MediaPlayer OnVideoSizeChangedListener
     */
    private class MediaPlayerVideoSizeChangedListener
    implements android.media.MediaPlayer.OnVideoSizeChangedListener
    {

        @Override
        public void onVideoSizeChanged(final android.media.MediaPlayer mp,
                                       final int width,
                                       final int height)
        {
            onVideoSizeChangedNative(width, height, mID);
        }

    }

    /*************
    * Events
    *************/
    private org.videolan.libvlc.IVLCVout.Callback mIVLCVoutCallback = new MyIVLCVoutCallback();
    private org.videolan.libvlc.MediaPlayer.EventListener mPlayerListener = new MyPlayerListener(this);

    private class MyIVLCVoutCallback implements IVLCVout.Callback
    {
        public MyIVLCVoutCallback()
        {
        }
        @Override
        public void onNewLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen)
        {
            if (width == 0 || height == 0)
                return;
            onVideoSizeChangedNative(width, height, mID);
        }

        @Override
        public void onSurfacesCreated(IVLCVout vout) {

        }

        @Override
        public void onSurfacesDestroyed(IVLCVout vout) {

        }
    }

    private class MyPlayerListener implements MediaPlayer.EventListener
    {
        public MyPlayerListener(QtAndroidMediaPlayer owner)
        {
        }

        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch(event.type) {
                case MediaPlayer.Event.EndReached:
                    setState(State.PlaybackCompleted);
                    break;
                case MediaPlayer.Event.EncounteredError:
                    setState(State.Error);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void eventHardwareAccelerationError()
    {
        Log.d(TAGVLC, "eventHardwareAccelerationError");
    }
    /*************
     * Events
     *************/


     public static ArrayList<String> getLibOptions()
     {
         ArrayList<String> options = new ArrayList<String>(50);

         // parameters
         int networkCaching = 7500;
         String chroma = "YV12"; // "RV32";
         final boolean verboseMode = false;
         final boolean timeStreching = true;
         final boolean frameSkip = false;
         final String subtitlesEncoding = "";
         int deblocking = -1;


         if (chroma != null)
             chroma = chroma.equals("YV12") && !AndroidUtil.isGingerbreadOrLater() ? "" : chroma;

         try {
             deblocking = getDeblocking(deblocking);
         } catch (NumberFormatException ignored) {}


         if (networkCaching > 60000)
             networkCaching = 60000;
         else if (networkCaching < 0)
             networkCaching = 0;

         options.add(timeStreching ? "--audio-time-stretch" : "--no-audio-time-stretch");

         options.add("--avcodec-skiploopfilter");
         options.add("" + deblocking);

         //options.add("--avcodec-skip-frame");
         //options.add(frameSkip ? "2" : "0");

         //options.add("--avcodec-skip-idct");
         //options.add(frameSkip ? "2" : "0");

         options.add("--subsdec-encoding");
         options.add(subtitlesEncoding);

         //options.add("--stats");

         if (networkCaching > 0)
             options.add("--network-caching=" + networkCaching);

         options.add("--androidwindow-chroma");
         options.add(chroma != null ? chroma : "RV32");

         options.add("--audio-resampler");
         options.add(getResampler());

         options.add("--aout=opensles");
         options.add(verboseMode ? "-vvv" : "-vv");

         return options;
     }

    private static String getResampler()
    {
        final VLCUtil.MachineSpecs m = VLCUtil.getMachineSpecs();
        return m.processors > 2 ? "soxr" : "ugly";
    }

    private static int getDeblocking(int deblocking)
    {
        int ret = deblocking;
        if (deblocking < 0) {
            /**
             * Set some reasonable sDeblocking defaults:
             *
             * Skip all (4) for armv6 and MIPS by default
             * Skip non-ref (1) for all armv7 more than 1.2 Ghz and more than 2 cores
             * Skip non-key (3) for all devices that don't meet anything above
             */
            VLCUtil.MachineSpecs m = VLCUtil.getMachineSpecs();
            if (m == null)
                return ret;
            if ((m.hasArmV6 && !(m.hasArmV7)) || m.hasMips)
                ret = 4;
            else if (m.frequency >= 1200 && m.processors > 2)
                ret = 1;
            else if (m.bogoMIPS >= 1200 && m.processors > 2) {
                ret = 1;
                Log.d(TAG, "Used bogoMIPS due to lack of frequency info");
            } else
                ret = 3;
        } else if (deblocking > 4) { // sanity check
            ret = 3;
        }
        return ret;
    }


    public QtAndroidMediaPlayer(final Context context, final long id)
    {
        Log.d(TAGVLC, "|--------------------|");
        Log.d(TAGVLC, "|                    |");
        Log.d(TAGVLC, "|                    |");
        Log.d(TAGVLC, mGithubURL);
        Log.d(TAGVLC, "|                    |");
        Log.d(TAGVLC, "|                    |");
        Log.d(TAGVLC, "|--------------------|");

        mID = id;
        mContext = context;
    }

    private void setState(int state)
    {
        if (mState == state)
            return;

        mState = state;

        onStateChangedNative(mState, mID);
    }

    private void initVLC()
    {
        if(mLibVLC == null)
        {
            boolean hasCompatibleCPU = VLCUtil.hasCompatibleCPU(mContext);

            if(hasCompatibleCPU == false)
                return;

            ArrayList<String> options = getLibOptions();
            mLibVLC = new LibVLC(options);
            mLibVLC.setOnHardwareAccelerationError(QtAndroidMediaPlayer.this);
            mMediaPlayerVLC = new MediaPlayer(mLibVLC);
            setState(State.Idle);
        }

        setVout();
        setState(State.Idle);
    }

    private void setVout()
    {
        if(mSurfaceAttached == true)
            return;

        if(mSurfaceHolder == null)
            return;

        if(mLibVLC == null)
            return;

        if(mMediaPlayerVLC == null)
            return;

        final IVLCVout vout = mMediaPlayerVLC.getVLCVout();
        vout.detachViews();
        vout.setVideoSurface(mSurfaceHolder.getSurface(), mSurfaceHolder);
        vout.addCallback(mIVLCVoutCallback);
        vout.attachViews();
        mSurfaceAttached = true;
    }

    private void releaseVout()
    {
        if(mMediaPlayerVLC == null)
            return;
        if(mSurfaceAttached == false)
            return;

        final IVLCVout vout = mMediaPlayerVLC.getVLCVout();
        vout.removeCallback(mIVLCVoutCallback);
        vout.detachViews();
        mSurfaceAttached = false;
    }

    private void init()
    {
        if (mMediaPlayer == null) {
            mMediaPlayer = new android.media.MediaPlayer();
            setState(State.Idle);
            // Make sure the new media player has the volume that was set on the QMediaPlayer
            setVolumeHelper(mMuted ? 0 : mVolume);
        }
    }


    public void start()
    {
        if ((mState & (State.Prepared
                       | State.Started
                       | State.Paused
                       | State.PlaybackCompleted)) == 0) {
            return;
        }

        if(mVlcModeOn == true)
        {
            Media m = new Media(mLibVLC, mUri);
            mMediaPlayerVLC.setMedia(m);
            m.release();
            mMediaPlayerVLC.setEventListener(mPlayerListener);
            mMediaPlayerVLC.play();
            setState(State.Started);
        }
        else
        {
            try {
                mMediaPlayer.start();
                setState(State.Started);
            } catch (final IllegalStateException e) {
                Log.d(TAG, "" + e.getMessage());
            }
        }
    }


    public void pause()
    {
        if ((mState & (State.Started | State.Paused | State.PlaybackCompleted)) == 0)
            return;

            if(mVlcModeOn == true)
            {
                mMediaPlayerVLC.pause();
                setState(State.Paused);
            }
            else
            {
                try {
                    mMediaPlayer.pause();
                    setState(State.Paused);
                } catch (final IllegalStateException e) {
                    Log.d(TAG, "" + e.getMessage());
                }
            }
    }


    public void stop()
    {
        if ((mState & (State.Prepared
                       | State.Started
                       | State.Stopped
                       | State.Paused
                       | State.PlaybackCompleted)) == 0) {
            return;
        }

        if(mVlcModeOn == true)
        {
            final Media media = mMediaPlayerVLC.getMedia();

            if(media != null)
            {
                mMediaPlayerVLC.stop();
                mMediaPlayerVLC.setEventListener(null);
                mMediaPlayerVLC.setMedia(null);
                media.release();
            }

            setState(State.Stopped);
        }
        else
        {
            try {
                mMediaPlayer.stop();
                setState(State.Stopped);
            } catch (final IllegalStateException e) {
                Log.d(TAG, "" + e.getMessage());
            }
        }
    }


    public void seekTo(final int msec)
    {
        if ((mState & (State.Prepared
                       | State.Started
                       | State.Paused
                       | State.PlaybackCompleted)) == 0) {
            return;
        }

        if(mVlcModeOn == true)
        {
            mMediaPlayerVLC.setPosition((float)msec);
        }
        else
        {
            try {
                mMediaPlayer.seekTo(msec);
            } catch (final IllegalStateException e) {
                Log.d(TAG, "" + e.getMessage());
            }
        }
    }


    public boolean isPlaying()
    {
        boolean playing = false;
        if ((mState & (State.Idle
                       | State.Initialized
                       | State.Prepared
                       | State.Started
                       | State.Paused
                       | State.Stopped
                       | State.PlaybackCompleted)) == 0) {
            return playing;
        }

        if(mVlcModeOn == true)
        {
            playing = mMediaPlayerVLC.isPlaying();
        }
        else
        {
            try {
                playing = mMediaPlayer.isPlaying();
            } catch (final IllegalStateException e) {
                Log.d(TAG, "" + e.getMessage());
            }
        }

        return playing;
    }

    public void prepareAsync()
    {
        if ((mState & (State.Initialized | State.Stopped)) == 0)
           return;

           if(mVlcModeOn == true)
           {
               //setState(State.Preparing);
               setState(State.Prepared);
               onDurationChangedNative(getDuration(), mID);
           }
           else
           {
               try {
                   mMediaPlayer.prepareAsync();
                   setState(State.Preparing);
               } catch (final IllegalStateException e) {
                   Log.d(TAG, "" + e.getMessage());
               }
           }
    }

    public String decodeUrl(String url)
    {
        int i = -1;
        if(url.equals("") == false)
        {
            i = url.lastIndexOf("???");
            if(i >= 0)
            {
                String str = url.substring(i);
                if(str.equals("???vlc") == true)
                {
                    mVlcModeOn = true;
                    url = url.substring(0, i);
                }
            }

            i = url.lastIndexOf("???");
            if(i >= 0)
            {
                String str = url.substring(i);
                if(str.equals("???replaceRtspToHttp") == true)
                {
                    url = url.substring(0, i);
                    url = url.substring(7);
                    url = "http://" + url;
                }
            }

        }
        return url;
    }

    public void setDataSource(final String path)
    {
        String pathVLC = decodeUrl(path);

        if(mVlcModeOn == true)
        {
            mDataSource = pathVLC;
            mUri = Uri.parse(mDataSource);

            if(mSurfaceHolder != null)
                 initVLC();

            //setVout();
            setState(State.Initialized);
        }
        else
        {
            if ((mState & State.Uninitialized) != 0)
                init();

            if ((mState & State.Idle) == 0)
               return;

            mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayerBufferingListener());
            mMediaPlayer.setOnCompletionListener(new MediaPlayerCompletionListener());
            mMediaPlayer.setOnInfoListener(new MediaPlayerInfoListener());
            mMediaPlayer.setOnSeekCompleteListener(new MediaPlayerSeekCompleteListener());
            mMediaPlayer.setOnVideoSizeChangedListener(new MediaPlayerVideoSizeChangedListener());
            mMediaPlayer.setOnErrorListener(new MediaPlayerErrorListener());
            mMediaPlayer.setOnPreparedListener(new MediaPlayerPreparedListener());

            if (mSurfaceHolder != null)
                mMediaPlayer.setDisplay(mSurfaceHolder);

            AssetFileDescriptor afd = null;
            FileInputStream fis = null;
            try {
                mUri = Uri.parse(path);
                final boolean inAssets = (mUri.getScheme().compareTo("assets") == 0);
                if (inAssets) {
                    final String asset = mUri.getPath().substring(1 /* Remove first '/' */);
                    final AssetManager am = mContext.getAssets();
                    afd = am.openFd(asset);
                    final long offset = afd.getStartOffset();
                    final long length = afd.getLength();
                    FileDescriptor fd = afd.getFileDescriptor();
                    mMediaPlayer.setDataSource(fd, offset, length);
                } else if (mUri.getScheme().compareTo("file") == 0) {
                    fis = new FileInputStream(mUri.getPath());
                    FileDescriptor fd = fis.getFD();
                    mMediaPlayer.setDataSource(fd);
                } else {
                    mMediaPlayer.setDataSource(mContext, mUri);
                }
                setState(State.Initialized);
            } catch (final IOException e) {
                Log.d(TAG, "" + e.getMessage());
            } catch (final IllegalArgumentException e) {
                Log.d(TAG, "" + e.getMessage());
            } catch (final SecurityException e) {
                Log.d(TAG, "" + e.getMessage());
            } catch (final IllegalStateException e) {
                Log.d(TAG, "" + e.getMessage());
            } catch (final NullPointerException e) {
                Log.d(TAG, "" + e.getMessage());
            } finally {
                try {
                   if (afd != null)
                       afd.close();
                   if (fis != null)
                       fis.close();
                } catch (final IOException ioe) { /* Ignore... */ }

                if ((mState & State.Initialized) == 0) {
                    setState(State.Error);
                    onErrorNative(android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN,
                                  -1004 /*MEDIA_ERROR_IO*/,
                                  mID);
                    return;
                }
            }
        }


    }


    public int getCurrentPosition()
    {
        int currentPosition = 0;
        if ((mState & (State.Idle
                       | State.Initialized
                       | State.Prepared
                       | State.Started
                       | State.Paused
                       | State.Stopped
                       | State.PlaybackCompleted)) == 0) {
            return currentPosition;
        }

         if(mVlcModeOn == true)
         {
             currentPosition = (int)mMediaPlayerVLC.getPosition();
         }
         else
         {
             try {
                 currentPosition = mMediaPlayer.getCurrentPosition();
             } catch (final IllegalStateException e) {
                 Log.d(TAG, "" + e.getMessage());
             }
         }

        return currentPosition;
    }


    public int getDuration()
    {
        int duration = 0;
        if ((mState & (State.Prepared
                       | State.Started
                       | State.Paused
                       | State.Stopped
                       | State.PlaybackCompleted)) == 0) {
            return duration;
        }

         if(mVlcModeOn == true)
         {
             duration = (int)mMediaPlayerVLC.getLength();
         }
         else
         {
             try {
                 duration = mMediaPlayer.getDuration();
             } catch (final IllegalStateException e) {
                 Log.d(TAG, "" + e.getMessage());
             }
         }

        return duration;
    }

   private float adjustVolume(final int volume)
   {
       if (volume < 1)
           return 0.0f;

       if (volume > 98)
           return 1.0f;

       return (float) (1-(Math.log(100-volume)/Math.log(100)));
   }

   public void setVolume(int volume)
   {
       if (volume < 0)
           volume = 0;

       if (volume > 100)
           volume = 100;

       mVolume = volume;

       if (!mMuted)
           setVolumeHelper(mVolume);
   }

   private void setVolumeHelper(int volume)
   {
       if ((mState & (State.Idle
                      | State.Initialized
                      | State.Stopped
                      | State.Prepared
                      | State.Started
                      | State.Paused
                      | State.PlaybackCompleted)) == 0) {
           return;
       }

       try {
           float newVolume = adjustVolume(volume);
           mMediaPlayer.setVolume(newVolume, newVolume);
       } catch (final IllegalStateException e) {
           Log.d(TAG, "" + e.getMessage());
       }
   }

   public SurfaceHolder display()
   {
       return mSurfaceHolder;
   }

    public void setDisplay(SurfaceHolder sh)
    {
        mSurfaceHolder = sh;

        if(mVlcModeOn == true)
        {
            if(mMediaPlayerVLC == null)
                initVLC();
        }
         else
         {
             if ((mState & State.Uninitialized) != 0)
                 return;

             mMediaPlayer.setDisplay(mSurfaceHolder);
         }
    }


   public int getVolume()
   {
       return mVolume;
   }

    public void mute(final boolean mute)
    {
        mMuted = mute;
        setVolumeHelper(mute ? 0 : mVolume);
    }

    public boolean isMuted()
    {
        return mMuted;
    }


    public void reset()
    {
        if ((mState & (State.Idle
                       | State.Initialized
                       | State.Prepared
                       | State.Started
                       | State.Paused
                       | State.Stopped
                       | State.PlaybackCompleted
                       | State.Error)) == 0) {
            return;
        }
        if(mVlcModeOn == false)
            mMediaPlayer.reset();

        setState(State.Idle);
    }

    public void release()
    {
        if(mVlcModeOn == true)
        {
            if(mLibVLC != null)
            {
                mMediaPlayerVLC.stop();
                releaseVout();
                mMediaPlayerVLC.release();
                mLibVLC.release();
                mLibVLC = null;
            }
        }
        else
        {
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }

        setState(State.Uninitialized);
    }

}
