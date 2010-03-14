package org.devtcg.five.provider.util;

import org.devtcg.five.provider.Five;

import android.content.ContentUris;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.Uri;

public class AlbumItem extends AbstractDAOItem
{
	private final int mColumnId;
	private final int mColumnMbid;
	private final int mColumnName;
	private final int mColumnFullName;
	private final int mColumnNamePrefix;
	private final int mColumnArtwork;
	private final int mColumnArtworkBig;
	private final int mColumnDiscoveryDate;
	private final int mColumnReleaseDate;
	private final int mColumnNumSongs;
	private final int mColumnSet;
	private final int mColumnArtist;
	private final int mColumnArtistId;

	public static AlbumItem getInstance(Context context, Uri uri)
	{
		return CREATOR.newInstance(context, uri);
	}

	public static AlbumItem getInstance(Cursor cursor)
	{
		return CREATOR.newInstance(cursor);
	}

	public AlbumItem(Cursor cursor)
	{
		super(cursor);

		mColumnId = cursor.getColumnIndex(Five.Music.Albums._ID);
		mColumnMbid = cursor.getColumnIndex(Five.Music.Albums.MBID);
		mColumnName = cursor.getColumnIndex(Five.Music.Albums.NAME);
		mColumnFullName = cursor.getColumnIndex(Five.Music.Albums.FULL_NAME);
		mColumnNamePrefix = cursor.getColumnIndex(Five.Music.Albums.NAME_PREFIX);
		mColumnArtwork = cursor.getColumnIndex(Five.Music.Albums.ARTWORK);
		mColumnArtworkBig = cursor.getColumnIndex(Five.Music.Albums.ARTWORK_BIG);
		mColumnDiscoveryDate = cursor.getColumnIndex(Five.Music.Albums.DISCOVERY_DATE);
		mColumnReleaseDate = cursor.getColumnIndex(Five.Music.Albums.RELEASE_DATE);
		mColumnNumSongs = cursor.getColumnIndex(Five.Music.Albums.NUM_SONGS);
		mColumnSet = cursor.getColumnIndex(Five.Music.Albums.SET);
		mColumnArtist = cursor.getColumnIndex(Five.Music.Albums.ARTIST);
		mColumnArtistId = cursor.getColumnIndex(Five.Music.Albums.ARTIST_ID);
	}

	public Uri getUri()
	{
		return ContentUris.withAppendedId(Five.Music.Albums.CONTENT_URI, getId());
	}

	public long getId()
	{
		return mCursor.getLong(mColumnId);
	}

	public String getMbid()
	{
		return mCursor.getString(mColumnMbid);
	}

	public String getName()
	{
		return mCursor.getString(mColumnName);
	}

	public String getFullName()
	{
		return mCursor.getString(mColumnFullName);
	}

	public void getFullName(CharArrayBuffer buffer)
	{
		mCursor.copyStringToBuffer(mColumnFullName, buffer);
	}

	public String getNamePrefix()
	{
		return mCursor.getString(mColumnNamePrefix);
	}

	public Uri getArtworkThumbUri()
	{
		return parseUriNullSafe(mCursor.getString(mColumnArtwork));
	}

	public Uri getArtworkFullUri()
	{
		return parseUriNullSafe(mCursor.getString(mColumnArtworkBig));
	}

	public String getDiscoveryDate()
	{
		return mCursor.getString(mColumnDiscoveryDate);
	}

	public String getReleaseDate()
	{
		return mCursor.getString(mColumnReleaseDate);
	}

	public int getNumSongs()
	{
		return mCursor.getInt(mColumnNumSongs);
	}

	public long getArtistId()
	{
		return mCursor.getLong(mColumnArtistId);
	}

	public String getArtist()
	{
		return mCursor.getString(mColumnArtist);
	}

	public void getArtist(CharArrayBuffer buffer)
	{
		mCursor.copyStringToBuffer(mColumnArtist, buffer);
	}

	private static final AbstractDAOItem.Creator<AlbumItem> CREATOR =
		new AbstractDAOItem.Creator<AlbumItem>()
	{
		@Override
		public AlbumItem init(Cursor cursor)
		{
			return new AlbumItem(cursor);
		}
	};
}
