package org.devtcg.five.music.activity;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.devtcg.five.R;
import org.devtcg.five.music.service.IPlaylistBufferListener;
import org.devtcg.five.music.service.IPlaylistChangeListener;
import org.devtcg.five.music.service.IPlaylistDownloadListener;
import org.devtcg.five.music.service.IPlaylistMoveListener;
import org.devtcg.five.music.service.IPlaylistService;
import org.devtcg.five.music.service.PlaylistService;
import org.devtcg.five.music.util.PlaylistServiceActivity;
import org.devtcg.five.music.util.Song;
import org.devtcg.five.music.widget.PlaybackBar;
import org.devtcg.five.provider.Five;
import org.devtcg.five.util.ServiceActivity;
import org.devtcg.five.widget.StatefulListView;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.OnItemClickListener;

public class PlaylistServiceTest extends PlaylistServiceActivity
{
	public static final String TAG = "PlaylistServiceTest";

	private final ProgressHandler mHandler = new ProgressHandler();

	private IPlaylistService mService = null;
	
	private PlaylistAdapter mAdapter;

	private StatefulListView mPlaylist;
	private Button mClear;
	private Button mDeleteRandom;
	private Button mAddRandom;
	private PlaybackBar mPlayInfo;
	private Button mPrev;
	private Button mPlay;
	private Button mNext;
	
	private final Random mRandom = new Random();

	private int mPlayingPosition = -1;
	private long mPlaying = -1;
	
	@Override
	protected void onInitUI()
	{
		setContentView(R.layout.playlist_service_test);
		
		mClear = (Button)findViewById(R.id.clear);
		mClear.setOnClickListener(mClearClick);

		mDeleteRandom = (Button)findViewById(R.id.delete_random);
		mDeleteRandom.setOnClickListener(mDeleteRandomClick);

		mAddRandom = (Button)findViewById(R.id.add_random);
		mAddRandom.setOnClickListener(mAddRandomClick);

		mPlayInfo = (PlaybackBar)findViewById(R.id.playback_info);
		mPlayInfo.setEnabled(false);
		mPlayInfo.setOnSeekBarChangeListener(mSeeked);

		mPrev = (Button)findViewById(R.id.prev);
		mPrev.setOnClickListener(mPrevClick);

		mPlay = (Button)findViewById(R.id.playstop);
		mPlay.setOnClickListener(mPlayClick);

		mNext = (Button)findViewById(R.id.next);
		mNext.setOnClickListener(mNextClick);

		mPlaylist = (StatefulListView)findViewById(R.id.playlist);
		mAdapter = new PlaylistAdapter(this);
		mPlaylist.setAdapter(mAdapter);
		mPlaylist.setOnItemClickListener(mSongClicked);
	}

	@Override
	protected void onAttached()
	{
		try {
			mService.registerOnMoveListener(mMoveListener);
			mService.registerOnChangeListener(mChangeListener);
			mService.registerOnBufferingListener(mBufferingListener);
			mService.registerOnDownloadListener(mDownloadListener);
			
			mAdapter.setList((List<Long>)mService.getPlaylist());

			if (mService.isPlaying() == true)
			{
				int pos = mService.getPosition();
				setPlaying(mService.getSongAt(pos), pos);
			}
		} catch (RemoteException e) {}
	}

	@Override
	protected void onDetached()
	{
		try {
			mService.unregisterOnMoveListener(mMoveListener);
			mService.unregisterOnChangeListener(mChangeListener);
			mService.unregisterOnBufferingListener(mBufferingListener);
			mService.unregisterOnDownloadListener(mDownloadListener);
		} catch (RemoteException e) {}

		mAdapter.clearList();
	}

	private void setPlaying(long songId, int pos)
	{
		mPlayInfo.setProgress(0);
		mPlayInfo.setSecondaryProgress(0);

		if ((mPlaying < 0 && songId >= 0) || (mPlaying >= 0 && songId < 0))
		{
			if (songId >= 0)
			{
				mPlay.setText("Stop");
				mPlay.setOnClickListener(mStopClick);
				mPlayInfo.setEnabled(true);
				
				mHandler.startPlaybackMonitoring();
			}
			else
			{
				mPlay.setText("Play");
				mPlay.setOnClickListener(mPlayClick);
				mPlayInfo.setEnabled(false);

				mHandler.stopPlaybackMonitoring();
			}
		}

		if (mPlayingPosition >= 0)
		{
			View row = mPlaylist.getChildFromPos(mPlayingPosition);
			Log.d(TAG, "row[" + mPlayingPosition + "]=" + row);
			if (row != null)
				row.findViewById(R.id.song_playing_icon).setVisibility(View.INVISIBLE);
		}

		View row = mPlaylist.getChildFromPos(pos);
		Log.d(TAG, "row[" + pos + "]=" + row);
		if (row != null)
			row.findViewById(R.id.song_playing_icon).setVisibility(View.VISIBLE);

		mPlaying = songId;
		mPlayingPosition = pos;
	}

