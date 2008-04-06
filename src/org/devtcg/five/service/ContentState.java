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

package org.devtcg.five.service;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Holds intermediate state for dynamic five content objects to give the
 * content viewer a chance to provide a sensible wait schedule to the user.
 * That is, if the content is in the cache, the viewer could pause and wait
 * for the service to deliver the Uri; otherwise, an "Accessing..." message
 * could be displayed prompting the user to wait.
 * <p>
 * This object is accurate only from the time it was instantiated by the
 * server.  It should be used only as a hint, not a rule.   
 */
public class ContentState implements Parcelable
{
	public static final int IN_PROCESS = 0;
	public static final int IN_CACHE = 1;
	public static final int NOT_FOUND = 2;

	/**
	 * Current state.
	 * 
	 * {@see #IN_PROCESS}
	 * {@see #IN_CACHE}
	 * {@see #NOT_FOUND}
	 */
	public int state;

	/**
	 * Number of bytes currently available to the phone; equal to
	 * <code>total</code> if state is <code>IN_CACHE</code>.
	 */
	public long ready;

	/**
	 * Total bytes expected.  When <code>ready</code> and <code>total</code>
	 * match, the content is fully delivered.
	 */
	public long total;

	public ContentState()
	{
	}

	public ContentState(int state)
	{
		this.state = state;
	}

	public ContentState(int state, long length, long total)
	{
		this(state);
		this.ready = length;
		this.total = total;
	}

	public ContentState(Parcel in)
	{
		this();
		readFromParcel(in);
	}

	public float getPercentage()
	{
		return (float)ready / (float)total;
	}

	public void writeToParcel(Parcel out)
	{
		out.writeInt(state);
		out.writeLong(ready);
		out.writeLong(total);
	}

	public void readFromParcel(Parcel in)
	{
		state = in.readInt();
		ready = in.readLong();
		total = in.readLong();
	}

	@Override
	public String toString()
	{
		switch (state)
		{
		case IN_CACHE:
			return "cached (" + total + " bytes)";
		case NOT_FOUND:
			return "not found";
		case IN_PROCESS:
			return "downloading (" + ready + " / " + total + " bytes)";
		default:
			throw new IllegalStateException();
		}
	}

	@Override
	public boolean equals(Object o)
	{
		if (o instanceof ContentState)
		{
			ContentState oo = (ContentState)o;
			
			if (oo.state != state)
				return false;
			
			if (oo.ready != ready)
				return false;
			
			if (oo.total != total)
				return false;
			
			return true;
		}
		else
		{
			return super.equals(o);
		}
	}
	
	public static final Parcelable.Creator<ContentState> CREATOR = new Creator<ContentState>()
	{
		public ContentState createFromParcel(Parcel source)
		{
			return new ContentState(source);
		}

		public ContentState[] newArray(int size)
		{
			return new ContentState[size];
		}		
	};
}
