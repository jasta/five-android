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

package org.devtcg.five.provider.util;

import org.devtcg.five.Constants;
import org.devtcg.five.provider.Five;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class SourceItem extends AbstractDAOItem
{
	private int mColumnId;
	private int mColumnHost;
	private int mColumnPort;
	private int mColumnPassword;
	private int mColumnLastSyncTime;
	private int mColumnStatus;

	public static SourceItem getInstance(Context context, Uri uri)
	{
		return CREATOR.newInstance(context, uri);
	}

	public static SourceItem getInstance(Cursor cursor)
	{
		return CREATOR.newInstance(cursor);
	}

	public SourceItem(Cursor cursor)
	{
		super(cursor);

		mColumnId = cursor.getColumnIndex(Five.Sources._ID);
		mColumnHost = cursor.getColumnIndex(Five.Sources.HOST);
		mColumnPort = cursor.getColumnIndex(Five.Sources.PORT);
		mColumnPassword = cursor.getColumnIndex(Five.Sources.PASSWORD);
		mColumnLastSyncTime = cursor.getColumnIndex(Five.Sources.LAST_SYNC_TIME);
		mColumnStatus = cursor.getColumnIndex(Five.Sources.STATUS);
	}

	public Uri getUri()
	{
		return ContentUris.withAppendedId(Five.Sources.CONTENT_URI, getId());
	}

	public long getId()
	{
		return mCursor.getLong(mColumnId);
	}

	public String getHost()
	{
		return mCursor.getString(mColumnHost);
	}

	public int getPort()
	{
		return mCursor.getInt(mColumnPort);
	}

	public String getPassword()
	{
		return mCursor.getString(mColumnPassword);
	}

	public long getLastSyncTime()
	{
		return mCursor.getLong(mColumnLastSyncTime);
	}

	public String getStatus()
	{
		return mCursor.getString(mColumnStatus);
	}

	/**
	 * Get a canonized label for the host and port of this server source.
	 */
	public String getHostLabel()
	{
		int port = getPort();
		if (port != Constants.DEFAULT_SERVER_PORT)
			return getHost() + ":" + port;
		else
			return getHost();
	}

	public String getFeedUrl(String feedType)
	{
		return "http://" + getHost() + ":" + getPort() + "/feeds/" + feedType;
	}

	public String getSongUrl(long syncId)
	{
		return "http://" + getHost() + ":" + getPort() + "/songs/" + syncId;
	}

	public String getImageUrl(String feedType, long syncId, int width, int height)
	{
		return "http://" + getHost() + ":" + getPort() + "/image/" + feedType + "/" +
				width + "x" + height + "/" + syncId;
	}

	public String getServerInfoUrl()
	{
		return "http://" + getHost() + ":" + getPort() + "/info";
	}

	private static final AbstractDAOItem.Creator<SourceItem> CREATOR =
		new AbstractDAOItem.Creator<SourceItem>()
	{
		@Override
		public SourceItem init(Cursor cursor)
		{
			return new SourceItem(cursor);
		}
	};
}
