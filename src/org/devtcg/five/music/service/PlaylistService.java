/*
 * $Id$
 *
 * Copyright (C) 2008 Josh Guilfoyle <jasta@devtcg.org>
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package org.devtcg.five.music.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.devtcg.five.R;
import org.devtcg.five.music.activity.Player;
import org.devtcg.five.music.util.Song;
import org.devtcg.five.music.util.streaming.DownloadManager;
import org.devtcg.five.music.util.streaming.StreamMediaPlayer;
import org.devtcg.five.music.util.streaming.TailStream;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.Sources;
import org.devtcg.five.service.ICacheService;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;

public class PlaylistService extends Service implements ServiceConnection,
  MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnErrorListener,
  MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener
{
	public static final String TAG = "PlaylistService";
	
	private static final String STATE_FILE = "playlist_state";
	private static final int STATE_FILE_FORMAT = 3;

	/* Lock allowing us to guarantee the presence of the cache service
	 * on demand. */
	final Object mCacheLock = new Object();

	/* Lock synchronizing resource access from binder threads.  This is more
	 * of a hint than a rule as we know that only one thread will be making
	 * changes to the playlist state at any time. */
	final Object mBinderLock = new Object();

	ICacheService mCache = null;

	StreamMediaPlayer mPlayer = null;
	NotificationManager mNM = null;
	private static final int NOTIF_PLAYING = 0;

	final List<Long> mPlaylist =
	  Collections.synchronizedList(new ArrayList<Long>(50));

	volatile int mPosition = -1;
	volatile boolean mPlaying = false;
	volatile boolean mPaused = false;
	volatile boolean mPrepared = false;
	
	/**
	 * Tracks whether there are activities currently bound to the service so
	 * that we can determine when it would be safe to call stopSelf().
	 */
	boolean mActive = false;

	IPlaylistChangeListenerCallbackList mChangeListeners;
	IPlaylistMoveListenerCallbackList mMoveListeners;
	IPlaylistDownloadListenerCallbackList mDownloadListeners;
	IPlaylistBufferListenerCallbackList mBufferListeners;
	
	PowerManager.WakeLock mWakeLock;

	volatile boolean mResumeAfterCall = false;

	@Override
	public void onCreate()
	{
		Log.d(TAG, "onCreate");

		super.onCreate();

		/* We use a wake lock for the entire time the service is alive.
		 * When playback is explicitly, we delay for a short while then die,
		 * releasing the wake lock. */
		PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		mWakeLock.acquire();

		/* Listen for incoming calls so we can temporarily pause playback. */
		TelephonyManager tm =
		  (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);

		mPlayer = new StreamMediaPlayer();
		mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		assert mNM != null;

		mChangeListeners = new IPlaylistChangeListenerCallbackList();
		mMoveListeners = new IPlaylistMoveListenerCallbackList();
		mDownloadListeners = new IPlaylistDownloadListenerCallbackList();
		mBufferListeners = new IPlaylistBufferListenerCallbackList();

		mManager = new SongDownloadManager(this);

		/* When the service dies we attempt to serialize playlist state to
		 * disk.  Check for, and recover from, this state file. */
		try {
			recoverState();
		} catch (IOException e) {
			Log.e(TAG, "Couldn't recover state!", e);
		}

		/* Detect changes in the network state to adjust behaviour accordingly.
		 * For instance, if the connectivity leaves and then returns, restart
		 * stalled or failed downloads. */
		registerReceiver(mConnectivityReceiver,
		  new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

		bindCacheService();
	}

	@Override
	public void onDestroy()
	{
		Log.d(TAG, "onDestroy");

		try {
			saveState();
		} catch (IOException e) {
			Log.e(TAG, "Couldn't save state!", e);
		}

		unregisterReceiver(mConnectivityReceiver);

		TelephonyManager tm =
		  (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(mPhoneListener, PhoneStateListener.LISTEN_NONE);

		/* XXX: Synchronization may not be necessary here as onDestroy() is
		 * likely to have been called after any binder threads were nuked. */
		synchronized(mBinderLock) {
			mChangeListeners.kill();
			mMoveListeners.kill();
			mDownloadListeners.kill();
			mBufferListeners.kill();

			mPlayer.reset();
			mPlayer.release();			
			mPlayer = null;
		}

		unbindService(this);

		mWakeLock.release();

		super.onDestroy();
	}

	public void saveState()
	  throws IOException
	{
		FileOutputStream outf = openFileOutput(STATE_FILE, MODE_PRIVATE);
		DataOutputStream out = null;

		try {
			out = new DataOutputStream(new BufferedOutputStream(outf, 2048));

			out.writeInt(STATE_FILE_FORMAT);
			out.writeInt(mPosition);
			out.writeBoolean(mPlaying);
			out.writeBoolean(mPaused);

			if (mPaused == true)
				out.writeInt(mPlayer.getCurrentPosition());
			else
				out.writeInt(0);

			out.writeInt(mPlaylist.size());
			for (Long songId: mPlaylist)
				out.writeLong(songId);
		} finally {
			if (out != null)
				out.close();
			else
				outf.close();
		}
	}

	public boolean recoverState()
	  throws IOException
	{
		FileInputStream inf;

		try {
			inf = openFileInput(STATE_FILE);
		} catch (FileNotFoundException e) {
			return false;
		}

		DataInputStream in = null;

		try {
			in = new DataInputStream(new BufferedInputStream(inf, 2048));

			int version = in.readInt();
			if (version != STATE_FILE_FORMAT)
				return false;

			int pos = in.readInt();
			boolean playing = in.readBoolean();
			boolean paused = in.readBoolean();
			int playpos = in.readInt();

			int playlist_length = in.readInt();

			try {
				for (int i = 0; i < playlist_length; i++)
					mPlaylist.add(in.readLong());
			} catch (IOException e) {
				mPlaylist.clear();
				throw e;
			}

			mPosition = pos;
			mPlaying = playing;

			/* XXX: We don't support this yet.  We would need to reload
			 * the MediaPlayer to its original state and seek to playpos,
			 * which will be tricky to guarantee.  For now, we force
			 * mPrepared false to ensure that a call to unpause will restart
			 * from the beginning in either case. */
			mPaused = paused;
			mPrepared = false;

			return true;
		} finally {
			if (in != null)
				in.close();
			else
				inf.close();
		}
	}
	
	/* Logic taken from packages/apps/Music.  Will pause when an incoming
	 * call rings (volume > 0), or if a call (incoming or outgoing) is
	 * connected. */
	private PhoneStateListener mPhoneListener = new PhoneStateListener()
	{
		@Override
		public void onCallStateChanged(int state, String incomingNumber)
		{
			try {
				switch (state)
				{
				case TelephonyManager.CALL_STATE_RINGING:
					AudioManager am = 
					  (AudioManager)getSystemService(AUDIO_SERVICE);
					
					/* Don't pause if the ringer isn't making any noise. */
					int ringvol = am.getStreamVolume(AudioManager.STREAM_RING);
					if (ringvol <= 0)
						break;

					/* Fall through... */
				case TelephonyManager.CALL_STATE_OFFHOOK:
					if (mBinder.isPlaying() == true &&
					    mBinder.isPaused() == false)
					{
						mResumeAfterCall = true;
						mBinder.pause();
					}
					break;
				case TelephonyManager.CALL_STATE_IDLE:
					if (mResumeAfterCall == true)
					{
						mBinder.unpause();
						mResumeAfterCall = false;
					}
					break;
				default:
					Log.d(TAG, "Unknown phone state=" + state);
				}
			} catch (RemoteException e) {}
		}
	};

	private void bindCacheService()
	{
		ComponentName service = new ComponentName("org.devtcg.five",
		  "org.devtcg.five.service.CacheService");

		bindService(new Intent().setComponent(service),
		  this, BIND_AUTO_CREATE);
	}

	private void waitForCacheService()
	{
		long now = -1;
		long then;

		/* Wait up to 1s before critically failing. */
		int timeout = 1000;

		synchronized(mCacheLock) {
			while (mCache == null)
			{
				then = now;
				now = System.currentTimeMillis();

				if (then >= 0)
				{
					timeout -= (now - then);

					if (timeout <= 0)
					{
						throw new IllegalStateException("Cannot proceed " +
						  "without a connection to the cache service");
					}
				}

				try { mCacheLock.wait(timeout); }
				catch (InterruptedException e) {}
			}
		}
	}

	public void onServiceConnected(ComponentName name, IBinder binder)
	{
		synchronized(mCacheLock) {
			assert mCache == null;
			mCache = ICacheService.Stub.asInterface(binder);
			mCacheLock.notifyAll();
		}
	}

	public void onServiceDisconnected(ComponentName name)
	{
		/* AIEEEEEE! */
		throw new IllegalStateException("Cannot proceed without a connection" +
		  " to " + name);
	}

	private final DeferredStopHandler mHandler = new DeferredStopHandler();
	private class DeferredStopHandler extends Handler
	{
		/* Wait 2 minutes before vanishing. */
		public static final long DEFERRAL_DELAY = 2 * (60 * 1000);

		private static final int DEFERRED_STOP = 0;

		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case DEFERRED_STOP:
				stopSelf();
				break;
			default:
				super.handleMessage(msg);
			}
		}

		public void deferredStopSelf()
		{
			Log.i(TAG, "Service stop scheduled " + (DEFERRAL_DELAY / 1000 / 60) + " minutes from now.");
			sendMessageDelayed(obtainMessage(DEFERRED_STOP), DEFERRAL_DELAY); 
		}

		public void cancelStopSelf()
		{
			if (hasMessages(DEFERRED_STOP) == true)
			{
				Log.i(TAG, "Service stop cancelled.");
				removeMessages(DEFERRED_STOP);
			}
		}
	};

	@Override
	public IBinder onBind(Intent intent)
	{
		mActive = true;
		return mBinder;
	}

	@Override
	public void onRebind(Intent intent)
	{
		mActive = true;
		mHandler.cancelStopSelf();
		super.onRebind(intent);
	}

	@Override
	public boolean onUnbind(Intent intent)
	{
		mActive = false;

		if (mResumeAfterCall == true)
			return true;

		if (mPlaying == true && mPaused == false)
			return true;
		
		mHandler.deferredStopSelf();
		return true;
	}

	private void notifySong(long songId)
	{
		if (songId < 0)
			mNM.cancel(NOTIF_PLAYING);
		else
		{
			Notification n = new Notification();

			n.contentIntent = PendingIntent.getActivity(this,
			  0, new Intent(this, Player.class), 0);

			n.flags = Notification.FLAG_NO_CLEAR |
			  Notification.FLAG_ONGOING_EVENT;

			n.icon = R.drawable.stat_notify_musicplayer;
			n.when = System.currentTimeMillis();

			RemoteViews view = new RemoteViews("org.devtcg.five", 
			  R.layout.notif_playing);

			Song song = new Song(getContentResolver(), songId);

			if (song.albumCover != null)
				view.setImageViewUri(R.id.album_cover, song.albumCover);
			else
				view.setImageViewResource(R.id.album_cover, R.drawable.lastfm_cover_small);

			view.setTextViewText(R.id.song_name, song.title);
			view.setTextViewText(R.id.artist_name, song.artist);
			view.setTextViewText(R.id.album_name, song.album);

			n.contentView = view;

			mNM.notify(NOTIF_PLAYING, n);
		}
	}
	
	private long getPlayingSong()
	{
		synchronized(mBinderLock) {
			if (mPlaying == false)
				return -1;

			if (mPosition < 0)
				return -1;

			return mPlaylist.get(mPosition);
		}
	}

	private BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			NetworkInfo info = (NetworkInfo)intent.getParcelableExtra
			  (ConnectivityManager.EXTRA_NETWORK_INFO);

			Log.v(TAG, "ConnectivityChange: info=" + info);

			/* Resume paused downloads if we get connected.  Note that there is
			 * no matching action when we've lost connectivity as we may
			 * regain it before the established connection has timed out
			 * and failed. */
			if (info != null && info.isConnected() == true)
				mManager.resumeDownloads();
		}
	};

	private Cursor getContentCursor(long songId)
	{
		Uri songUri = ContentUris.withAppendedId(Five.Music.Songs.CONTENT_URI,
		  songId);

		Cursor c = getContentResolver().query(songUri,
		  new String[] { Five.Music.Songs.CONTENT_ID }, null, null, null);

		long _id;

		try {
			if (c.moveToFirst() == false)
				return null;

			_id = c.getLong(0);
		} finally {
			c.close();
		}

		Uri contentUri =
		  ContentUris.withAppendedId(Five.Content.CONTENT_URI, _id);

		c = getContentResolver().query(contentUri,
		  new String[] { Five.Content.CONTENT_ID, Five.Content.SOURCE_ID,
		    Five.Content.CACHED_PATH, Five.Content.SIZE }, null, null, null);

		return c;
	}

	/**
	 * Does all the heavy lifting to play a song.  Checks the cache,
	 * manages the local HTTP server / streaming, and (later) playback
	 * events.
	 *
	 * @return
	 *   True if playback is starting; false if an unforeseen error has
	 *   occurred.
	 */
	private boolean playInternal(long songId)
	{
		/* Blocking wait for the cache service to become connected now that
		 * we critically require it.  If the service is not made available 
		 * within a reasonable time, we will halt. */
		waitForCacheService();
		assert mCache != null;

		mHandler.cancelStopSelf();

		Cursor c = getContentCursor(songId);

		long contentId;
		long sourceId;
		String cachePath;
		long size;

		try {
			if (c.moveToFirst() == false)
				return false;

			contentId = c.getLong(c.getColumnIndexOrThrow(Five.Content.CONTENT_ID));
			assert contentId >= 0;

			sourceId = c.getLong(c.getColumnIndexOrThrow(Five.Content.SOURCE_ID));
			assert sourceId >= 0;

			cachePath = c.getString(c.getColumnIndexOrThrow(Five.Content.CACHED_PATH));

			size = c.getLong(c.getColumnIndexOrThrow(Five.Content.SIZE));
			assert size > 0;
		} finally {
			c.close();
		}

		Log.v(TAG, "Preparing to play songId=" + songId + ": " +
		  "size=" + size + ", cachePath=" + cachePath);

		mPrepared = false;
		mPlayer.reset();
//		mPlayer.setOnBufferingUpdateListener(this);
		mPlayer.setOnCompletionListener(this);
		mPlayer.setOnErrorListener(this);
		mPlayer.setOnPreparedListener(this);

		try {
			if (cachePath != null)
			{
				long length = (new File(cachePath)).length();

				if (length == size)
				{
					Log.i(TAG, "Cache hit, playing " + cachePath);
					mPlayer.setDataSource(cachePath);
				}
				else
				{
					Log.i(TAG, "Partial cache hit, resuming from " +
					  cachePath + " at " + length);

					/* XXX: We have a small race condition possibility here
					 * since we aren't synchronizing anything.  The download
					 * might have just finished, in which case our lookup
					 * would yield null, but we'll foolishly try a resumed
					 * download for a very small section of the file. */
					DownloadManager.Download dl =
					  mManager.lookupDownload(songId);

					/* Must be a partially complete, canceled download. */ 
					if (dl == null)
					{
						mManager.stopAllDownloads();

						URL url = Sources.getContentURL(getContentResolver(),
						  sourceId, contentId);
						dl = mManager.startDownload(songId, 
						  url.toString(), cachePath, length);
					}

					mPlayer.setDataSource(new TailStream(cachePath, size));
				}
			}
			else
			{
				assert mManager.lookupDownload(songId) == null;

				cachePath = mCache.requestStorageAsPath(sourceId, contentId);

				URL url = Sources.getContentURL(getContentResolver(),
				  sourceId, contentId);
				assert url != null;

				/* We only allow 1 download active at a time, and it's gonna
				 * be the one that is currently streaming. */
				mManager.stopAllDownloads();

				DownloadManager.Download dl = 
				  mManager.startDownload(songId, url.toString(), cachePath);

				mPlayer.setDataSource(new TailStream(cachePath, size));
			}
		} catch (Exception e) {
			Log.e(TAG, "Crap", e);
			mPlayer.reset();
			return false;
		}

		notifySong(songId);
				
		mBufferListeners.broadcastOnBufferingUpdate(songId, 0);
		mPlayer.prepareAsync();

		Log.i(TAG, "Should be preparing...");

		return true;
	}

	/* XXX: Copy/paste job from playInternal.  Refactor SOON. */
	private DownloadManager.Download preempt(long songId)
	{
		/* Blocking wait for the cache service to become connected now that
		 * we critically require it.  If the service is not made available 
		 * within a reasonable time, we will halt. */
		waitForCacheService();
		assert mCache != null;

		Cursor c = getContentCursor(songId);

		long contentId;
		long sourceId;
		String cachePath;
		long size;

		try {
			if (c.moveToFirst() == false)
				return null;

			contentId = c.getLong(c.getColumnIndexOrThrow(Five.Content.CONTENT_ID));
			assert contentId >= 0;

			sourceId = c.getLong(c.getColumnIndexOrThrow(Five.Content.SOURCE_ID));
			assert sourceId >= 0;

			cachePath = c.getString(c.getColumnIndexOrThrow(Five.Content.CACHED_PATH));

			size = c.getLong(c.getColumnIndexOrThrow(Five.Content.SIZE));
			assert size > 0;
		} finally {
			c.close();
		}

		Log.v(TAG, "Preparing to preempt songId=" + songId + ": " +
		  "size=" + size + ", cachePath=" + cachePath);

		try {
			if (cachePath != null)
			{
				long length = (new File(cachePath)).length();

				if (length == size)
				{
					Log.i(TAG, "Cache hit, preemption not necessary.");
					return null;
				}
				else
				{
					Log.i(TAG, "Partial cache hit, resuming from " +
					  cachePath + " at " + length);

					/* XXX: We have a small race condition possibility here
					 * since we aren't synchronizing anything.  The download
					 * might have just finished, in which case our lookup
					 * would yield null, but we'll foolishly try a resumed
					 * download for a very small section of the file. */
					DownloadManager.Download dl =
					  mManager.lookupDownload(songId);

					/* Must be a partially complete, canceled download. */ 
					if (dl == null)
					{
						mManager.stopAllDownloads();

						URL url = Sources.getContentURL(getContentResolver(),
						  sourceId, contentId);
						dl = mManager.startDownload(songId, 
						  url.toString(), cachePath, length);
					}

					return dl;
				}
			}
			else
			{
				assert mManager.lookupDownload(songId) == null;

				cachePath = mCache.requestStorageAsPath(sourceId, contentId);

				URL url = Sources.getContentURL(getContentResolver(),
				  sourceId, contentId);
				assert url != null;

				Log.i(TAG, "Preemptively downloading to " + cachePath);

				mManager.stopAllDownloads();

				DownloadManager.Download dl = 
				  mManager.startDownload(songId, url.toString(), cachePath);
			}
		} catch (Exception e) {
			Log.e(TAG, "Preemption failed!", e);
			mManager.stopDownload(songId);
			return null;
		}

		return null;
	}
	
	/**
	 * Check at key stages to make sure that the song to be played next
	 * is preemptively downloading.
	 */
	private void preemptionCheck()
	  throws RemoteException
	{
		long currentId;
		long nextId = -1;

		synchronized(mBinderLock) {
			if (mPlaying == false)
				return;

			currentId = getPlayingSong();
			assert currentId >= 0;

			int next = mBinder.peekNext();
			if (next < 0)
				return;

			nextId = mPlaylist.get(next);
			assert nextId >= 0;
		}

		if (mManager.lookupDownload(currentId) != null)
		{
			Log.i(TAG, "Preemption miss due to active download.");
			return;
		}

		if (mManager.lookupDownload(nextId) != null)
		{
			Log.i(TAG, "Preemption already in progress.");
			return;
		}

		preempt(nextId);
	}

	private SongDownloadManager mManager; 
	private class SongDownloadManager extends DownloadManager
	{
		private final Map<String, Long> mUrlToSongMap =
		  Collections.synchronizedMap(new HashMap<String, Long>());
		
		public SongDownloadManager(Context ctx)
		{
			super(ctx);
		}

		@Override
		public void onStateChange(String url, int state, String message)
		{
			Log.i(TAG, "url=" + url + ", state=" + state + ", message=" + message);

			if (state == STATE_CONNECTED)
			{
				long songId = mUrlToSongMap.get(url);
				mDownloadListeners.broadcastOnDownloadBegin(songId);
			}
		}

		public boolean commitStorage(long songId)
		{
			Cursor c = getContentCursor(songId);

			long sourceId;
			long contentId;

			try {
				if (c.moveToFirst() == false)
					return false;

				sourceId = c.getLong(c.getColumnIndexOrThrow(Five.Content.SOURCE_ID));
				contentId = c.getLong(c.getColumnIndexOrThrow(Five.Content.CONTENT_ID));
			} finally {
				c.close();
			}

			try {
				mCache.commitStorage(sourceId, contentId);
			} catch (RemoteException e) {
				return false;
			}

			return true;
		}

		@Override
		public void onFinished(String url)
		{
			long songId = mUrlToSongMap.get(url);
			mDownloadListeners.broadcastOnDownloadFinish(songId);

			commitStorage(songId);

			final Download d = lookupDownload(url);

			mHandler.post(new Runnable() {
				public void run() {
					try {
						/* Should be on its way out, but until then
						 * the manager thinks we're actively downloading. */
						d.joinUninterruptibly();
						preemptionCheck();
					} catch (RemoteException e) {}
				}
			});
		}

		@Override
		public void onAborted(String url)
		{
			long songId = mUrlToSongMap.get(url);
			mDownloadListeners.broadcastOnDownloadCancel(songId);

			//super.onAborted(url);
		}

		@Override
		public void onError(String url, int state, final String err)
		{
			long songId = mUrlToSongMap.get(url);
			mDownloadListeners.broadcastOnDownloadError(songId, err);
		}

		@Override
		public void onProgressUpdate(String url, int percent)
		{
			long songId = mUrlToSongMap.get(url);
			mDownloadListeners.broadcastOnDownloadProgressUpdate(songId, percent);
		}

		long getSongIdFromUrl(String url)
		{
			return mUrlToSongMap.get(url);
		}

		public Download lookupDownload(long songId)
		{
			/* Ouch. */
			synchronized(mUrlToSongMap) {
				Set<Entry<String, Long>> set = mUrlToSongMap.entrySet();

				for (Entry<String, Long> entry: set)
				{
					if (entry.getValue() == songId)
						return super.lookupDownload(entry.getKey());
				}
			}

			return null;
		}

		public Download startDownload(long songId, String url, String path,
		  long resumeFrom)
		  throws IOException
		{
			Download d = super.startDownload(url, path, resumeFrom);

			if (d != null)
				mUrlToSongMap.put(url, songId);

			return d;
		}

		public Download startDownload(long songId, String url, String path)
		  throws IOException
		{
			Download d = super.startDownload(url, path);

			if (d != null)
				mUrlToSongMap.put(url, songId);

			return d;
		}

		public void stopDownload(long songId)
		{
			super.stopDownload(lookupDownload(songId));
		}

		@Override
		public void removeDownload(String url)
		{
			super.removeDownload(url);
			mUrlToSongMap.remove(url);
		}
	};

	/* This callback doesn't report useful information in 1.0r1, so we
	 * ignore it. */
	public void onBufferingUpdate(MediaPlayer mp, int percent)
	{
//		long songId = getPlayingSong();
//		assert songId >= 0;
//
//		mBufferListeners.broadcastOnBufferingUpdate(songId, percent);
	}

	public boolean onError(MediaPlayer mp, int what, int extra)
	{
		Log.d(TAG, "Media playback error, what=" + what + ", extra=" + extra);

		assert mp == mPlayer;

		long songId = getPlayingSong();

		if (songId >= 0)
		{
			/* If we have an error condition on this song's download, 
			 * trigger it as a download error.  Normally, we hide network
			 * errors and passively retry but if the MediaPlayer catches
			 * up, we should report the last error we encountered.
			 * XXX: This introduces a bug where onDownloadError could fire
			 * twice consecutively for the same download. */
			DownloadManager.Download dl = mManager.lookupDownload(songId);
			if (dl != null)
			{
				switch (dl.getDownloadState())
				{
				case DownloadManager.STATE_HTTP_ERROR:
				case DownloadManager.STATE_FILE_ERROR:
				case DownloadManager.STATE_PAUSED_LOCAL_FAILURE:
				case DownloadManager.STATE_PAUSED_REMOTE_FAILURE:
					mDownloadListeners.broadcastOnDownloadError(songId, 
					  dl.getStateMessage());
					break;
				}

				mManager.stopDownload(songId);
			}
		}
		
		boolean playing;
		boolean paused;

		synchronized(mBinderLock) {
			playing = mPlaying;
			paused = mPaused;

			if (mPlayer.isPlaying() == true)
				mPlayer.stop();

			mPlayer.reset();

			mPlaying = false;
			mPrepared = false;
			mPaused = false;
		}

		try {
			if (playing == true && paused == false)
				mBinder.next();
		} catch (RemoteException e) {}

		return true;
	}

	public void onCompletion(MediaPlayer mp)
	{
		Log.i(TAG, "Should be finished.");
		
		assert mp == mPlayer;

		assert mPlaying == true;
		assert mPaused == false;
		
		synchronized(mBinderLock) {
			mPlayer.stop();
			mPlayer.reset();

			mPrepared = false;		
		}

		try {
			mBinder.next();
		} catch (RemoteException e) {}
	}

	public void onPrepared(MediaPlayer mp)
	{
		assert mp == mPlayer;

		assert mPlaying == true;

		synchronized(mBinderLock) {
			if (mPaused == true)
				Log.i(TAG, "Ready to play, but paused.");
			else
			{
				Log.i(TAG, "Should be playing...");
				mPlayer.start();
			}

			mPrepared = true;
		}

		long songId = getPlayingSong();
		assert songId >= 0;
		mBufferListeners.broadcastOnBufferingUpdate(songId, 100);
	}

	private final IPlaylistService.Stub mBinder = new IPlaylistService.Stub()
	{
		/*-********************************************************************/

		public void registerOnMoveListener(IPlaylistMoveListener l)
		  throws RemoteException
		{
			mMoveListeners.register(l);
		}

		public void unregisterOnMoveListener(IPlaylistMoveListener l)
		  throws RemoteException
		{
			mMoveListeners.unregister(l);
		}

		public int next()
		  throws RemoteException
		{
			int next = peekNext();

			if (next >= 0)
				jump(next);
			else
			{
				stop();
				jump(-1);
			}

			return next;
		}

		public int previous()
		  throws RemoteException
		{
			int prev;

			synchronized(mBinderLock) {
				if (mPlaying == true)
				{
					if (mPaused == true)
					{
						play();
						return mPosition;
					}
					else if (tell() > 10000)
					{
						seek(0);
						return mPosition;
					}
				}

				prev = mPosition - 1;

				if (prev < 0)
				{
					int n;
					if ((n = mPlaylist.size()) == 0)
					{
						stop();
						jump(-1);
						return -1;
					}

					prev = n - 1;
				}
			}

			jump(prev);
			return prev;
		}

		public void jump(int pos)
		  throws RemoteException
		{
			if (pos < -1 || pos >= mPlaylist.size())
				return;

			synchronized(mBinderLock) {
				mPosition = pos;
			}

			if (pos >= 0)
			{
				mMoveListeners.broadcastOnJump(pos);

				if (mPlaying == true && mPaused == false)
				{
					synchronized(mBinderLock) {
						mPlayer.stop();
						playInternal(mPlaylist.get(pos));
					}
				}
				else
					play();

				synchronized(mBinderLock) {
					preemptionCheck();
				}
			}
		}

		public void play()
		  throws RemoteException
		{
			long songId; 

			synchronized(mBinderLock) {
				if (mPlaylist.isEmpty() == true)
					return;
			}

			setForeground(true);

			synchronized(mBinderLock) {
				if (mPosition == -1)
					jump(0);

				songId = mPlaylist.get(mPosition);

				mPlaying = true;
				mPaused = false;

				/* TODO: How should we handle this?  Gracefully destroying
				 * the service may be a good idea. */
				boolean ret = playInternal(songId);
				assert ret == true;
			}

			mMoveListeners.broadcastOnPlay();
		}

		public void pause()
		  throws RemoteException
		{
			if (isPlaying() == false)
				return;

			setForeground(false);
			notifySong(-1);

			synchronized(mBinderLock) {
				if (mPlayer.isPlaying() == true)
					mPlayer.pause();

				mPaused = true;
			}

			mMoveListeners.broadcastOnPause();
		}

		public void unpause()
		  throws RemoteException
		{
			if (isPaused() == false)
				return;

			setForeground(true);

			synchronized(mBinderLock) {
				assert mPlaying == true;
				assert mPosition > 0;

				long songId = mPlaylist.get(mPosition);
				notifySong(songId);

				if (mPrepared == true)
					mPlayer.start();
				else
				{
					boolean ret = playInternal(songId);
					assert ret == true;
				}

				mPaused = false;
			}

			mMoveListeners.broadcastOnUnpause();
		}

		public void stop()
		  throws RemoteException
		{
			if (mActive == false)
				mHandler.deferredStopSelf();

			if (isPlaying() == false && isPaused() == false)
				return;

			setForeground(false);
			notifySong(-1);

			synchronized(mBinderLock) {
				mPlayer.stop();
				mPlayer.reset();
				mPrepared = false;
				mPaused = false;
				mPlaying = false;
			}

			mMoveListeners.broadcastOnStop();
		}

		public void seek(long pos)
		  throws RemoteException
		{
			synchronized(mBinderLock) {
				mPlayer.seekTo((int)pos);
			}

			mMoveListeners.broadcastOnSeek(pos);
		}

		/*-********************************************************************/

		public int getPosition()
		  throws RemoteException
		{
			return mPosition;
		}

		public long tell()
		  throws RemoteException
		{
			if (isPlaying() == false && isPaused() == false)
				return -1;

			int pos;

			synchronized(mBinderLock) {
				pos = mPlayer.getCurrentPosition();
			}

			return (long)pos;
		}

		public long getSongDuration()
		  throws RemoteException
		{
			int dur;

			synchronized(mBinderLock) {
				dur = mPlayer.getDuration();
			}
			
			return (long)dur;
		}

		public boolean isPlaying()
		  throws RemoteException
		{
			return mPlaying;
		}

		public boolean isStopped()
		  throws RemoteException
		{
			return mPlaying == false;
		}

		public boolean isPaused()
		  throws RemoteException
		{
			return mPaused;
		}

		public boolean isDownloading()
		  throws RemoteException
		{
			int n = getPosition();

			if (n == -1)
				return false;

			return mManager.lookupDownload(mPlaylist.get(n)) != null;
		}

		public boolean isOutputting()
		  throws RemoteException
		{
			boolean playing;
			
			synchronized(mBinderLock) {
				playing = mPlayer.isPlaying();
			}
			
			return playing;
		}

		/*-********************************************************************/

		public List getPlaylist()
		  throws RemoteException
		{
			return mPlaylist;
		}

		public List getPlaylistWindow(int from, int to)
		  throws RemoteException
		{
			if (from >= to)
				return null;

			if (from < 0)
				return null;

			synchronized(mBinderLock) {
				if (mPlaylist.isEmpty() == true)
					return null;

				int n = mPlaylist.size();
				assert n > 0;

				if (to > n)
					to = n;

				/* XXX: We assume that Android's serialization will
				 * copy our list so as not to leak references. */ 
				return mPlaylist.subList(from, to);
			}
		}

		public int getPlaylistLength()
		  throws RemoteException
		{
			return mPlaylist.size();
		}

		public long getSongAt(int pos)
		  throws RemoteException
		{
			if (pos < 0)
				return -1;

			synchronized(mBinderLock) {
				if (pos >= mPlaylist.size())
					return -1;

				return mPlaylist.get(pos);
			}
		}

		public int getPositionOf(long songId)
		  throws RemoteException
		{
			return mPlaylist.lastIndexOf(songId);
		}

		public int peekNext()
		  throws RemoteException
		{
			int next;

			synchronized(mBinderLock) {
				next = mPosition + 1;

				if (next >= mPlaylist.size())
					return -1;
			}

			return next;
		}

		/*-********************************************************************/

		public void shuffle()
		  throws RemoteException
		{
		}

		public void setRepeat(int repeatMode)
		  throws RemoteException
		{
		}

		public int getRepeat()
		  throws RemoteException
		{
			return -1;
		}

		public void setRandom(boolean random)
		  throws RemoteException
		{
			assert random == false;
		}

		public boolean getRandom()
		  throws RemoteException
		{
			return false;
		}

		/*-********************************************************************/

		public void registerOnChangeListener(IPlaylistChangeListener l)
		  throws RemoteException
		{
			mChangeListeners.register(l);
		}

		public void unregisterOnChangeListener(IPlaylistChangeListener l)
		  throws RemoteException
		{
			mChangeListeners.unregister(l);
		}

		public void loadPlaylistRef(long playlistId)
		  throws RemoteException
		{
		}

		public long getPlaylistRef()
		  throws RemoteException
		{
			return -1;
		}

		public boolean isPlaylistRefLiteral()
		  throws RemoteException
		{
			return false;
		}

		public void clear()
		  throws RemoteException
		{
			stop();
			jump(-1);

			synchronized(mBinderLock) {
				mPlaylist.clear();
			}

			mChangeListeners.broadcastOnClear();
		}

		public void insert(long songId, int pos)
		  throws RemoteException
		{
			synchronized(mBinderLock) {
				mPlaylist.add(pos, songId);

				if (mPosition >= 0)
				{
					if (pos <= mPosition)
						mPosition++;
				}

				if (peekNext() == pos)
					preemptionCheck();
			}

			mChangeListeners.broadcastOnInsert(songId, pos);

			synchronized(mBinderLock) {
				if (isPlaying() == false && isPaused() == false)
				{
					mPosition = pos;
					play();
				}
			}
		}

		public void insertNext(long songId)
		  throws RemoteException
		{
			int n = getPosition();

			if (n == -1)
				append(songId);
			else
				insert(songId, n + 1);
		}

		public void prepend(long songId)
		  throws RemoteException
		{
			insert(songId, 0);
		}

		public void append(long songId)
		  throws RemoteException
		{
			insert(songId, mPlaylist.size());
		}

		public long remove(int pos)
		  throws RemoteException
		{
			long songId;

			synchronized(mBinderLock) {
				if (mPosition == pos && isPlaying() == true)
					stop();

				songId = mPlaylist.remove(pos);
			}

			mChangeListeners.broadcastOnRemove(pos);
			return songId;
		}

		public long move(int oldpos, int newpos)
		  throws RemoteException
		{
			Log.d(TAG, "UNIMPLEMENTED: move(int,int)");
			return -1;
		}

		/*-********************************************************************/

		public void registerOnDownloadListener(IPlaylistDownloadListener l)
		  throws RemoteException
		{
			/* Inform this new listener of the currently active
			 * downloads. */
			List<DownloadManager.Download> downloads =
			  mManager.getDownloadsCopy();

			for (DownloadManager.Download dl: downloads)
			{
				long songId = mManager.getSongIdFromUrl(dl.getUrl());
				l.onDownloadBegin(songId);
				l.onDownloadProgressUpdate(songId, dl.getProgress());
			}
			
			mDownloadListeners.register(l);
		}

		public void unregisterOnDownloadListener(IPlaylistDownloadListener l)
		  throws RemoteException
		{
			mDownloadListeners.unregister(l);
		}

		public void registerOnBufferingListener(IPlaylistBufferListener l)
		  throws RemoteException
		{
			if (mPlaying == true)
			{
				long songId = getPlayingSong();
				l.onBufferingUpdate(songId, (mPrepared == true) ? 100 : 0);
			}

			mBufferListeners.register(l);
		}

		public void unregisterOnBufferingListener(IPlaylistBufferListener l)
		  throws RemoteException
		{
			mBufferListeners.unregister(l);
		}
	};
}
