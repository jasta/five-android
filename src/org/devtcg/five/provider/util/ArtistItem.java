package org.devtcg.five.provider.util;

import org.devtcg.five.provider.Five;

import android.content.ContentUris;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.net.Uri;

public class ArtistItem extends AbstractDAOItem
{
	private final int mColumnMbid;
	private final int mColumnName;
	private final int mColumnFullName;
	private final int mColumnNamePrefix;
	private final int mColumnPhoto;
	private final int mColumnGenre;
	private final int mColumnDiscoveryDate;
	private final int mColumnNumAlbums;
	private final int mColumnNumSongs;

	public static ArtistItem getInstance(Context context, Uri uri)
	{
		return CREATOR.newInstance(context, uri);
	}

	public static ArtistItem getInstance(Cursor cursor)
	{
		return CREATOR.newInstance(cursor);
	}

	public ArtistItem(Cursor cursor)
	{
		super(cursor);

		mColumnMbid = cursor.getColumnIndex(Five.Music.Artists.MBID);
		mColumnName = cursor.getColumnIndex(Five.Music.Artists.NAME);
		mColumnFullName = cursor.getColumnIndex(Five.Music.Artists.FULL_NAME);
		mColumnNamePrefix = cursor.getColumnIndex(Five.Music.Artists.NAME_PREFIX);
		mColumnPhoto = cursor.getColumnIndex(Five.Music.Artists.PHOTO);
		mColumnGenre = cursor.getColumnIndex(Five.Music.Artists.GENRE);
		mColumnDiscoveryDate = cursor.getColumnIndex(Five.Music.Artists.DISCOVERY_DATE);
		mColumnNumAlbums = cursor.getColumnIndex(Five.Music.Artists.NUM_ALBUMS);
		mColumnNumSongs = cursor.getColumnIndex(Five.Music.Artists.NUM_SONGS);
	}

	public Uri getUri()
	{
		return ContentUris.withAppendedId(Five.Music.Artists.CONTENT_URI, getId());
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

	public Uri getPhotoUri()
	{
		return parseUriNullSafe(mCursor.getString(mColumnPhoto));
	}

	public String getGenre()
	{
		return mCursor.getString(mColumnGenre);
	}

	public String getDiscoveryDate()
	{
		return mCursor.getString(mColumnDiscoveryDate);
	}

	public int getNumAlbums()
	{
		return mCursor.getInt(mColumnNumAlbums);
	}

	public int getNumSongs()
	{
		return mCursor.getInt(mColumnNumSongs);
	}

	private static final AbstractDAOItem.Creator<ArtistItem> CREATOR =
		new AbstractDAOItem.Creator<ArtistItem>()
	{
		@Override
		public ArtistItem init(Cursor cursor)
		{
			return new ArtistItem(cursor);
		}
	};
}