	private class PlaylistAdapter extends BaseAdapter
	{
		private final ArrayList<Song> mList = new ArrayList<Song>();
		private final LayoutInflater mInflater;
		
		public PlaylistAdapter(Context ctx)
		{
			super();

			mInflater = (LayoutInflater)ctx
			  .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public int getCount()
		{
			return mList.size();
		}

		public Object getItem(int position)
		{
			return mList.get(position);
		}

		public long getItemId(int position)
		{
			return mList.get(position).id;
		}

		public View getView(int position, View convertView, ViewGroup parent)
		{
			if (convertView == null)
				convertView = mInflater.inflate(R.layout.playlist_service_item, null);

			Song song = mList.get(position);

			TextView title = (TextView)convertView.findViewById(R.id.song_name);
			title.setText(song.title);

			TextView artist = (TextView)convertView.findViewById(R.id.artist_name);
			artist.setText(song.artist);

			View notif = convertView.findViewById(R.id.song_playing_icon);

			if (mPlayingPosition == position)
				notif.setVisibility(View.VISIBLE);
			else
				notif.setVisibility(View.INVISIBLE);

			return convertView;
		}

		public void clearList()
		{
			mList.clear();
			notifyDataSetChanged();
		}

		public void setList(List<Long> songs)
		{
			int n = songs.size();
			mList.ensureCapacity(n);
			for (int i = 0; i < n; i++)
				addSong(songs.get(i), i);
		}

		public void addSong(long songId, int pos)
		{
			mList.add(pos, new Song(getContentResolver(), songId));
			notifyDataSetChanged();
		}

		public void removeSong(int pos)
		{
			mList.remove(pos);
			notifyDataSetChanged();
		}
	}
	
