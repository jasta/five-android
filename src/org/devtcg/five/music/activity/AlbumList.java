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

import java.lang.ref.SoftReference;
import java.util.HashMap;

import org.devtcg.five.R;
import org.devtcg.five.music.util.BetterBitmapFactory;
import org.devtcg.five.music.widget.AlphabetIndexer;
import org.devtcg.five.music.widget.FastScrollView;
import org.devtcg.five.provider.Five;
import org.devtcg.five.widget.EfficientCursorAdapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.AdapterView.OnItemClickListener;

public class AlbumList extends Activity
{
	private static final String TAG = "AlbumList";

	private static final String QUERY_FIELDS[] =
	  { Five.Music.Albums._ID, Five.Music.Albums.NAME,
		Five.Music.Albums.FULL_NAME, Five.Music.Albums.ARTWORK,
		Five.Music.Albums.ARTIST, Five.Music.Albums.ARTWORK_BIG,
		Five.Music.Albums.ARTIST_ID };

	private AlbumAdapter mAdapter;

	private HashMap<Long, SoftReference<Bitmap>> mArtworkCache =
	  new HashMap<Long, SoftReference<Bitmap>>();

	private Cursor getCursor(String selection, String[] args)
	{
		return getContentResolver().query(getIntent().getData(),
		  QUERY_FIELDS, selection, args, "a.name COLLATE UNICODE ASC");
	}

	public static void show(Context context)
	{
		context.startActivity(new Intent(context, AlbumList.class));
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.album_list);

		Intent i = getIntent();
		if (i.getData() == null)
			i.setData(Five.Music.Albums.CONTENT_URI_COMPLETE);

		if (i.getAction() == null)
			i.setAction(Intent.ACTION_VIEW);

		Cursor c = getCursor(null, null);

		ListView list = (ListView)findViewById(R.id.album_list);

		mAdapter = new AlbumAdapter(this,
		  R.layout.album_list_item, c,
		  new String[] { "artwork", "full_name", "artist" },
		  new int[] { R.id.album_cover, R.id.album_name, R.id.artist_name });

		list.setAdapter(mAdapter);
		list.setOnItemClickListener(mClickEvent);
		list.setTextFilterEnabled(true);
	}

	@Override
	protected void onDestroy()
	{
		mArtworkCache.clear();
		mAdapter.changeCursor(null);
		super.onDestroy();
	}

	private final OnItemClickListener mClickEvent = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> av, View v, int pos, long id)
		{
			Intent i = getIntent();

			Intent chosen = new Intent();

			chosen.setData(Five.Music.Albums.CONTENT_URI.buildUpon()
			  .appendPath(String.valueOf(id))
			  .build());

			Cursor c = (Cursor)av.getItemAtPosition(pos);

			chosen.putExtra("artistId",
			  c.getLong(c.getColumnIndexOrThrow(Five.Music.Albums.ARTIST_ID)));
			chosen.putExtra("artistName",
			  c.getString(c.getColumnIndexOrThrow(Five.Music.Albums.ARTIST)));
			chosen.putExtra("albumId", id);
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
				chosen.setClass(AlbumList.this, SongList.class);
				startActivity(chosen);
			}
		}
	};

	private class AlbumAdapter extends EfficientCursorAdapter
	  implements FilterQueryProvider, ViewBinder,
	  FastScrollView.SectionIndexer
	{
		private AlphabetIndexer mIndexer;

		public AlbumAdapter(Context context, int layout, Cursor c,
		  String[] from, int[] to)
		{
			super(context, layout, c, from, to);
			setFilterQueryProvider(this);
			setViewBinder(this);

			mIndexer = new AlphabetIndexer(context, c,
			  c.getColumnIndexOrThrow(Five.Music.Albums.NAME));
		}

		public boolean setViewValue(View v, Cursor c, int col)
		{
			if (QUERY_FIELDS[col] == Five.Music.Albums.ARTWORK)
			{
				Long id = c.getLong(0);

				ImageView vv = (ImageView)v;

				Bitmap bmp = null;
				SoftReference<Bitmap> hit = mArtworkCache.get(id);
				if (hit != null)
					bmp = hit.get();

				if (bmp == null)
				{
					bmp = BetterBitmapFactory.decodeUriWithFallback(AlbumList.this,
			    	  c.getString(col), R.drawable.albumart_mp_unknown);
					mArtworkCache.put(id, new SoftReference<Bitmap>(bmp));
				}

				vv.setImageBitmap(bmp);

				return true;
			}

			return false;
		}

		public Cursor runQuery(CharSequence constraint)
		{
			String sel = null;
			String[] args = null;

			Log.d(TAG, "runQuery: " + constraint);

			if (TextUtils.isEmpty(constraint) == false)
			{
				sel = "UPPER(a.name) GLOB ?";

				String wildcard = constraint.toString().replace(' ', '*');
				args = new String[] { "*" + wildcard.toUpperCase() + "*" };
			}

			return AlbumList.this.getCursor(sel, args);
		}

		@Override
		public void changeCursor(Cursor cursor)
		{
			super.changeCursor(cursor);
			mIndexer.setCursor(cursor);
		}

		public int getPositionForSection(int section)
		{
			return mIndexer.indexOf(section);
		}

		public int getSectionForPosition(int position)
		{
			return 0;
		}

		public Object[] getSections()
		{
			return mIndexer.getSections();
		}
	}
}
