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
 * Describes the type of storage allocation policy the cache service
 * will apply.
 */
public class CachePolicy implements Parcelable
{
	/**
	 * How much space to leave free on the externally mounted storage card.
	 */
	public long leaveFree;

	public CachePolicy(long leaveFree)
	{
		this.leaveFree = leaveFree;
	}
	
	private CachePolicy(Parcel in)
	{
		leaveFree = in.readLong();
	}

	public void writeToParcel(Parcel out, int flags)
	{
		out.writeLong(leaveFree);
	}

	public int describeContents()
	{
		return 0;
	}

	public static final Parcelable.Creator<CachePolicy> CREATOR =
	  new Parcelable.Creator<CachePolicy>()
	{
		public CachePolicy createFromParcel(Parcel source)
		{
			return new CachePolicy(source);
		}

		public CachePolicy[] newArray(int size)
		{
			return new CachePolicy[size];
		}
	};

}