	private final OnItemClickListener mSongClicked = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> adapter, View v, int pos,
		  long id)
		{
			try {
				mService.jump(pos);
			} catch (RemoteException e) {}
		}
	};

	private final IPlaylistDownloadListener.Stub mDownloadListener =
	  new IPlaylistDownloadListener.Stub()
	{
		public void onDownloadBegin(long songId) throws RemoteException
		{
			Log.i(TAG, "Started download for songId=" + songId);
		}

		public void onDownloadCancel(long songId) throws RemoteException
		{
			Log.i(TAG, "Canceled download for songId=" + songId);
		}

		public void onDownloadError(long songId, final String err)
		  throws RemoteException
		{
			Log.i(TAG, "Error downloading songId=" + songId + ": " + err);

			mHandler.post(new Runnable() {
				public void run() {
					Toast.makeText(PlaylistServiceTest.this,
					  "Error accessing content: " + err, Toast.LENGTH_LONG)
					    .show();
				}
			});
		}

		public void onDownloadFinish(final long songId) throws RemoteException
		{
			Log.i(TAG, "Download finished for songId=" + songId);
			
			mHandler.post(new Runnable() {
				public void run() {
					if (mPlaying == songId)
						mPlayInfo.fadeOutDownloadProgress();
				}
			});
		}

		public void onDownloadProgressUpdate(long songId, int percent)
		  throws RemoteException
		{
			mHandler.sendDownloadProgress(songId, percent);
		}
	};
	
	private final IPlaylistBufferListener.Stub mBufferingListener =
	  new IPlaylistBufferListener.Stub()
	{
		public void onBufferingUpdate(long songId, int bufferPercent)
		  throws RemoteException
		{
//			Log.i(TAG, "onBufferingUpdate: songId=" + songId + ", bufferPercent=" + bufferPercent);
		}
	};

	private final IPlaylistMoveListener.Stub mMoveListener =
	  new IPlaylistMoveListener.Stub()
	{
		public void onAdvance() throws RemoteException
		{
			/* This method isn't used... */
			Log.e(TAG, "What the hell?");
		}

		public void onJump(final int pos) throws RemoteException
		{
			Log.i(TAG, "onJump: pos=" + pos);

			final long songId = mService.getSongAt(pos);

			mHandler.post(new Runnable() {
				public void run() {
					setPlaying(songId, pos);
				}
			});
		}

		public void onPause() throws RemoteException
		{
			Log.i(TAG, "onPaused");
		}

		public void onPlay() throws RemoteException
		{
			Log.i(TAG, "onPlay");

			final int pos = mService.getPosition();
			final long songId = mService.getSongAt(pos);
			
			mHandler.post(new Runnable() {
				public void run() {
					setPlaying(songId, pos);
				}
			});
		}

		public void onSeek(long pos) throws RemoteException
		{
			Log.i(TAG, "onSeek: pos=" + pos);

			/* Really just schedules an update now. */
			mHandler.startPlaybackMonitoring();
		}

		public void onStop() throws RemoteException
		{
			Log.i(TAG, "onStop");

			mHandler.post(new Runnable() {
				public void run() {
					setPlaying(-1, -1);
				}
			});
		}

		public void onUnpause() throws RemoteException
		{
			Log.i(TAG, "onUnpause()");
		}
	};

	private final IPlaylistChangeListener.Stub mChangeListener =
	  new IPlaylistChangeListener.Stub()
	{
		public void onClear() throws RemoteException
		{
			Log.i(TAG, "Playlist cleared.");

			mHandler.post(new Runnable() {
				public void run() {
					mAdapter.clearList();
				}
			});
		}

		public void onInsert(final long songId, final int pos)
		  throws RemoteException
		{
			Log.i(TAG, "Inserted songId=" + songId + " at pos=" + pos);

			mPlayingPosition = mService.getPosition();

			mHandler.post(new Runnable() {
				public void run() {
					mAdapter.addSong(songId, pos);					
				}
			});
		}

		public void onLoadPlaylistRef(long playlistId) throws RemoteException
		{
			Log.i(TAG, "Loaded playlistId=" + playlistId);
		}

		public void onMove(long songId, int oldpos, int newpos)
		  throws RemoteException
		{
			Log.i(TAG, "Playlist item (songId=" + songId + ") moved from oldpos=" + oldpos + " to newpos=" + newpos);
		}

		public void onRemove(final int pos) throws RemoteException
		{
			Log.i(TAG, "Removed song at pos=" + pos);
			
			mHandler.post(new Runnable() {
				public void run() {
					mAdapter.removeSong(pos);
				}
			});
		}
	};

	private final OnClickListener mClearClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			try {
				mService.clear();
			} catch (RemoteException e) {}
		}
	};

	private final OnClickListener mDeleteRandomClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			try {
				int len = mService.getPlaylistLength();
				int pos = mRandom.nextInt(len);
				mService.remove(pos);
			} catch (RemoteException e) {}
		}
	};

	private final OnClickListener mAddRandomClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			Cursor c = getContentResolver().query(Five.Music.Songs.CONTENT_URI,
			  new String[] { Five.Content._ID }, null, null, null);

			int nrecords = c.getCount();			
			int record = mRandom.nextInt(nrecords);

			try {
				if (c.moveToPosition(record) == false)
					return;

				mService.append(c.getLong(0));
			} catch (RemoteException e) {
			} finally {
				c.close();
			}
		}
	};

	private final OnClickListener mPrevClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			try {
				int r = mService.previous();
				Log.d(TAG, "previous: r=" + r);
			} catch (RemoteException e) {}
		}
	};
	
	private final OnClickListener mNextClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			try {
				int r = mService.next();
				Log.d(TAG, "next: r=" + r);
			} catch (RemoteException e) {}
		}
	};
	
	private final OnClickListener mPlayClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			try {
				mService.play();
			} catch (RemoteException e) {}
		}
	};

	private final OnClickListener mStopClick = new OnClickListener()
	{
		public void onClick(View v)
		{
			try {
				mService.stop();
			} catch (RemoteException e) {}
		}
	};

	private final SeekBar.OnSeekBarChangeListener mSeeked = new SeekBar.OnSeekBarChangeListener()
	{
		public void onProgressChanged(SeekBar seekBar, int progress,
		  boolean fromTouch)
		{
			if (fromTouch == false)
				return;

			assert mPlaying >= 0;

			try {
				long dur = mService.getSongDuration();
				long target = (long)((progress / 100f) * dur);
				mService.seek(target);
			} catch (RemoteException e) {}
		}

		public void onStartTrackingTouch(SeekBar seekBar) {}
		public void onStopTrackingTouch(SeekBar seekBar) {}
	};

	private class ProgressHandler extends Handler
	{
		private static final int MSG_DOWNLOAD_PROGRESS = 1;
		private static final int MSG_PLAYBACK_PROGRESS = 2;

		public int mLastDownloadProgress = -1;

		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case MSG_DOWNLOAD_PROGRESS:
				if (mPlaying == (Long)msg.obj)
					mPlayInfo.setSecondaryProgress(msg.arg1);
				break;
			case MSG_PLAYBACK_PROGRESS:
				try {
					if (mService.isOutputting() == true)
					{
						mPlayInfo.setProgress((int)
						  (((float)mService.tell() /
						    (float)mService.getSongDuration()) * 100f));
					}
				} catch (RemoteException e) {}
				sendMessageDelayed(obtainMessage(MSG_PLAYBACK_PROGRESS),
				  1000);
				break;
			default:
				super.handleMessage(msg);
				break;
			}
		}

		public void startPlaybackMonitoring()
		{
			removeMessages(MSG_PLAYBACK_PROGRESS);
			sendMessage(obtainMessage(MSG_PLAYBACK_PROGRESS));
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
}
