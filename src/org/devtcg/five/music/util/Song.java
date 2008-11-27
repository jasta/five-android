package org.devtcg.five.music.util;

import org.devtcg.five.provider.Five;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class Song
{
	public long id;
	public long artistId = -1;
	public long albumId = -1;
	
	public String artist;
	public String album;
	public Uri albumCover;
	public Uri albumCoverBig;
	public String title;
	public long length;

	public Song() {}

	public Song(Context ctx, long songId)
	{
		this(ctx.getContentResolver(), songId);
	}

	public Song(ContentResolver r, long songId)
	{
		id = songId;

		Uri uri = Five.Music.Songs.CONTENT_URI.buildUpon()
		  .appendEncodedPath(String.valueOf(songId))
		  .build();

		Cursor c = r.query(uri,
		  new String[] { Five.Music.Songs.ARTIST_ID,
		    Five.Music.Songs.ALBUM_ID, Five.Music.Songs.TITLE,
		    Five.Music.Songs.LENGTH },
		  null, null, null);

		try {
			if (c.moveToFirst() == false)
				throw new RuntimeException();

			artistId = c.getLong(0);
			albumId = c.getLong(1);
			title = c.getString(2);
			length = c.getLong(3);
		} finally {
			c.close();
		}

		uri = Five.Music.Artists.CONTENT_URI.buildUpon()
		  .appendEncodedPath(String.valueOf(artistId))
		  .build();

		c = r.query(uri,
		  new String[] { Five.Music.Artists.NAME },
		  null, null, null);
		
		try {
			if (c.moveToFirst() == false)
				throw new RuntimeException();
			
			artist = c.getString(0);
		} finally {
			c.close();
		}
		
		uri = Five.Music.Albums.CONTENT_URI.buildUpon()
		  .appendEncodedPath(String.valueOf(albumId))
		  .build();

		c = r.query(uri,
		  new String[] { Five.Music.Albums.NAME, Five.Music.Albums.ARTWORK,
		    Five.Music.Albums.ARTWORK_BIG },
		  null, null, null);

		try {
			if (c.moveToFirst() == false)
				throw new RuntimeException();

			album = c.getString(0);
			
			String cover = c.getString(1);
			if (cover != null)
				albumCover = Uri.parse(cover);

			String coverBig = c.getString(2);
			if (coverBig != null)
				albumCoverBig = Uri.parse(coverBig);
		} finally {
			c.close();
		}
	}
}
