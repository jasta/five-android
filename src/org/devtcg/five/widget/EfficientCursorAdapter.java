/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
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

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/**
 * SimpleCursorAdapter which implements the ViewBinder using the
 * ViewHolder pattern.  See ApiDemos sample List14.java ("Efficient
 * Adapter") for more information on the ViewHolder pattern.
 */
public class EfficientCursorAdapter extends SimpleCursorAdapter
{
	protected int[] mFrom;
	protected int[] mTo;
	protected ViewBinder mViewBinder;

	public EfficientCursorAdapter(Context context, int layout, Cursor c,
	  String[] from, int[] to)
	{
		super(context, layout, c, from, to);
		mFrom = getColumnIndices(c, from);
		mTo = to;
	}

	private static int[] getColumnIndices(Cursor c, String[] from)
	{
		int n = from.length;
		int[] indices = new int[n];

		for (int i = 0; i < n; i++)
			indices[i] = c.getColumnIndexOrThrow(from[i]);

		return indices;
	}

	@Override
	public void setViewBinder(ViewBinder viewBinder)
	{
		super.setViewBinder(viewBinder);
		mViewBinder = viewBinder;
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent)
	{
		View v = super.newView(context, cursor, parent);

		View[] holder = new View[mTo.length];

		for (int i = 0; i < mTo.length; i++)
		{
			View held = v.findViewById(mTo[i]);
			if (held == null)
				throw new IllegalStateException("Could not locate view for column " + mFrom[i]);

			holder[i] = held;
		}

		v.setTag(holder);

		return v;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor)
	{
		View[] holder = (View[])view.getTag();

		for (int i = 0; i < mTo.length; i++)
		{
			View v = holder[i];

			boolean bound = false;

			if (mViewBinder != null)
				bound = mViewBinder.setViewValue(v, cursor, mFrom[i]);

			if (bound == false)
			{
				String value = cursor.getString(mFrom[i]);
				if (value == null)
					value = "";

				if (v instanceof TextView)
					setViewText((TextView)v, value);
				else if (v instanceof ImageView)
					setViewImage((ImageView)v, value);
				else
					throw new IllegalStateException("ViewBinder must have handled unknown view type for column " + mFrom[i]);
			}
		}
	}
}
