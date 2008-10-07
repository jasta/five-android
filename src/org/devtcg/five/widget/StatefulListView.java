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

package org.devtcg.five.widget;

import java.util.HashMap;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Special ListView class which can efficiently map adapter row ids to
 * list positions.  This can be useful when UIs need to introduce stateful
 * UI changes not backed by the underlying adapter data set. 
 */
public class StatefulListView extends ListView
{
	/** Cache of searches to resolve row positions by adapter row id. */
	protected HashMap<Long, Integer> mViewMapCache = null;
	
	public StatefulListView(Context context)
	{
		super(context);
	}

	public StatefulListView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	public void setAdapter(ListAdapter adapter)
	{
		super.setAdapter(adapter);
		adapter.registerDataSetObserver(mDataSetChanged);
	}

	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();
		getAdapter().unregisterDataSetObserver(mDataSetChanged);
	}

	private final DataSetObserver mDataSetChanged = new DataSetObserver()
	{
		@Override
		public void onChanged()
		{
			/* The cache must be cleared in the event that our ordering changed.
			 * For example, if a new entry was inserted somewhere in the list
			 * before our cached positions. */
			if (mViewMapCache != null)
				mViewMapCache.clear();
		}

		@Override
		public void onInvalidated()
		{
			onChanged();
		}
	};

	private int lazyListSearchForId(long id)
	{
		ListAdapter adapter = getAdapter();
		int start = getFirstVisiblePosition();
		int count = getChildCount();
		
		assert adapter.getCount() > start + count;

		for (int i = start; i < start + count; i++)
		{
			if (adapter.getItemId(i) == id)
				return i;
		}
		
		return -1;
	}
	
	public int getRowFromId(long id)
	{
		if (mViewMapCache == null)
			mViewMapCache = new HashMap<Long, Integer>();

		Integer row = mViewMapCache.get(id);

		/* Damn, have to search for the list position */
		if (row == null)
		{
			if ((row = lazyListSearchForId(id)) == -1)
				return -1;

			mViewMapCache.put(id, row);
		}
		
		return row;
	}

	/**
	 * Efficiently locate the row view by data set id.  Only "visible" views
	 * can be located this way, as the list view recycles views for other
	 * off-screen items and thus those cannot be referenced directly.
	 * 
	 * To complete this abstraction, you must also be careful to bind your
	 * stateful data in your ListAdapter so that scrolling will not ignore
	 * your UI changes.
	 * 
	 * @param id
	 *   Item id as would be returned by {@link ListAdapter#getItemId(int)}.
	 * @return
	 *   The child view if found in the list; null otherwise.
	 */
	public View getChildFromId(long id)
	{
		int row = getRowFromId(id);
		if (row < 0)
			return null;

		return getChildFromPos(row);
	}
	
	public View getChildFromPos(int pos)
	{
		int first = getFirstVisiblePosition();

		/* View is no longer on screen... */
		if (pos < first || pos > (first + getChildCount()))
			return null;

		return getChildAt(pos - first);
	}
}
