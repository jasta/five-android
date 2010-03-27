package org.devtcg.five.provider.util;

import org.devtcg.five.provider.Five;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class PlaylistItem extends AbstractDAOItem
{
	private final int mColumnName;
	private final int mColumnNumSongs;

	public static PlaylistItem getInstance(Context context, Uri uri)
	{
		return CREATOR.newInstance(context, uri);
	}

	public static PlaylistItem getInstance(Cursor cursor)
	{
		return CREATOR.newInstance(cursor);
	}

	public PlaylistItem(Cursor cursor)
	{
		super(cursor);

		mColumnName = cursor.getColumnIndex(Five.Music.Playlists.NAME);
		mColumnNumSongs = cursor.getColumnIndex(Five.Music.Playlists.NUM_SONGS);
	}

	public Uri getUri()
	{
		return ContentUris.withAppendedId(Five.Music.Playlists.CONTENT_URI, getId());
	}

	public String getName()
	{
		return mCursor.getString(mColumnName);
	}

	public int getNumSongs()
	{
		return mCursor.getInt(mColumnNumSongs);
	}

	private static final AbstractDAOItem.Creator<PlaylistItem> CREATOR =
		new AbstractDAOItem.Creator<PlaylistItem>()
	{
		@Override
		public PlaylistItem init(Cursor cursor)
		{
			return new PlaylistItem(cursor);
		}
	};
}
