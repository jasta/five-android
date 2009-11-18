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

package org.devtcg.five.activity;

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class PlaylistList extends Activity implements ViewBinder
{
	public static final String TAG = "Playlists";
	
	private ListView mList;
	private EfficientCursorAdapter mAdapter;
	
	private boolean mCreateShortcut = false;

	private static final String QUERY_FIELDS[] =
	  { Five.Music.Playlists._ID, Five.Music.Playlists.NAME,
		Five.Music.Playlists.NUM_SONGS };
	
	private static final int MENU_RETURN_LIBRARY = Menu.FIRST;
	private static final int MENU_GOTO_PLAYER = Menu.FIRST + 1;
	
	private Cursor getCursor(String sel, String[] args)
	{
		return getContentResolver().query(getIntent().getData(),
		  QUERY_FIELDS, sel, args, Five.Music.Playlists.NAME + " ASC");
	}
	
	public static void show(Context context)
	{
		context.startActivity(new Intent(context, PlaylistList.class));
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.lastfm);

		Intent i = getIntent();
		if (i.getData() == null)
			i.setData(Five.Music.Playlists.CONTENT_URI);

		String action = i.getAction();
		if (action == null)
			i.setAction(Intent.ACTION_VIEW);
		else if (action.equals(Intent.ACTION_CREATE_SHORTCUT) == true)
			mCreateShortcut = true;

		Cursor c = getCursor(null, null);

		mList = (ListView)findViewById(android.R.id.list);

		mAdapter = new EfficientCursorAdapter(this,
		  R.layout.playlist_item, c,
		  new String[] { Five.Music.Playlists.NAME, Five.Music.Playlists.NUM_SONGS },
		  new int[] { R.id.playlist_name, R.id.playlist_counts });

		mAdapter.setViewBinder(this);
		mList.setAdapter(mAdapter);
		mList.setOnItemClickListener(mItemClick);
	}
	
	@Override
	public void onDestroy()
	{
		mAdapter.changeCursor(null);
		super.onDestroy();
	}

	public boolean setViewValue(View v, Cursor c, int col)
	{
		if (QUERY_FIELDS[col] == Five.Music.Playlists.NUM_SONGS)
		{
			int nsongs = c.getInt(col);
			
			StringBuilder b = new StringBuilder();
			
			b.append(nsongs).append(" song");
			if (nsongs != 1)
				b.append('s');
			
			((TextView)v).setText(b.toString());
			
			return true;
		}
		
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		
		if (mCreateShortcut == false)
		{
			menu.add(0, MENU_RETURN_LIBRARY, 0, R.string.return_library)
			.setIcon(R.drawable.ic_menu_music_library);
			menu.add(0, MENU_GOTO_PLAYER, 0, R.string.goto_player)
			.setIcon(R.drawable.ic_menu_playback);
		}
		
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
    
	private OnItemClickListener mItemClick = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int pos, long id)
		{
			Intent i = getIntent();

			Intent chosen = new Intent();

			chosen.setDataAndType(i.getData().buildUpon()
			  .appendEncodedPath(String.valueOf(id)).build(),
			  Five.Music.Playlists.CONTENT_ITEM_TYPE);

			Cursor c = (Cursor)av.getItemAtPosition(pos);

			chosen.putExtra("playlistId", id);
			
			String playlistName =
			  c.getString(c.getColumnIndexOrThrow(Five.Music.Playlists.NAME));
			chosen.putExtra("playlistName", playlistName);

			chosen.setAction(Intent.ACTION_VIEW);
			
			if (mCreateShortcut == true)
			{
				Intent shortcut = new Intent();
				shortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT, chosen);
				shortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, playlistName);
				shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
				  Intent.ShortcutIconResource.fromContext(PlaylistList.this,
				    R.drawable.ic_launcher_shortcut_music_playlist));

	            setResult(RESULT_OK, shortcut);
	            finish();
			}
			else
			{
				chosen.setClass(PlaylistList.this, SongList.class);
				startActivity(chosen);
			}
		}
	};
}
