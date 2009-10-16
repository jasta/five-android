package org.devtcg.five.provider.util;

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
	private int mColumnRevision;
	private int mColumnLastError;

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
		mColumnRevision = cursor.getColumnIndex(Five.Sources.REVISION);
		mColumnLastError = cursor.getColumnIndex(Five.Sources.LAST_ERROR);
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

	public long getRevision()
	{
		return mCursor.getLong(mColumnRevision);
	}

	public String getLastError()
	{
		return mCursor.getString(mColumnLastError);
	}

	public String getFeedUrl(String feedType)
	{
		return "http://" + getHost() + ":" + getPort() + "/feeds/" + feedType;
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
