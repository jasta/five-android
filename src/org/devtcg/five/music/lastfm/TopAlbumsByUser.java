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

package org.devtcg.five.music.lastfm;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.devtcg.five.provider.Five;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class TopAlbumsByUser
{
	private ContentResolver mContent;
	
	private String mUser;
	private int mMaxResults;
	private List<Album> mAlbums = new ArrayList<Album>(50);

	public TopAlbumsByUser(ContentResolver content, String user)
	{
		this(content, user, 50);
	}

	public TopAlbumsByUser(ContentResolver content, String user,
	  int maxResults)
	{
		mContent = content;
		mUser = user;
		mMaxResults = maxResults;		
		mAlbums = new ArrayList<Album>(Math.min(50, maxResults));
	}

	public String getUser()
	{
		return mUser;
	}

	public void fetch()
	  throws Exception
	{
		Parser p = new Parser();
		
		try {
			p.parse(new URL("http://ws.audioscrobbler.com/1.0/user/" + mUser + "/topalbums.xml"));
		} catch (MaxResultsException e) {}
	}

	public List<Album> getAlbums()
	{
		return mAlbums;
	}

	public static class Album
	{
		public long artistId;
		public long albumId;
		
		public Album(long artistId, long albumId)
		{
			this.artistId = artistId;
			this.albumId = albumId;
		}
	}

	private class Parser extends DefaultHandler
	{
		private static final int CTX_INIT = 0;
		private static final int CTX_ALBUM = 1 << 0;
		private static final int CTX_ALBUM_ARTIST = 1 << 1;
		private static final int CTX_ALBUM_NAME = 1 << 2;
		private static final int CTX_ALBUM_MBID = 1 << 3;

		private int ctx;

		private AlbumBuf mBuf = new AlbumBuf();

		public Parser() {}

		public void parse(URL url)
		  throws Exception
		{
			SAXParserFactory spf = SAXParserFactory.newInstance();
			SAXParser sp = spf.newSAXParser();
			XMLReader xr = sp.getXMLReader();

			xr.setContentHandler(this);
			xr.setErrorHandler(this);

			ctx = CTX_INIT;
			xr.parse(new InputSource(url.openStream()));
		}

		public void startElement(String uri, String name, String qName,
		  Attributes attrs)
		{
			if ((ctx & CTX_ALBUM) == 0)
			{
				if (qName.equals("album") == true)
				{
					ctx |= CTX_ALBUM;
					mBuf.reset();
				}
			}
			else
			{
				if (qName.equals("artist") == true)
				{
					ctx |= CTX_ALBUM_ARTIST;
					
					String mbid = attrs.getValue("mbid");
					
					if (mbid != null && mbid.length() > 0)
						mBuf.mbid = mbid;
				}
				else if (qName.equals("name") == true)
					ctx |= CTX_ALBUM_NAME;
				else if (qName.equals("mbid") == true)
					ctx |= CTX_ALBUM_MBID;
			}
		}

		public void endElement(String uri, String name, String qName)
		  throws MaxResultsException
		{
			if ((ctx & CTX_ALBUM) == 0)
				return;
			
			if (qName.equals("album") == true)
			{
				if (addAlbum(mBuf) == true)
				{
					if (mAlbums.size() == mMaxResults)
						throw new MaxResultsException("Max results reached");
				}
				
				ctx = CTX_INIT;
			}
			else
			{
				if ((ctx & CTX_ALBUM_ARTIST) !=0 && qName.equals("artist") == true)
					ctx &= ~CTX_ALBUM_ARTIST;
				else if ((ctx & CTX_ALBUM_NAME) !=0 && qName.equals("name") == true)
					ctx &= ~CTX_ALBUM_NAME;
				else if ((ctx & CTX_ALBUM_MBID) !=0 && qName.equals("mbid") == true)
					ctx &= ~CTX_ALBUM_MBID;
			}
		}

		public void characters(char ch[], int start, int length)
		{
			if ((ctx & CTX_ALBUM) == 0)
				return;
			
			if ((ctx & CTX_ALBUM_ARTIST) != 0)
				mBuf.artistName = new String(ch, start, length);
			else if ((ctx & CTX_ALBUM_NAME) != 0)
				mBuf.name = new String(ch, start, length);
			else if ((ctx & CTX_ALBUM_MBID) != 0)
				mBuf.mbid = new String(ch, start, length);
		}
		
		/*
		 * Adds the listed album.  This function relates to our local
		 * database from Last.fm, which requires several heuristics since
		 * Last.fm is not consistent about using MusicBrainz IDs.
		 */
		public boolean addAlbum(AlbumBuf buf)
		{
			Uri albums = Five.Music.Albums.CONTENT_URI;
			
			String albumProj[] = { "_id", "artist_id" };

			/* First try an mbid lookup. */
			if (buf.mbid != null)
			{
				Cursor c = mContent.query(albums, albumProj,
				  "a.mbid=?", new String[] { buf.mbid }, null);

				if (c.getCount() > 0)
				{
					c.moveToFirst();
					mAlbums.add(new Album(c.getLong(1), c.getLong(0)));
					c.close();
					
					return true;
				}

				c.close();
			}

			/* Try a name lookup. */
			if (buf.artistName == null || buf.name == null)
				return false;

//			Uri artists = Five.Music.Artists.CONTENT_URI;
//			String artistProj[] = { "_id" };
//
//			Cursor c = mContent.query(artists, artistProj,
//			  "a.name=?", new String[] { buf.artistName }, null);
//			
//			if (c.count() == 0)
//			{
//				c.close();
//				return false;
//			}
//			
//			c.first();
//			long artistId = c.getLong(0);
//			c.close();

			Cursor c = mContent.query(albums, albumProj,
			  "a.name=? AND artists.name=?",
			  new String[] { buf.name, buf.artistName }, null);

			if (c.getCount() == 0)
			{
				c.close();
				return false;
			}

			c.moveToFirst();
			long artistId = c.getLong(1);
			long albumId = c.getLong(0);
			c.close();

			mAlbums.add(new Album(artistId, albumId));
			return true;
		}
	}

	private static class AlbumBuf
	{
		public AlbumBuf() {}
		
		public String name;
		public String mbid;
		public String artistName;
		public String artistMbid;
		
		public void reset()
		{
			name = null;
			mbid = null;
			artistName = null;
			artistMbid = null;
		}
	}
	
	private static class MaxResultsException extends SAXException
	{
		public MaxResultsException(String message)
		{
			super(message);
		}
	}
}
