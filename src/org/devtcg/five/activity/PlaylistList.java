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

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.PlaylistItem;
import org.devtcg.five.widget.AbstractDAOItemAdapter;
import org.devtcg.five.widget.AbstractMainListActivity.OptionsMenuHelper;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

public class PlaylistList extends ListActivity
{
	public static final String TAG = "Playlists";

	private boolean mCreateShortcut = false;

	private static final String[] sProjection = {
		Five.Music.Playlists._ID, Five.Music.Playlists.NAME,
		Five.Music.Playlists.NUM_SONGS
	};

	private final OptionsMenuHelper mMenuHelper = new OptionsMenuHelper(this);

	public static void show(Context context)
	{
		context.startActivity(new Intent(context, PlaylistList.class));
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction()))
			mCreateShortcut = true;

		setListAdapter(new PlaylistListAdapter(this));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if (!mCreateShortcut)
			return mMenuHelper.dispatchOnCreateOptionsMenu(menu);
		else
			return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		return mMenuHelper.dispatchOnOptionsItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id)
	{
		PlaylistItem item = (PlaylistItem)list.getItemAtPosition(position);

		if (!mCreateShortcut)
			SongList.showByPlaylist(this, item);
		else
		{
			Intent shortcut = new Intent();
			shortcut.putExtra(Intent.EXTRA_SHORTCUT_INTENT,
					SongList.makeShowByPlaylistIntent(this, item));
			shortcut.putExtra(Intent.EXTRA_SHORTCUT_NAME, item.getName());
			shortcut.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
					ShortcutIconResource.fromContext(this,
							R.drawable.ic_launcher_shortcut_music_playlist));

			setResult(RESULT_OK, shortcut);
			finish();
		}
	}

	private static class PlaylistListAdapter extends AbstractDAOItemAdapter<PlaylistItem>
	{
		public PlaylistListAdapter(Activity context)
		{
			super(context, R.layout.playlist_item,
					context.managedQuery(Five.Music.Playlists.CONTENT_URI,
							sProjection, null, null, Five.Music.Playlists.NAME + " ASC"));
		}

		@Override
		protected PlaylistItem newItemDAO(Cursor cursor)
		{
			return new PlaylistItem(cursor);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			TextView playlistNameView = (TextView)view.findViewById(R.id.playlist_name);
			playlistNameView.setText(mItemDAO.getName());

			TextView playlistCountsView = (TextView)view.findViewById(R.id.playlist_counts);
			playlistCountsView.setText(getSongCounts(mItemDAO, new StringBuilder()));
		}

		private static String getSongCounts(PlaylistItem playlist, StringBuilder buffer)
		{
			int nsongs = playlist.getNumSongs();

			buffer.append(nsongs).append(" song");
			if (nsongs != 1)
				buffer.append('s');

			return buffer.toString();
		}
	}
}
