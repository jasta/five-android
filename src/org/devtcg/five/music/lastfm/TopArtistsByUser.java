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

public class TopArtistsByUser
{
	private ContentResolver mContent;
	
	private String mUser;
	private int mMaxResults;
	private List<Artist> mArtists = new ArrayList<Artist>(50);

	public TopArtistsByUser(ContentResolver content, String user)
	{
		this(content, user, 50);
	}

	public TopArtistsByUser(ContentResolver content, String user,
	  int maxResults)
	{
		mContent = content;
		mUser = user;
		mMaxResults = maxResults;		
		mArtists = new ArrayList<Artist>(Math.min(50, maxResults));
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
			p.parse(new URL("http://ws.audioscrobbler.com/1.0/user/" + mUser + "/topartists.xml"));
		} catch (MaxResultsException e) {}
	}

	public List<Artist> getArtists()
	{
		return mArtists;
	}

	public static class Artist
	{
		public long artistId;
		
		public Artist(long artistId)
		{
			this.artistId = artistId;
		}
	}

	private class Parser extends DefaultHandler
	{
		private static final int CTX_INIT = 0;
		private static final int CTX_ARTIST = 1 << 0;
		private static final int CTX_ARTIST_NAME = 1 << 2;
		private static final int CTX_ARTIST_MBID = 1 << 3;

		private int ctx;

		private ArtistBuf mBuf = new ArtistBuf();

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
			if ((ctx & CTX_ARTIST) == 0)
			{
				if (qName.equals("artist") == true)
				{
					ctx |= CTX_ARTIST;
					mBuf.reset();
				}
			}
			else
			{
				if (qName.equals("name") == true)
					ctx |= CTX_ARTIST_NAME;
				else if (qName.equals("mbid") == true)
					ctx |= CTX_ARTIST_MBID;
			}
		}

		public void endElement(String uri, String name, String qName)
		  throws MaxResultsException
		{
			if ((ctx & CTX_ARTIST) == 0)
				return;
			
			if (qName.equals("artist") == true)
			{
				if (addArtist(mBuf) == true)
				{
					if (mArtists.size() == mMaxResults)
						throw new MaxResultsException("Max results reached");
				}
				
				ctx = CTX_INIT;
			}
			else
			{
				if ((ctx & CTX_ARTIST_NAME) !=0 && qName.equals("name") == true)
					ctx &= ~CTX_ARTIST_NAME;
				else if ((ctx & CTX_ARTIST_MBID) !=0 && qName.equals("mbid") == true)
					ctx &= ~CTX_ARTIST_MBID;
			}
		}

		public void characters(char ch[], int start, int length)
		{
			if ((ctx & CTX_ARTIST) == 0)
				return;
			
			else if ((ctx & CTX_ARTIST_NAME) != 0)
				mBuf.name = new String(ch, start, length);
			else if ((ctx & CTX_ARTIST_MBID) != 0)
				mBuf.mbid = new String(ch, start, length);
		}
		
		/*
		 * Adds the listed artist.  This function relates to our local
		 * database from Last.fm, which requires several heuristics since
		 * Last.fm is not consistent about using MusicBrainz IDs.
		 */
		public boolean addArtist(ArtistBuf buf)
		{
			Uri artists = Five.Music.Artists.CONTENT_URI;

			String artistProj[] = { "_id" };

			/* First try an mbid lookup. */
			if (buf.mbid != null)
			{
				Cursor c = mContent.query(artists, artistProj,
				  "mbid=?", new String[] { buf.mbid }, null);

				if (c.getCount() > 0)
				{
					c.moveToFirst();
					mArtists.add(new Artist(c.getLong(0)));
					c.close();
					
					return true;
				}

				c.close();
			}

			/* Try a name lookup. */
			if (buf.name == null)
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

			Cursor c = mContent.query(artists, artistProj,
			  "name=?", new String[] { buf.name }, null);

			if (c.getCount() == 0)
			{
				c.close();
				return false;
			}

			c.moveToFirst();
			long artistId = c.getLong(0);
			c.close();

			mArtists.add(new Artist(artistId));
			return true;
		}
	}

	private static class ArtistBuf
	{
		public ArtistBuf() {}
		
		public String name;
		public String mbid;
		
		public void reset()
		{
			name = null;
			mbid = null;
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
