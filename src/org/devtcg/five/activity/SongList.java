/*
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

package org.devtcg.five.activity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.AlbumItem;
import org.devtcg.five.service.IPlaylistMoveListener;
import org.devtcg.five.service.IPlaylistService;
import org.devtcg.five.util.PlaylistServiceActivity;
import org.devtcg.five.widget.EfficientCursorAdapter;
import org.devtcg.five.widget.StatefulListView;

import android.content.Context;
import android.content.Intent;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class SongList extends PlaylistServiceActivity
  implements ViewBinder
{
	public static final String TAG = "SongList";

	private static final String[] QUERY_FIELDS =
	  { Five.Music.Songs._ID, Five.Music.Songs.TITLE,
		Five.Music.Songs.LENGTH, Five.Music.Songs.TRACK,
		Five.Music.Songs.ARTIST_ID };

	/* Compilation albums are handled specially by showing the artist name
	 * with the song title.  This map is designed to efficiently cache
	 * the result of that extra query. */
	private HashMap<Long, String> mVAArtistMap = null;

	/* Stored information handed to us through our Intent. */
	private SongListExtras mExtras;

	private Handler mHandler = new Handler();

	private StatefulListView mList;
	private EfficientCursorAdapter mAdapter;

	private Cursor mCursor;

	private long mSongPlaying = -1;

	private static final int MENU_ENQUEUE_LAST = Menu.FIRST;
	private static final int MENU_PLAY_NEXT = Menu.FIRST + 1;
	private static final int MENU_PLAY_NOW = Menu.FIRST + 2;
	private static final int MENU_PAUSE = Menu.FIRST + 3;
	private static final int MENU_REPEAT = Menu.FIRST + 4;
	private static final int MENU_STOP = Menu.FIRST + 5;
	private static final int MENU_PLAY_SHUFFLED = Menu.FIRST + 6;
	private static final int MENU_REMOVE = Menu.FIRST + 7;
	private static final int MENU_RETURN_LIBRARY = Menu.FIRST + 8;
	private static final int MENU_GOTO_PLAYER = Menu.FIRST + 9;

	private static class SongListExtras
	{
		public long artistId;
		public String artistName;
		public long albumId;
		public String albumName;
		public String albumArt;
		public String albumArtBig;

		public long playlistId;
		public String playlistName;

		public boolean allAlbums;

		public boolean playQueue;

		public SongListExtras(Bundle b)
		{
			artistId = b.getLong("artistId", -1);
			artistName = b.getString("artistName");
			albumId = b.getLong("albumId", -1);
			albumName = b.getString("albumName");
			albumArt = b.getString("albumArtwork");
			albumArtBig = b.getString("albumArtworkBig");

			if (b.containsKey("playlistId") == true)
			{
				playlistId = b.getLong("playlistId");
				playlistName = b.getString("playlistName");
			}
			else
			{
				playlistId = -1;
				playlistName = null;
			}

			allAlbums = b.getBoolean("allAlbums");
			playQueue = b.getBoolean("playQueue");
		}

		public boolean showAlbumCover()
		{
			if (allAlbums == true || playlistId >= 0 || playQueue == true)
				return false;

			return true;
		}

		public boolean showTrackNumbers()
		{
			return showAlbumCover();
		}

		public boolean hasMultipleArtists()
		{
			if (playlistId >= 0 || playQueue == true)
				return true;

			return artistName.equals("Various Artists");
		}
	}

	public static void actionOpenPlayQueue(Context context)
	{
		Intent i = new Intent(context, SongList.class);
		i.putExtra("playQueue", true);
		context.startActivity(i);
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		mExtras = new SongListExtras(getIntent().getExtras());

		if (mExtras.showAlbumCover() == true)
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		else
		{
			if (mExtras.allAlbums == true)
				setTitle(mExtras.artistName);
			else if (mExtras.playlistId >= 0)
				setTitle(mExtras.playlistName);
			else if (mExtras.playQueue == true)
				setTitle("Now Playing");
			else
				throw new IllegalArgumentException("What's a matter you?");
		}
	}

	@Override
	protected void onInitUI()
	{
		setContentView(R.layout.song_list);

		Intent i = getIntent();

		if (mExtras.playQueue == true)
		{
			mCursor = new PlayQueueCursor(mService, QUERY_FIELDS);
			startManagingCursor(mCursor);
		}
		else
		{
			Uri songsUri = i.getData().buildUpon()
			  .appendPath("songs").build();

			mCursor = managedQuery(songsUri, QUERY_FIELDS, null, null, null);
		}

		if (mExtras.hasMultipleArtists() == true)
			mVAArtistMap = new HashMap<Long, String>(mCursor.getCount());

		mList = (StatefulListView)findViewById(android.R.id.list);

		if (mExtras.showAlbumCover() == true)
		{
			AlbumInfoView infoView = new AlbumInfoView(this);
			infoView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.FILL_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));
			infoView.bindToData(mExtras);
			mList.addHeaderView(infoView, null, false);
		}

		mAdapter = new EfficientCursorAdapter(this,
		  R.layout.song_list_item, mCursor,
		  new String[] { "title", "artist_id", "_id" },
		  new int[] { R.id.song_name, R.id.artist_name, R.id.song_playing_icon });

		mAdapter.setViewBinder(this);

		mList.setOnItemClickListener(mOnItemClickListener);
		registerForContextMenu(mList);
		mList.setAdapter(mAdapter);

		if (mExtras.playQueue == true)
		{
			try {
				mList.setSelection(mService.getPosition());
			} catch (RemoteException e) {}
		}
	}

	public String getArtistName(long artistId)
	{
		Uri artistUri = Five.Music.Artists.CONTENT_URI.buildUpon()
		  .appendEncodedPath(String.valueOf(artistId)).build();

		Cursor c = getContentResolver().query(artistUri,
		  new String[] { Five.Music.Artists.NAME }, null, null, null);

		try {
			if (c.moveToFirst() == false)
				return null;

			return c.getString(0);
		} finally {
			c.close();
		}
	}

	public boolean setViewValue(View v, Cursor c, int col)
	{
		if (QUERY_FIELDS[col] == Five.Music.Songs._ID)
		{
			if (mSongPlaying >= 0 && c.getLong(col) == mSongPlaying)
				v.setVisibility(View.VISIBLE);
			else
				v.setVisibility(View.INVISIBLE);
		}
		else if (QUERY_FIELDS[col] == Five.Music.Songs.ARTIST_ID)
		{
			if (mVAArtistMap != null)
			{
				long artistId = c.getLong(col);

				String artist = mVAArtistMap.get(artistId);
				if (artist == null)
				{
					artist = getArtistName(artistId);
					if (artist == null)
						throw new IllegalStateException("What the fuck?");

					mVAArtistMap.put(artistId, artist);
				}

				((TextView)v).setText(artist);
				v.setVisibility(View.VISIBLE);
			}
		}
		else
		{
			return false;
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		menu.add(0, MENU_RETURN_LIBRARY, 0, R.string.return_library)
		  .setIcon(R.drawable.ic_menu_music_library);
		menu.add(0, MENU_GOTO_PLAYER, 0, R.string.goto_player)
		  .setIcon(R.drawable.ic_menu_playback);
		menu.add(0, MENU_PLAY_SHUFFLED, 0, R.string.shuffle_all)
		  .setIcon(R.drawable.ic_menu_shuffle);

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		menu.findItem(MENU_GOTO_PLAYER).setVisible(mSongPlaying >= 0);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		try {
			switch (item.getItemId())
			{
			case MENU_RETURN_LIBRARY:
				Main.show(this);
				return true;
			case MENU_GOTO_PLAYER:
				Player.show(this);
				return true;
			case MENU_PLAY_SHUFFLED:
				playSongsShuffled();
				return true;
			}
		} catch (RemoteException e) {}

		return false;
	}

	@Override
	protected void onAttached()
	{
		super.onAttached();

		try {
			mService.registerOnMoveListener(mPlaylistMoveListener);

			long songId;

			if (mService.isPlaying() == false)
				songId = -1;
			else
			{
				int pos = mService.getPosition();
				songId = mService.getSongAt(pos);
			}

			setPlaying(songId);
		} catch (RemoteException e) {}
	}

	@Override
	protected void onDetached()
	{
		super.onDetached();

		try {
			mService.unregisterOnMoveListener(mPlaylistMoveListener);
		} catch (RemoteException e) {}
	}

	private static class AlbumInfoView extends LinearLayout
	{
		public AlbumInfoView(Context ctx)
		{
			super(ctx);

			LayoutInflater.from(ctx)
			  .inflate(R.layout.album_header, this);

			setPadding(0, 0, 0, 0);
		}

		public void bindToData(String artist, String album, String artwork)
		{
			((TextView)findViewById(R.id.album_name)).setText(album);

		    ImageView artworkView = (ImageView)findViewById(R.id.album_cover);

		    if (artwork != null)
		    	artworkView.setImageURI(Uri.parse(artwork));
		    else
		    	artworkView.setImageResource(R.drawable.lastfm_cover_small);
		}

		public void bindToData(SongListExtras e)
		{
			bindToData(e.artistName, e.albumName, e.albumArt);
		}

		public void bindToData(Cursor c)
		{
			bindToData(c.getString(c.getColumnIndexOrThrow(Five.Music.Artists.NAME)),
			  c.getString(c.getColumnIndexOrThrow(Five.Music.Albums.FULL_NAME)),
			  c.getString(c.getColumnIndexOrThrow(Five.Music.Albums.ARTWORK)));
		}
	}

	private void markRowPlaying(long songId, boolean playing)
	{
		View row = mList.getChildFromId(songId);
		if (row == null)
			return;

		int vis = playing ? View.VISIBLE : View.INVISIBLE;
		row.findViewById(R.id.song_playing_icon).setVisibility(vis);
	}

	private void setPlaying(long songId)
	{
		if (mSongPlaying >= 0)
			markRowPlaying(mSongPlaying, false);

		if (songId >= 0)
			markRowPlaying(songId, true);

		mSongPlaying = songId;
	}

//	private static class ShuffleAllView extends LinearLayout
//	{
//		public ShuffleAllView(Context context)
//		{
//			super(context);
//			init();
//		}
//
//		public void init()
//		{
//			LayoutInflater.from(getContext())
//			  .inflate(R.layout.shuffle_all_view, this);
//		}
//
//		public static ShuffleAllView getListView(Context ctx)
//		{
//			ShuffleAllView header = new ShuffleAllView(ctx);
//			header.setLayoutParams(new AbsListView.LayoutParams
//			  (AbsListView.LayoutParams.FILL_PARENT,
//			   AbsListView.LayoutParams.WRAP_CONTENT));
//			return header;
//		}
//	}

	private IPlaylistMoveListener.Stub mPlaylistMoveListener = new IPlaylistMoveListener.Stub()
	{
		public void onAdvance() throws RemoteException
		{
		}

		public void onJump(int pos) throws RemoteException
		{
			final long songId = mService.getSongAt(pos);
			mHandler.post(new Runnable() {
				public void run() {
					setPlaying(songId);
				}
			});
		}

		public void onPause() throws RemoteException
		{
		}

		public void onPlay() throws RemoteException
		{
			int pos = mService.getPosition();
			final long songId = mService.getSongAt(pos);

			mHandler.post(new Runnable() {
				public void run() {
					setPlaying(songId);
				}
			});
		}

		public void onSeek(long pos) throws RemoteException
		{
		}

		public void onStop() throws RemoteException
		{
			mHandler.post(new Runnable() {
				public void run() {
					setPlaying(-1);
				}
			});
		}

		public void onUnpause() throws RemoteException
		{
		}
	};

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
		long songId = info.id;
		int pos = info.position;

		try
		{
			switch (item.getItemId())
			{
			case MENU_STOP:
				mService.stop();
				return true;
			case MENU_PAUSE:
				if (mService.isPaused() == false)
					mService.pause();
				else
					mService.unpause();
				return true;
			case MENU_REPEAT:
				Toast.makeText(this, "Not supported",
				  Toast.LENGTH_SHORT).show();
				return true;
			case MENU_PLAY_NEXT:
				insertOneSongNext(pos, songId);
				return true;
			case MENU_PLAY_NOW:
				playOneSong(pos, songId);
				return true;
			case MENU_ENQUEUE_LAST:
				appendOneSong(pos, songId);
				return true;
			}
		}
		catch (RemoteException e) {}

		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	  ContextMenu.ContextMenuInfo menuInfo)
	{
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;

		if (info.id == -1)
			return;

		Cursor c = (Cursor)mAdapter.getItem(info.position -
		  mList.getHeaderViewsCount());
		String trackName =
		  c.getString(c.getColumnIndex(Five.Music.Songs.TITLE));
		menu.setHeaderTitle(trackName);

		if (mSongPlaying == info.id)
		{
			menu.add(0, MENU_STOP, Menu.NONE, "Stop");

			try {
				if (mService.isPaused() == false)
					menu.add(0, MENU_PAUSE, Menu.NONE, "Pause");
				else
					menu.add(0, MENU_PAUSE, Menu.NONE, "Unpause");
			} catch (RemoteException e) {}

			menu.add(0, MENU_REPEAT, Menu.NONE, "Repeat once");
		}
		else
		{
			menu.add(0, MENU_PLAY_NEXT, Menu.NONE, "Play next");
			menu.add(0, MENU_PLAY_NOW, Menu.NONE, "Play now");

			if (mExtras.playQueue == false)
				menu.add(0, MENU_ENQUEUE_LAST, Menu.NONE, "Enqueue");
		}

//		if (mExtras.playQueue == true)
//			menu.add(0, MENU_REMOVE, Menu.NONE, "Remove from playlist");
	};

	/* Gets an Intent to move to the player screen with plenty of optimization
	 * hints. */
	private Intent getHintedIntent(int pos, long id)
	{
		Intent i = new Intent(SongList.this, Player.class);

		if (mExtras.showAlbumCover() == false)
		{
			Log.w(TAG, "TODO: We don't pre-hint without the album queried out.");
			return i;
		}

		if (pos < 0 || id < 0)
		{
			Log.w(TAG, "TODO: Hinting isn't covered in all cases where it could be.");
			return i;
		}

		Cursor c = (Cursor)mAdapter.getItem(pos);

		if (mVAArtistMap != null)
		{
			long artistId =
			  c.getLong(c.getColumnIndexOrThrow(Five.Music.Songs.ARTIST_ID));
			i.putExtra("artistId", artistId);
			i.putExtra("artistName", mVAArtistMap.get(artistId));
		}
		else
		{
			i.putExtra("artistId", mExtras.artistId);
			i.putExtra("artistName", mExtras.artistName);
		}

		i.putExtra("albumId", mExtras.albumId);
		i.putExtra("albumName", mExtras.albumName);
		i.putExtra("albumArtworkBig", mExtras.albumArtBig);

		i.putExtra("songId", id);
		i.putExtra("songTitle",
		  c.getString(c.getColumnIndexOrThrow(Five.Music.Songs.TITLE)));
		i.putExtra("songLength",
		  c.getLong(c.getColumnIndexOrThrow(Five.Music.Songs.LENGTH)));
		i.putExtra("playlistPosition", pos);
		i.putExtra("playlistLength", mAdapter.getCount());

		return i;
	}

	private void playerStart(int pos, long id)
	  throws RemoteException
	{
		mService.unregisterOnMoveListener(mPlaylistMoveListener);
		startActivity(getHintedIntent(pos, id));
	}

	private void playSongsShuffled()
	  throws RemoteException
	{
		playerStart(-1, -1);

		mService.clear();

		Random r = new Random();

		int n = mAdapter.getCount();
		long[] playlist = new long[n];

		for (int i = 0; i < n; i++)
			playlist[i] = mAdapter.getItemId(i);

		while (n > 0)
		{
			int k = r.nextInt(n);
			n--;
			mService.append(playlist[k]);
			playlist[k] = playlist[n];
		}
	}

	private void playSongsStartingAt(int pos, long id)
	  throws RemoteException
	{
		playerStart(pos, id);

		if (mExtras.playQueue == true)
		{
			mService.jump(pos);
			return;
		}

		mService.clear();
		mService.append(id);

		for (int i = pos - 1; i >= 0; i--)
			mService.prepend(mAdapter.getItemId(i));

		int n = mAdapter.getCount();

		for (int i = pos + 1; i < n; i++)
			mService.append(mAdapter.getItemId(i));
	}

	private void playExistingSongAt(int pos, long id, int currPos)
	  throws RemoteException
	{
		if (mSongPlaying != id || mExtras.playQueue == true)
			playSongsStartingAt(pos, id);
		else
		{
			playerStart(pos, id);

			/* Delete the current playlist (except the playing song). */
			int currN = mService.getPlaylistLength();

			while (currN-- > 0)
			{
				if (currN == currPos)
					continue;

				mService.remove(currN);
			}

			/* Then build the playlist up again around selected song. */
			for (int i = pos - 1; i >= 0; i--)
				mService.prepend(mAdapter.getItemId(i));

			int newN = mAdapter.getCount();

			for (int i = pos + 1; i < newN; i++)
				mService.append(mAdapter.getItemId(i));
		}
	}

	private void insertOneSongNext(int pos, long id)
	  throws RemoteException
	{
		mService.insertNext(id);
	}

	private void appendOneSong(int pos, long id)
	  throws RemoteException
	{
		mService.append(id);
	}

	private void playOneSong(int pos, long id)
	  throws RemoteException
	{
		playerStart(pos, id);

		if (mExtras.playQueue == true)
			mService.jump(pos);
		else
		{
			mService.clear();
			mService.append(id);
		}
	}

	private OnItemClickListener mOnItemClickListener = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int pos, long id)
		{
			/* Generalized logic to ignore header clicks... */
			Object ad = av.getAdapter();
			if (ad instanceof HeaderViewListAdapter)
			{
				HeaderViewListAdapter adapter = (HeaderViewListAdapter)ad;

				int clickpos = pos;
				pos -= adapter.getHeadersCount();

				if (pos < 0)
				{
					/* We only set an item for the "Shuffle" header... */
					if (adapter.getItem(clickpos) != null)
					{
						try {
							playSongsShuffled();
						} catch (RemoteException e) {}
					}

					return;
				}
			}

			Log.i(TAG, "Clicked pos=" + pos);

			try
			{
				int found;

				if ((found = mService.getPositionOf(id)) >= 0)
					playExistingSongAt(pos, id, found);
				else
					playSongsStartingAt(pos, id);
			}
			catch (RemoteException e) {}
		}
	};

	private class PlayQueueCursor extends AbstractCursor
	{
		/* The database cursor that represents this selection of songs, in
		 * _id order. */
		protected Cursor mWrapped;

		protected String[] mFields;

		/* The wrapped cursor is not ordered the same as our play queue,
		 * so we must maintain an index.  This maps queue position to
		 * cursor position. */
		protected int[] mPositions;

		protected int mCount;

		public PlayQueueCursor(IPlaylistService service, String[] fields)
		{
			super();

			mFields = fields;
			mService = service;

			init();
		}

		private void init()
		{
			List queue;
			try {
				queue = mService.getPlaylist();
			} catch (RemoteException e) {
				return;
			}

			if (queue.isEmpty() == true)
				return;

			StringBuilder where = new StringBuilder();
			where.append(Five.Music.Songs._ID + " IN (");
			for (int n = queue.size() - 1; n >= 0; n--)
			{
				where.append(queue.get(n));
				if (n > 0)
					where.append(',');
			}
			where.append(')');

			/* We can't get a cursor in the order that we want so
			 * we'll just order by _ID and do binary searches.
			 * XXX: It would be more efficient to perform these searches
			 * directly on the cursor in onMove rather than precomputing
			 * the indices here. */
			mWrapped = SongList.this.getContentResolver()
			  .query(Five.Music.Songs.CONTENT_URI, mFields,
			    where.toString(), null, Five.Music.Songs._ID);

			mCount = mWrapped.getCount();
			mPositions = new int[mCount];

			/* Make one pass to build a searchable array by _ID. */
			long[] songIdx = new long[mCount];
			for (int i = 0; i < mCount; i++)
			{
				mWrapped.moveToNext();

				/* XXX: We assume column 0 is the _ID column. */
				songIdx[i] = mWrapped.getLong(0);
			}

			/* ...then build our queue to cursor position mapping. */
			for (int i = 0; i < mCount; i++)
			{
				mPositions[i] =
				  Arrays.binarySearch(songIdx, (Long)queue.get(i));
			}
		}

		@Override
		public boolean onMove(int oldPosition, int newPosition)
		{
			if (oldPosition == newPosition)
				return true;

			if (mWrapped == null)
				return false;

			mWrapped.moveToPosition(mPositions[newPosition]);
			return true;
		}

		@Override
		public void deactivate()
		{
			if (mWrapped != null)
			{
				mWrapped.deactivate();
				mWrapped = null;
				mPositions = null;
			}
		}

		@Override
		public boolean requery()
		{
			close();
			init();
			return true;
		}

		@Override
		public void close()
		{
			if (mWrapped != null)
			{
				mWrapped.close();
				mWrapped = null;
				mPositions = null;
			}
		}

		@Override
		public String[] getColumnNames()
		{
			return mFields;
		}

		@Override
		public int getCount()
		{
			return mCount;
		}

		@Override
		public double getDouble(int column)
		{
			return mWrapped.getDouble(column);
		}

		@Override
		public float getFloat(int column)
		{
			return mWrapped.getFloat(column);
		}

		@Override
		public int getInt(int column)
		{
			return mWrapped.getInt(column);
		}

		@Override
		public long getLong(int column)
		{
			return mWrapped.getLong(column);
		}

		@Override
		public short getShort(int column)
		{
			return mWrapped.getShort(column);
		}

		@Override
		public String getString(int column)
		{
			return mWrapped.getString(column);
		}

		@Override
		public boolean isNull(int column)
		{
			return mWrapped.isNull(column);
		}
	}

	public static void show(Context context, AlbumItem album)
	{
		Intent chosen = new Intent(Intent.ACTION_VIEW, album.getUri(), context, SongList.class);
		chosen.putExtra("artistId", album.getArtistId());
		chosen.putExtra("artistName", album.getArtist());
		chosen.putExtra("albumId", album.getId());
		chosen.putExtra("albumName", album.getName());
		chosen.putExtra("albumArtwork", album.getArtworkThumbUri() != null ? album.getArtworkThumbUri().toString() : null);
		chosen.putExtra("albumArtworkBig", album.getArtworkFullUri() != null ? album.getArtworkFullUri().toString() : null);
		context.startActivity(chosen);
	}
}
