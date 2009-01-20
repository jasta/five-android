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
import org.devtcg.five.music.widget.IdleListDetector.OnListIdleListener;
import org.devtcg.five.music.util.BetterBitmapFactory;
import org.devtcg.five.music.util.ImageMemCache;
import org.devtcg.five.music.widget.AlphabetIndexer;
import org.devtcg.five.music.widget.CrossFadeDrawable;
import org.devtcg.five.music.widget.FastBitmapDrawable;
import org.devtcg.five.music.widget.FastScrollView;
import org.devtcg.five.music.widget.IdleListDetector;
import org.devtcg.five.provider.Five;
import org.devtcg.five.widget.EfficientCursorAdapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;

public class AlbumList extends Activity
{
	private static final String TAG = "AlbumList";

	private static final String QUERY_FIELDS[] =
	  { Five.Music.Albums._ID, Five.Music.Albums.NAME,
		Five.Music.Albums.FULL_NAME, Five.Music.Albums.ARTWORK,
		Five.Music.Albums.ARTIST, Five.Music.Albums.ARTWORK_BIG,
		Five.Music.Albums.ARTIST_ID };
	
	private static final int ARTWORK_TRANSITION_DURATION = 175;

	private ListView mList;
	private FastScrollView mFastScroller;
	private AlbumAdapter mAdapter;
	private IdleListDetector mImageLoader;

	private static final ImageMemCache mCache = new ImageMemCache();

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

		if (mCache.getFallback() == null)
			mCache.setFallback(getResources(), R.drawable.albumart_mp_unknown);

		Intent i = getIntent();
		if (i.getData() == null)
			i.setData(Five.Music.Albums.CONTENT_URI_COMPLETE);

		if (i.getAction() == null)
			i.setAction(Intent.ACTION_VIEW);

		Cursor c = getCursor(null, null);

		ListView list = (ListView)findViewById(R.id.album_list);

		mAdapter = new AlbumAdapter(c);

		mImageLoader = new IdleListDetector(mListIdleListener);
		mFastScroller = (FastScrollView)list.getParent();
		mFastScroller.setOnIdleListDetector(mImageLoader);

		list.setAdapter(mAdapter);
		list.setOnItemClickListener(mClickEvent);
		list.setTextFilterEnabled(true);
		list.setOnScrollListener(mScrollListener);
		list.setOnTouchListener(mTouchListener);

