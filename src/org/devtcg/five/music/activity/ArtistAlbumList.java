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
import org.devtcg.five.provider.Five;
import org.devtcg.five.widget.EfficientCursorAdapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class ArtistAlbumList extends Activity
  implements ViewBinder
{
	public static final String TAG = "ArtistAlbumList";

	private static final String[] QUERY_FIELDS = 
	  { Five.Music.Albums._ID, Five.Music.Albums.NAME,
	    Five.Music.Albums.FULL_NAME, Five.Music.Albums.ARTWORK,
	    Five.Music.Albums.NUM_SONGS, Five.Music.Albums.ARTWORK_BIG };

	private Cursor mCursor;
	
	private static final int MENU_RETURN_LIBRARY = Menu.FIRST;
	private static final int MENU_GOTO_PLAYER = Menu.FIRST + 1;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.artist_album_list);
	
		Intent artistIntent = getIntent();
		Uri artistUri = artistIntent.getData();
	
		if (artistUri == null)
			throw new IllegalArgumentException("No artist given, cannot do thy bidding");
	
		String artistName = artistIntent.getStringExtra("artistName");
		setTitle(artistName);
	
		Uri albumsUri = artistUri.buildUpon()
		  .appendPath("albums").build();
	
		mCursor = managedQuery(albumsUri,
		  QUERY_FIELDS, null, null, "a.name ASC");
	
		/* Convenience for when there is only one album by this artist. */
		if (mCursor.getCount() == 1)
		{
			mCursor.moveToFirst();
			chooseAlbum(mCursor, true);
			return;
		}
	
		ListView list = (ListView)findViewById(android.R.id.list);
	
		EfficientCursorAdapter adapter = new EfficientCursorAdapter(this,
		  R.layout.artist_album_list_item, mCursor,
		  new String[] { "artwork", "full_name", "num_songs" },
		  new int[] { R.id.album_cover, R.id.album_name, R.id.album_counts });
	
		adapter.setViewBinder(this);
	
		list.addHeaderView(AllAlbumsView.getListView(this));
		list.setOnItemClickListener(mOnItemClickListener);
		list.setAdapter(adapter);
	}

	private void chooseAlbum(Cursor c, boolean shouldFinish)
	{
		long id = c.getLong(0);
		
		Intent i = getIntent();

		Intent chosen = new Intent();

		chosen.setData(i.getData().buildUpon()
		  .appendEncodedPath("albums")
		  .appendEncodedPath(String.valueOf(id))
		  .build());

		chosen.putExtra("artistId",
		  i.getLongExtra("artistId", -1));
		chosen.putExtra("artistName",
		  i.getStringExtra("artistName"));
		chosen.putExtra("albumId",
		  c.getLong(c.getColumnIndexOrThrow(Five.Music.Albums._ID)));
		chosen.putExtra("albumName",
		  c.getString(c.getColumnIndexOrThrow(Five.Music.Albums.FULL_NAME)));
		chosen.putExtra("albumArtwork",
		  c.getString(c.getColumnIndexOrThrow(Five.Music.Albums.ARTWORK)));
		chosen.putExtra("albumArtworkBig",
		  c.getString(c.getColumnIndexOrThrow(Five.Music.Albums.ARTWORK_BIG)));

		String action = i.getAction();

		if (action.equals(Intent.ACTION_PICK) == true ||
		    action.equals(Intent.ACTION_GET_CONTENT) == true)
		{
			setResult(RESULT_OK, chosen);
			finish();
		}
		else
		{
			chosen.setAction(Intent.ACTION_VIEW);
			chosen.setClass(this, SongList.class);
			startActivity(chosen);

			if (shouldFinish == true)
				finish();
		}    	
	}

	private void chooseAllAlbums()
	{
		Intent i = getIntent();

		Intent chosen = new Intent();

		chosen.setData(i.getData());

		chosen.putExtra("artistId",
		  i.getLongExtra("artistId", -1));
		chosen.putExtra("artistName",
		  i.getStringExtra("artistName"));
		chosen.putExtra("allAlbums", true);

		chosen.setAction(Intent.ACTION_VIEW);
		chosen.setClass(this, SongList.class);
		startActivity(chosen);
	}

	private OnItemClickListener mOnItemClickListener = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int pos, long id)
		{
			if (id == -1)
				chooseAllAlbums();
			else
				chooseAlbum((Cursor)av.getItemAtPosition(pos), false);
		}
	};

	public boolean setViewValue(View view, Cursor c, int col)
	{
		if (QUERY_FIELDS[col] == Five.Music.Albums.NUM_SONGS)
		{
			int nsongs = c.getInt(col);

			StringBuilder b = new StringBuilder();

			b.append(nsongs).append(" song");
			if (nsongs != 1)
				b.append('s');

			((TextView)view).setText(b.toString());

			return true;
		}
		else if (QUERY_FIELDS[col] == Five.Music.Albums.ARTWORK)
		{
			ImageView vv = (ImageView)view;
			
			if (c.isNull(col) == true)
				vv.setImageResource(R.drawable.albumart_mp_unknown);
			else
				vv.setImageURI(Uri.parse(c.getString(col)));
	
			return true;
		}
		
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		
		menu.add(0, MENU_RETURN_LIBRARY, 0, R.string.return_library)
		  .setIcon(R.drawable.ic_menu_music_library);
		menu.add(0, MENU_GOTO_PLAYER, 0, R.string.goto_player)
		  .setIcon(R.drawable.ic_menu_playback);
		
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
//		menu.findItem(MENU_GOTO_PLAYER).setVisible(mSongPlaying >= 0);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case MENU_RETURN_LIBRARY:
			Main.show(this);
			return true;
		case MENU_GOTO_PLAYER:
			Player.show(this);
			return true;
		}

		return false;
	}	
	
	private static class AllAlbumsView extends LinearLayout
	{
		public AllAlbumsView(Context context)
		{
			super(context);
			init();
		}

		private void init()
		{
			LayoutInflater.from(getContext())
			  .inflate(R.layout.all_albums_item, this);
		}

		public static AllAlbumsView getListView(Context ctx)
		{
			AllAlbumsView header = new AllAlbumsView(ctx);
			header.setLayoutParams(new AbsListView.LayoutParams
			  (AbsListView.LayoutParams.FILL_PARENT,
			   AbsListView.LayoutParams.WRAP_CONTENT));
			return header;
		}
	}
}
