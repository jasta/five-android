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

import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.AlbumItem;
import org.devtcg.five.provider.util.ArtistItem;
import org.devtcg.five.widget.AbstractDAOItemAdapter;
import org.devtcg.five.widget.AbstractMainListActivity.OptionsMenuHelper;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class ArtistAlbumList extends ListActivity
{
	private final OptionsMenuHelper mMenuHelper = new OptionsMenuHelper(this);

	public static void show(Context context, ArtistItem item)
	{
		/*
		 * As a convenience to the user, if the artist has only 1 album, just
		 * move directly to the song list for that album.
		 */
		if (item.getNumAlbums() == 1)
		{
			AlbumItem album = AlbumItem.getInstance(context, Five.makeArtistAlbumsUri(item.getUri()));
			try {
				SongList.showByAlbum(context, album);
			} finally {
				album.close();
			}
		}
		else
		{
			Intent chosen = new Intent(Intent.ACTION_VIEW, item.getUri(), context, ArtistAlbumList.class);
			chosen.putExtra(Constants.EXTRA_ARTIST_ID, item.getId());
			chosen.putExtra(Constants.EXTRA_ARTIST_NAME, item.getName());
			context.startActivity(chosen);
		}
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		Intent intent = getIntent();
		Uri artistUri = intent.getData();

		setTitle(intent.getStringExtra(Constants.EXTRA_ARTIST_NAME));

		getListView().addHeaderView(LayoutInflater.from(this).inflate(R.layout.all_albums_item,
				getListView(), false));
		setListAdapter(new ArtistAlbumAdapter(this, Five.makeArtistAlbumsUri(artistUri)));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		return mMenuHelper.dispatchOnCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		return mMenuHelper.dispatchOnOptionsItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView list, View view, int position, long id)
	{
		AlbumItem data = (AlbumItem)list.getItemAtPosition(position);
		if (data != null)
			SongList.showByAlbum(this, data);
		else
		{
			SongList.showByArtist(this, getIntent().getData(),
					getIntent().getStringExtra(Constants.EXTRA_ARTIST_NAME));
		}
	}

	private static class ArtistAlbumAdapter extends AbstractDAOItemAdapter<AlbumItem>
	{
		public ArtistAlbumAdapter(Activity context, Uri uri)
		{
			super(context, R.layout.artist_album_list_item,
					context.managedQuery(uri, null, null, null,
							"a." + Five.Music.Albums.RELEASE_DATE + ", " +
							"a." + Five.Music.Albums.NAME + " COLLATE UNICODE"),
					true);
		}

		@Override
		protected void onAttachItemDAO(Cursor cursor)
		{
			mItemDAO = new AlbumItem(cursor);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor)
		{
			ImageView badge = (ImageView)view.findViewById(R.id.album_cover);
			Uri artwork = mItemDAO.getArtworkThumbUri();
			if (artwork != null)
				badge.setImageURI(artwork);
			else
				badge.setImageResource(R.drawable.lastfm_cover_small);

			TextView name = (TextView)view.findViewById(R.id.album_name);
			name.setText(mItemDAO.getName());

			TextView counts = (TextView)view.findViewById(R.id.album_counts);
			counts.setText(getAlbumCounts());
		}

		private String getAlbumCounts()
		{
			int nsongs = mItemDAO.getNumSongs();

			StringBuilder b = new StringBuilder();

			b.append(nsongs).append(" song");
			if (nsongs != 1)
				b.append('s');

			return b.toString();
		}
	}
}