		mList = list;
	}

	@Override
	protected void onDestroy()
	{
		mCache.cleanup();
		mAdapter.changeCursor(null);
		super.onDestroy();
	}
	
	private final OnListIdleListener mListIdleListener =
	  new OnListIdleListener()
	{
		public void onListIdle()
        {
			int first = mList.getFirstVisiblePosition();
			int n = mList.getChildCount();

			Log.i(TAG, String.format("onListIdle(%d, %d)", first, n));

			for (int i = 0; i < n; i++)
			{
				View row = mList.getChildAt(i);
				AlbumViewHolder holder = (AlbumViewHolder)row.getTag();

				if (holder.tempBind == true)
				{
					Cursor c = (Cursor)mAdapter.getItem(first + i);
					FastBitmapDrawable d =
					  mAdapter.getCachedArtwork(holder.albumId, c);

					if (d != mCache.getFallback())
					{
						CrossFadeDrawable transition = holder.transition;
						transition.setEnd(d.getBitmap());
						holder.albumArtwork.setImageDrawable(transition);
						transition.startTransition(ARTWORK_TRANSITION_DURATION);
					}

					holder.tempBind = false;
				}
			}

			mList.invalidate();
        }
	};

	private final OnScrollListener mScrollListener = new OnScrollListener()
	{
		public void onScroll(AbsListView view, int firstVisibleItem,
		  int visibleItemCount, int totalItemCount)
		{
			mFastScroller.onScroll(view, firstVisibleItem,
			  visibleItemCount, totalItemCount);
		}

		public void onScrollStateChanged(AbsListView view, int scrollState)
        {
			mImageLoader.onScrollStateChanged(view, scrollState);
        }
	};
	
	private final OnTouchListener mTouchListener = new OnTouchListener()
	{
		public boolean onTouch(View v, MotionEvent event)
        {
	        return mImageLoader.onTouch(v, event);
        }
	};

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

	private class AlbumAdapter extends CursorAdapter
	  implements FilterQueryProvider, FastScrollView.SectionIndexer
	{
		private final AlphabetIndexer mIndexer;
		private final LayoutInflater mInflater;

		private final int mIdIdx;
		private final int mAlbumArtworkIdx;
		private final int mAlbumNameIdx;
		private final int mArtistNameIdx;
		private final FastBitmapDrawable mDefaultArtwork;

		public AlbumAdapter(Cursor c)
		{
			super(AlbumList.this, c);
			setFilterQueryProvider(this);

			mInflater = LayoutInflater.from(AlbumList.this);
			mIdIdx = c.getColumnIndexOrThrow(Five.Music.Albums._ID);
			mAlbumArtworkIdx = c.getColumnIndexOrThrow(Five.Music.Albums.ARTWORK);
			mAlbumNameIdx = c.getColumnIndexOrThrow(Five.Music.Albums.NAME);
			mArtistNameIdx = c.getColumnIndexOrThrow(Five.Music.Albums.ARTIST);

			Bitmap bmp = BitmapFactory.decodeResource(getResources(),
			  R.drawable.albumart_mp_unknown);
			mDefaultArtwork = new FastBitmapDrawable(bmp); 

			mIndexer = new AlphabetIndexer(AlbumList.this, c,
			  c.getColumnIndexOrThrow(Five.Music.Albums.NAME));
		}

		private FastBitmapDrawable getCachedArtwork(Long id, Cursor c)
		{
			return mCache.fetchFromDatabase(id, AlbumList.this,
			  c, mAlbumArtworkIdx);
		}

		@Override
        public View newView(Context context, Cursor cursor, ViewGroup parent)
        {
	        View row = mInflater.inflate(R.layout.album_list_item,
	          parent, false);

	        AlbumViewHolder holder = new AlbumViewHolder();
	        row.setTag(holder);

	        holder.artistName = (TextView)row.findViewById(R.id.artist_name);
	        holder.albumName = (TextView)row.findViewById(R.id.album_name);
	        holder.albumArtwork = (ImageView)row.findViewById(R.id.album_cover);

	        CrossFadeDrawable transition =
	          new CrossFadeDrawable(mDefaultArtwork.getBitmap(), null);
	        transition.setCrossFadeEnabled(true);
	        holder.transition = transition;

	        return row;
        }
		
		private void setCursorText(Cursor c, TextView view, int idx,
		  CharArrayBuffer buf)
		{
			c.copyStringToBuffer(idx, buf);
			int n = buf.sizeCopied;
			if (n > 0)
				view.setText(buf.data, 0, n);
		}

		@Override
        public void bindView(View view, Context context, Cursor cursor)
        {
			AlbumViewHolder holder = (AlbumViewHolder)view.getTag();

			holder.albumId = cursor.getLong(mIdIdx);

			if (mImageLoader.isListIdle() == true)
			{
				holder.albumArtwork
				  .setImageDrawable(getCachedArtwork(holder.albumId, cursor));
				holder.tempBind = false;
			}
			else
			{
				holder.albumArtwork.setImageDrawable(mDefaultArtwork);
				holder.tempBind = true;
			}

			setCursorText(cursor, holder.albumName, mAlbumNameIdx,
			  holder.albumBuffer);
			setCursorText(cursor, holder.artistName, mArtistNameIdx,
			  holder.artistBuffer);
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

	public static class AlbumViewHolder
	{
		long albumId;
		ImageView albumArtwork;
		TextView albumName;
		TextView artistName;
		boolean tempBind;
		CrossFadeDrawable transition;
		final CharArrayBuffer albumBuffer = new CharArrayBuffer(64);
		final CharArrayBuffer artistBuffer = new CharArrayBuffer(64);
	}
}
