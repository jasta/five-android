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
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class ArtistList extends Activity implements ViewBinder
{
	private static final String TAG = "ArtistList";

	private static final String PROJECTION[] = 
	  { Five.Music.Artists._ID, Five.Music.Artists.NAME,
		Five.Music.Artists.FULL_NAME, Five.Music.Artists.PHOTO,
		Five.Music.Artists.NUM_ALBUMS, Five.Music.Artists.NUM_SONGS };

	private ListView mList;
	private ArtistAdapter mAdapter;

	private HashMap<Long, SoftReference<Bitmap>> mPhotoCache =
	  new HashMap<Long, SoftReference<Bitmap>>();
	
	private static final int MENU_RETURN_LIBRARY = Menu.FIRST;
	private static final int MENU_GOTO_PLAYER = Menu.FIRST + 1;

	private Cursor getCursor(String selection, String[] args)
	{
		String where = Five.Music.Artists.NUM_SONGS + " > 0";
		
		if (TextUtils.isEmpty(selection) == false)
			where = where + " AND (" + selection + ")";

		return getContentResolver().query(getIntent().getData(), PROJECTION,
		  where, args, Five.Music.Artists.NAME + " COLLATE UNICODE ASC");
	}
	
	public static void show(Context context)
	{
		context.startActivity(new Intent(context, ArtistList.class));
	}

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setContentView(R.layout.artist_list);

        Intent i = getIntent();
        if (i.getData() == null)
        	i.setData(Five.Music.Artists.CONTENT_URI);

        if (i.getAction() == null)
        	i.setAction(Intent.ACTION_VIEW);

        Cursor c = getCursor(null, null);

		mList = (ListView)findViewById(R.id.artist_list);

		mAdapter = new ArtistAdapter(this,
		  R.layout.artist_list_item, c,
		  new String[] { "photo", "full_name", "num_albums" },
		  new int[] { R.id.artist_photo, R.id.artist_name, R.id.artist_counts });

		mAdapter.setViewBinder(this);		
		mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(mClickEvent);
        mList.setTextFilterEnabled(true);
    }
    
    @Override
    protected void onDestroy()
    {
    	mPhotoCache.clear();
    	mAdapter.changeCursor(null);
    	super.onDestroy();
    }

	public boolean setViewValue(View view, Cursor c, int col)
	{
		if (PROJECTION[col] == Five.Music.Artists.NUM_ALBUMS)
		{
			int nalbums = c.getInt(col);
			int nsongs = c.getInt(c.getColumnIndexOrThrow("num_songs"));

			StringBuilder b = new StringBuilder();

			b.append(nalbums).append(" album");
			if (nalbums != 1)
				b.append('s');
			b.append(", ");
			b.append(nsongs).append(" song");
			if (nsongs != 1)
				b.append('s');

			((TextView)view).setText(b.toString());

			return true;
		}
		else if (PROJECTION[col] == Five.Music.Artists.PHOTO)
		{
			Long id = c.getLong(0);

    		ImageView vv = (ImageView)view;
    		
    		Bitmap bmp = null;
    		SoftReference<Bitmap> hit = mPhotoCache.get(id);
    		if (hit != null)
    			bmp = hit.get();

    		if (bmp == null)
    		{
    			bmp = BetterBitmapFactory.decodeUriWithFallback(this,
    			  c.getString(col), R.drawable.picture_contact_placeholder);
    			mPhotoCache.put(id, new SoftReference<Bitmap>(bmp));
    		}

    		vv.setImageBitmap(bmp);

			return true;
		}

		return false;
	}

    private OnItemClickListener mClickEvent = new OnItemClickListener()
    {
		public void onItemClick(AdapterView<?> adapter, View v, int pos, long id)
		{
			/* Clicked on a separator row. */
			if (id == -1)
				return;

			Intent i = getIntent();

			Intent chosen = new Intent();
			chosen.setData(i.getData().buildUpon()
			  .appendPath(String.valueOf(id))
			  .build());

			Cursor c = (Cursor)adapter.getItemAtPosition(pos);
			chosen.putExtra("artistId", id);
			chosen.putExtra("artistName",
			  c.getString(c.getColumnIndexOrThrow(Five.Music.Artists.NAME)));
			chosen.putExtra("artistPhoto",
			  c.getString(c.getColumnIndexOrThrow(Five.Music.Artists.PHOTO)));

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
				chosen.setClass(ArtistList.this, ArtistAlbumList.class);
				startActivity(chosen);
			}
		}
    };

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
    
    private class ArtistAdapter extends EfficientCursorAdapter
      implements FilterQueryProvider, FastScrollView.SectionIndexer
    {
    	private final AlphabetIndexer mIndexer;

		public ArtistAdapter(Context context, int layout, Cursor c,
		  String[] from, int[] to)
		{
			super(context, layout, c, from, to);
			setFilterQueryProvider(this);

			mIndexer = new AlphabetIndexer(context, c,
			  c.getColumnIndexOrThrow(Five.Music.Artists.NAME));
		}

		public Cursor runQuery(CharSequence constraint)
		{
			String sel = null;
			String[] args = null;

			Log.d(TAG, "runQuery: " + constraint);

			if (TextUtils.isEmpty(constraint) == false)
			{
				sel = "UPPER(name) GLOB ?";

				String wildcard = constraint.toString().replace(' ', '*');
				args = new String[] { "*" + wildcard.toUpperCase() + "*" };
			}

			return ArtistList.this.getCursor(sel, args);
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
