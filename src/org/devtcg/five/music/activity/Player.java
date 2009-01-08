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

package org.devtcg.five.music.activity;

import org.devtcg.five.R;
import org.devtcg.five.music.service.IPlaylistBufferListener;
import org.devtcg.five.music.service.IPlaylistDownloadListener;
import org.devtcg.five.music.service.IPlaylistMoveListener;
import org.devtcg.five.music.util.PlaylistServiceActivity;
import org.devtcg.five.music.util.Song;
import org.devtcg.five.music.widget.BetterReflectionLayout;
import org.devtcg.five.music.widget.PlaybackBar;
import org.devtcg.five.provider.Five;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class Player extends PlaylistServiceActivity
{
	private static final String TAG = "Playlist";
	
	private ProgressHandler mHandler = new ProgressHandler();

	private TextView mArtist;
	private TextView mAlbum;
	private TextView mSong;

	private BetterReflectionLayout mAlbumCover;
	private View mProgressControls;
	private TextView mPlaybackPos;
	private TextView mPlaybackDur;
	private TextView mPlaylistPos;
	private PlaybackBar mPlaybackInfo;
	private ImageButton mControlPrev;
	private ImageButton mControlPause;
	private ImageButton mControlNext;
	
	/* How many milliseconds to display the progress controls?  This
	 * timer begins after a user-initiated gesture or after the playback
	 * buffer becomes full. */
	private static final int PROGRESS_TIMEOUT = 5500;

	private boolean mPaused = false;
	
	private int mBufferPercent;

	private Song mSongPlaying;

	/* Flag used to determine if we have exercised an optimization potential
	 * given by the supplied Intent's extras. */
	private boolean mHintedOpt = false;

	private static final int MENU_SET_RANDOM = Menu.FIRST;
	private static final int MENU_MORE_BY_ARTIST = Menu.FIRST + 1;
	private static final int MENU_RETURN_LIBRARY = Menu.FIRST + 2;
	private static final int MENU_ARTIST_BIO = Menu.FIRST + 3;
	private static final int MENU_PLAYQUEUE = Menu.FIRST + 4;
	
	public static void show(Context context)
	{
		context.startActivity(new Intent(context, Player.class));
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		loadWithHint(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		loadWithHint(intent);
	}

	private void loadWithHint(Intent intent)
	{
		Bundle extras = getIntent().getExtras();
		if (extras != null)
		{
			mHintedOpt = true;
			showUI(true);
			setNowPlaying(extras);
			showProgress();
		}
	}
	
	@Override
	protected void onInitUI()
	{
		setContentView(R.layout.player);

		mArtist = (TextView)findViewById(R.id.artist_name);
		mAlbum = (TextView)findViewById(R.id.album_name);
		mSong = (TextView)findViewById(R.id.song_name);

		mAlbumCover = (BetterReflectionLayout)findViewById(R.id.album_cover);
		mAlbumCover.setOnClickListener(mAlbumClick);

		mProgressControls = mAlbumCover.findViewById(R.id.progress_controls);

		mPlaybackPos = (TextView)mAlbumCover.findViewById(R.id.playback_position);
		mPlaybackDur = (TextView)mAlbumCover.findViewById(R.id.playback_duration);
		mPlaylistPos = (TextView)mAlbumCover.findViewById(R.id.playlist_position);
		
		mPlaybackInfo = (PlaybackBar)mAlbumCover.findViewById(R.id.playback_info);
		mPlaybackInfo.setOnSeekBarChangeListener(mSeeked);

		mControlPrev = (ImageButton)mAlbumCover.findViewById(R.id.control_prev);
		mControlPause = (ImageButton)mAlbumCover.findViewById(R.id.control_pause);
		mControlNext = (ImageButton)mAlbumCover.findViewById(R.id.control_next);

		mControlPrev.setOnClickListener(mDoPrev);
		mControlPause.setOnClickListener(mDoPauseToggle);
		mControlNext.setOnClickListener(mDoNext);
	}

	@Override
	protected void onAttached()
	{
		super.onAttached();
		
		try {
			mService.registerOnBufferingListener(mServiceBufferListener);
			mService.registerOnMoveListener(mServiceMoveListener);
			mService.registerOnDownloadListener(mServiceDownloadListener);

			if (mService.isPlaying() == false)
				setNotPlaying();
			else
			{
				int pos = mService.getPosition();
				long songId = mService.getSongAt(pos);

				if (mSongPlaying == null || mSongPlaying.id != songId)
				{
					if (mHintedOpt == true)
						Log.w(TAG, "Hinted Intent was wrong!");

					setNowPlaying(songId, pos);
				}

				setPausedState(mService.isPaused());
			}
		} catch (RemoteException e) {}		
	}

	@Override
    protected void onDetached()
	{
		super.onDetached();
		
		try {
			mService.unregisterOnBufferingListener(mServiceBufferListener);
			mService.unregisterOnMoveListener(mServiceMoveListener);
			mService.unregisterOnDownloadListener(mServiceDownloadListener);
		} catch (RemoteException e) {}
	}

	private void hideProgress()
	{
		if (progressIsShown() == true)
		{
			mPlaybackInfo.clearAnimation();
			mProgressControls.setVisibility(View.GONE);
			mAlbumCover.requestLayout();
			mAlbumCover.invalidate();
		}
	}

	private void showProgress()
	{
		if (mBufferPercent < 100)
			showProgress(0);
		else
			showProgress(PROGRESS_TIMEOUT);
	}

	private void showProgress(int timeout)
	{
		if (progressIsShown() == false)
		{
			mPlaybackInfo.clearAnimation();
			mProgressControls.setVisibility(View.VISIBLE);
			mAlbumCover.requestLayout();
			mAlbumCover.invalidate();
		}

		if (timeout > 0)
			mHandler.startProgressTimeout(timeout);
		else
			mHandler.stopProgressTimeout();
	}

	private boolean progressIsShown()
	{
		return mProgressControls.getVisibility() == View.VISIBLE;
	}
	
	private final OnClickListener mAlbumClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			if (progressIsShown() == false)
				showProgress();
			else
				hideProgress();
		}
	};
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		
//		menu.add(0, MENU_SET_RANDOM, Menu.NONE, "Random")
//		  .setCheckable(true)
//		  .setIcon(R.drawable.ic_menu_shuffle)

		menu.add(0, MENU_MORE_BY_ARTIST, Menu.NONE, "More By Artist")
		  .setIcon(R.drawable.ic_menu_add);
		
		menu.add(0, MENU_RETURN_LIBRARY, Menu.NONE, R.string.return_library)
		  .setIcon(R.drawable.ic_menu_music_library);
		
		menu.add(0, MENU_ARTIST_BIO, Menu.NONE, "Artist Info")
		  .setIcon(R.drawable.ic_menu_artist_info);
		
		menu.add(0, MENU_PLAYQUEUE, Menu.NONE, "Play queue")
		  .setIcon(R.drawable.ic_menu_playqueue);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		menu.findItem(MENU_MORE_BY_ARTIST).setVisible(mSongPlaying != null);
		menu.findItem(MENU_ARTIST_BIO).setVisible(mSongPlaying != null);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case MENU_SET_RANDOM:
			Toast.makeText(this,"Not implemented",
			  Toast.LENGTH_SHORT).show();
			return true;
		case MENU_MORE_BY_ARTIST:
			chooseArtist();
			return true;
		case MENU_RETURN_LIBRARY:
			Main.show(this);
			return true;
		case MENU_ARTIST_BIO:
			Toast.makeText(this,"Not implemented",
			  Toast.LENGTH_SHORT).show();
			return true;
		case MENU_PLAYQUEUE:
			SongList.actionOpenPlayQueue(this);
			return true;
		}

		return false;
	}

	/* TODO: Generalize a way to re-use existing chooser building
	 * functions (in this case, the one in ArtistList.java). */
	private void chooseArtist()
	{
		if (mSongPlaying == null)
			return;

		Intent chosen = new Intent();

		chosen.setData(Five.Music.Artists.CONTENT_URI.buildUpon()
		  .appendEncodedPath(String.valueOf(mSongPlaying.artistId)).build());

		chosen.putExtra("artistName", mSongPlaying.artist);

		chosen.setAction(Intent.ACTION_VIEW);
		chosen.setClass(this, ArtistAlbumList.class);

		startActivity(chosen);
	}

	private static String formatTime(int sec)
	{
		int minutes = sec / 60;
		int seconds = sec % 60;
		
		StringBuilder b = new StringBuilder();
		b.append(minutes).append(':');
		
		if (seconds < 10)
			b.append('0');
		
		b.append(seconds);

		return b.toString();
	}

	private class ProgressHandler extends Handler
	{
		private static final int MSG_HIDE_PROGRESS = 0;
		private static final int MSG_DOWNLOAD_PROGRESS = 1;
		private static final int MSG_PLAYBACK_PROGRESS = 2;

		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case MSG_HIDE_PROGRESS:
				hideProgress();
				break;
			case MSG_DOWNLOAD_PROGRESS:
				if (mSongPlaying != null && mSongPlaying.id == (Long)msg.obj)
					mPlaybackInfo.setSecondaryProgress(msg.arg1);
				break;
			case MSG_PLAYBACK_PROGRESS:
				try {
					if (mService != null && mService.isOutputting() == true)
					{
						long pos = mService.tell();
						long dur = mService.getSongDuration();

						mPlaybackPos.setText(formatTime((int)(pos / 1000)));
						mPlaybackDur.setText("-" +
						  formatTime((int)((dur - pos) / 1000)));

						mPlaybackInfo.setProgress((int)
						  (((float)pos / (float)dur) * 100f));
					}
				} catch (RemoteException e) {}
				sendMessageDelayed(obtainMessage(MSG_PLAYBACK_PROGRESS), 1000);
				break;
			default:
				super.handleMessage(msg);
			}
		}
		
		public void startProgressTimeout(int timeout)
		{
			removeMessages(MSG_HIDE_PROGRESS);
			sendMessageDelayed(obtainMessage(MSG_HIDE_PROGRESS), timeout);
		}
		
		public void stopProgressTimeout()
		{
			removeMessages(MSG_HIDE_PROGRESS);
		}

		public void startPlaybackMonitoring(boolean updateNow)
		{
			removeMessages(MSG_PLAYBACK_PROGRESS);
			Message msg = obtainMessage(MSG_PLAYBACK_PROGRESS);
			
			if (updateNow == true)
				handleMessage(msg);
			else
				sendMessage(msg);
		}

		public void stopPlaybackMonitoring()
		{
			removeMessages(MSG_PLAYBACK_PROGRESS);
		}

		public void sendDownloadProgress(long songId, int progress)
		{
			sendMessage(obtainMessage(MSG_DOWNLOAD_PROGRESS,
			  progress, -1, (Long)songId));
		}
	}
	
	private final SeekBar.OnSeekBarChangeListener mSeeked =
	  new SeekBar.OnSeekBarChangeListener()
	{
		public void onProgressChanged(SeekBar seekBar, int progress,
		  boolean fromTouch)
		{
			if (fromTouch == false)
				return;

			try {
				long dur = mService.getSongDuration();
				long target = (long)((progress / 100f) * dur);
				mService.seek(target);
			} catch (RemoteException e) {}
		}

		public void onStartTrackingTouch(SeekBar seekBar) {}
		public void onStopTrackingTouch(SeekBar seekBar) {}
	};
	
	private final OnClickListener mDoPrev = new OnClickListener()
	{
		public void onClick(View v)
		{
			if (mService == null)
				return;

			int pos;
			
			try { pos = mService.previous(); }
			catch (RemoteException e) { finish(); return; }
			
			if (pos == -1)
			{
				Toast.makeText(Player.this, "End of playlist",
				  Toast.LENGTH_SHORT).show();
			}
		}
	};
	
	private final OnClickListener mDoPauseToggle = new OnClickListener()
	{
		public void onClick(View v)
		{
			if (mService == null)
				return;

			try
			{
				if (mPaused == true)
				{
					if (mService.isPaused() == true)
						mService.unpause();
					else
						mService.play();

					setPausedState(false);
				}
				else
				{
					mService.pause();
					setPausedState(true);
				}
			}
			catch (RemoteException e)
			{
				finish();
			}
		}
	};
	
	private final OnClickListener mDoNext = new OnClickListener()
	{
		public void onClick(View v)
		{
			if (mService == null)
				return;
			
			int pos;
			
			try { pos = mService.next(); }
			catch (RemoteException e) { finish(); return; }
			
			if (pos == -1)
			{
				Toast.makeText(Player.this, "End of playlist",
				  Toast.LENGTH_SHORT).show();
			}
		}
	};

	private void setNotPlaying()
	{
		mSongPlaying = null;

		Log.d(TAG, "*NOT PLAYING*");

		mSong.setText("End of Playlist");
		mArtist.setText("");
		mAlbum.setText("");
		mAlbumCover.setImageResource(R.drawable.not_playing_cover);

		mPlaybackPos.setText("0:00");
		mPlaybackDur.setText("-:--");
		mPlaylistPos.setText("");
		mPlaybackInfo.setEnabled(false);
		mPlaybackInfo.setProgress(0);
		mPlaybackInfo.setSecondaryProgress(0);

		/* Not really, we just want the mechanics to restart playback. */
		setPausedState(true);

		mHandler.stopPlaybackMonitoring();
	}

	private void setNowPlaying(Song song, int pos, int len)
	{
		Log.d(TAG, "setNowPlaying(" + song.id + "; pos=" + pos + "; len=" + len + ")");		

		if (mSongPlaying == null || mSongPlaying.albumId != song.albumId)
		{
			Log.i(TAG, "Setting a new album for albumId=" + song.albumId);

			Uri coverNow = (mSongPlaying != null ?
			  mSongPlaying.albumCoverBig : null);

			if (song.albumCoverBig == null)
				mAlbumCover.setImageResource(R.drawable.lastfm_cover);
			else
			{
				if (coverNow == null || coverNow.equals(song.albumCoverBig) == false)
					mAlbumCover.setImageURI(song.albumCoverBig);
			}
		}

		mSongPlaying = song;

		mSong.setText(song.title);
		mArtist.setText(song.artist);
		mAlbum.setText(song.album);

		mPlaybackPos.setText("0:00");
		mPlaybackDur.setText("-" + formatTime((int)song.length));
		mPlaybackInfo.setEnabled(true);
		mPlaybackInfo.setProgress(0);
		mPlaybackInfo.setSecondaryProgress(0);

		mPlaylistPos.setText((pos + 1) + " of " + len);

		mHandler.startPlaybackMonitoring(true);
	}

	private void setNowPlaying(long songId, int pos)
	  throws RemoteException
	{
		setNowPlaying(new Song(this, songId), pos,
		  mService.getPlaylistLength());
	}

	/* Special way to startup where we are handed our initial UI state so
	 * we can avoid an expensive query. */
	private void setNowPlaying(Bundle e)
	{
		Song song = new Song();

		song.artistId = e.getLong("artistId");
		song.artist = e.getString("artistName");

		song.albumId = e.getLong("albumId");
		song.album = e.getString("albumName");

		String artwork = e.getString("albumArtworkBig");
		if (artwork != null)
			song.albumCoverBig = Uri.parse(artwork);

		song.id = e.getLong("songId");
		song.title = e.getString("songTitle");
		song.length = e.getLong("songLength");

		setNowPlaying(song, e.getInt("playlistPosition"),
		  e.getInt("playlistLength"));
	}

	private void setPausedState(boolean paused)
	{
		if (mPaused == paused)
			return;

		int resid = (paused == true) ?
		  android.R.drawable.ic_media_play :
		  android.R.drawable.ic_media_pause;

		mControlPause.setImageResource(resid);
		mPaused = paused;
	}
	
	private final IPlaylistBufferListener.Stub mServiceBufferListener =
	  new IPlaylistBufferListener.Stub()
	{
		public void onBufferingUpdate(long songId, final int bufferPercent)
		  throws RemoteException
		{
			mHandler.post(new Runnable() {
				public void run() {
					mBufferPercent = bufferPercent;

					/* If the buffer is empty, show the progress bar until the
					 * download begins again or the player aborts.  Otherwise,
					 * start the timeout to automatically hide the progress
					 * controls */
					if (bufferPercent == 0)
						showProgress(0);
					else if (bufferPercent == 100)
					{
						if (progressIsShown() == true)
							showProgress();
					}
				}
			});
		}
	};

	private final IPlaylistMoveListener.Stub mServiceMoveListener =
	  new IPlaylistMoveListener.Stub()
	{
		public void onAdvance() throws RemoteException
		{
		}

		public void onJump(final int pos) throws RemoteException
		{
			final long songId = mService.getSongAt(pos);
			assert songId >= 0;

			mHandler.post(new Runnable() {
				public void run() {
					try {
						setNowPlaying(songId, pos);
					} catch (RemoteException e) {}

					/* Jump implicitly unpauses. */
					setPausedState(false);
				}
			});
		}

		public void onPause() throws RemoteException
		{
			mHandler.post(new Runnable() {
				public void run() {
					if (mPaused == true)
						return;

					setPausedState(true);
				}
			});
		}

		public void onPlay() throws RemoteException
		{
			final int pos = mService.getPosition();
			final long songId = mService.getSongAt(pos);
			assert songId >= 0;

			mHandler.post(new Runnable() {
				public void run()
				{
					try {
						setNowPlaying(songId, pos);
					} catch (RemoteException e) {}

					setPausedState(false);
				}
			});
		}

		public void onStop() throws RemoteException
		{
			mHandler.post(new Runnable() {
				public void run() {
					setNotPlaying();
				}
			});
		}

		public void onSeek(long pos) throws RemoteException
		{
		}

		public void onUnpause() throws RemoteException
		{
			mHandler.post(new Runnable() {
				public void run() {
					if (mPaused == false)
						return;

					setPausedState(false);
				}
			});
		}
	};
	
	private final IPlaylistDownloadListener.Stub mServiceDownloadListener =
	  new IPlaylistDownloadListener.Stub()
	{
		public void onDownloadBegin(long songId) throws RemoteException {}
		public void onDownloadCancel(long songId) throws RemoteException {}

		public void onDownloadError(long songId, final String err)
		  throws RemoteException
		{
			Log.i(TAG, "Error downloading songId=" + songId + ": " + err);

			mHandler.post(new Runnable() {
				public void run() {
					Toast.makeText(Player.this,
					  "Aww shit: " + err, Toast.LENGTH_LONG).show();
				}
			});
		}

		public void onDownloadFinish(final long songId)
		  throws RemoteException
		{
			Log.i(TAG, "Download finished for songId=" + songId);

			mHandler.post(new Runnable() {
				public void run() {
					if (mSongPlaying != null && mSongPlaying.id == songId)
						mPlaybackInfo.fadeOutDownloadProgress();
				}
			});
		}

		public void onDownloadProgressUpdate(long songId, int percent)
		  throws RemoteException
		{
			mHandler.sendDownloadProgress(songId, percent);
		}
	};
}
