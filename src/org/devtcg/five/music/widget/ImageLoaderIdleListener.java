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

package org.devtcg.five.music.widget;

import org.devtcg.five.music.util.ImageMemCache;
import org.devtcg.five.music.widget.IdleListDetector.OnListIdleListener;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.CursorAdapter;
import android.widget.ImageView;

/**
 * Useful common implementation of OnListIdleListener which handles
 * loading images that temporarily defaulted during a fling.  Utilizes
 * a mem cache to further enhance performance.
 */
public class ImageLoaderIdleListener implements OnListIdleListener
{
	private final Context mContext;
	
	private final AbsListView mList;
	private final CursorAdapter mAdapter;
	private final int mImageColumnIndex;
	
	private final ImageMemCache mCache;

	private static final int TRANSITION_DURATION = 175;
	
	public ImageLoaderIdleListener(Context ctx,
	  AbsListView list, ImageMemCache cache)
	{
		mContext = ctx;
		mList = list;
		mCache = cache;

		if (!(list.getAdapter() instanceof CursorAdapter))
			throw new IllegalArgumentException("ListView adapter must be a subclass of CursorAdapter.");

		mAdapter = (CursorAdapter)list.getAdapter();

		/* Hmm, this is pretty kludgey. */
		if (!(list.getAdapter() instanceof ImageLoaderAdapter))
			throw new IllegalArgumentException("ListView adapter must implement ImageLoaderAdapter.");
		
		mImageColumnIndex = 
		  ((ImageLoaderAdapter)list.getAdapter()).getImageColumnIndex();
	}

	public void onListIdle()
	{
		int first = mList.getFirstVisiblePosition();
		int n = mList.getChildCount();

		for (int i = 0; i < n; i++)
		{
			View row = mList.getChildAt(i);
			ImageLoaderHolder holder = (ImageLoaderHolder)row.getTag();
			
			if (holder.isTemporaryBind() == true)
			{
				Cursor c = (Cursor)mAdapter.getItem(first + i);
				FastBitmapDrawable d =
				  mCache.fetchFromDatabase(holder.getItemId(),
				    mContext, c, mImageColumnIndex);

				if (d != mCache.getFallback())
				{
					CrossFadeDrawable transition =
					  holder.getTransitionDrawable();
					transition.setEnd(d.getBitmap());
					holder.getImageLoaderView().setImageDrawable(transition);
					transition.startTransition(TRANSITION_DURATION);
				}

				holder.setTemporaryBind(false);
			}
		}

		mList.invalidate();
	}

	public interface ImageLoaderAdapter
	{
		public int getImageColumnIndex();
	}

	public interface ImageLoaderHolder
	{
		public Long getItemId();
		public boolean isTemporaryBind();
		public void setTemporaryBind(boolean temp);
		public ImageView getImageLoaderView();
		public CrossFadeDrawable getTransitionDrawable();
	}
}
