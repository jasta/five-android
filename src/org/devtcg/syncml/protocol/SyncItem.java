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

package org.devtcg.syncml.protocol;

import org.devtcg.syncml.parser.*;

import org.xmlpull.v1.*;
import org.kxml2.wap.*;
import java.io.IOException;

public class SyncItem
{
	protected String mSourceId;
	protected String mTargetId;
	protected byte[] mData;
	protected String mMimeType;
	protected boolean mMoreData = false;

	public SyncItem(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			String name = xpp.getName();

			if (name.equals("Source") == true)
			{
				xpp.nextTag();
				xpp.require(XmlPullParser.START_TAG, null, "LocURI");
				mSourceId = xpp.nextText();
				xpp.next();
				xpp.require(XmlPullParser.END_TAG, null, "Source");
			}
			else if (name.equals("Target") == true)
			{
				xpp.nextTag();
				xpp.require(XmlPullParser.START_TAG, null, "LocURI");
				mTargetId = xpp.nextText();
				xpp.next();
				xpp.require(XmlPullParser.END_TAG, null, "Target");
			}
			else if (name.equals("Data") == true)
			{
				int type = xpp.next();

				/* XXX: WbxmlParser.WAP_EXTENSION seems not to happen ever,
				 * though it is my suspicion that it should.  More kxml2 bugs,
				 * perhaps? */
				if (type == XmlPullParser.END_TAG || type == XmlPullParser.START_TAG)
				{
					WbxmlParser hack = (WbxmlParser)xpp;
					Object data = hack.getWapExtensionData();
					assert hack.getWapCode() == Wbxml.OPAQUE;
					mData = (byte[])data;
					xpp.next();
					xpp.require(XmlPullParser.END_TAG, null, "Data");
				}
				else
				{
					xpp.require(XmlPullParser.TEXT, null, null);
					mData = xpp.getText().getBytes();
					xpp.next();
				}
			}
			else if (name.equals("Meta") == true)
			{
				xpp.nextTag();
				xpp.require(XmlPullParser.START_TAG, null, "Type");
				mMimeType = xpp.nextText();
				xpp.next();
				xpp.require(XmlPullParser.END_TAG, null, "Meta");
			}
			else if (name.equals("MoreData") == true)
			{
				mMoreData = true;
				xpp.next();
				xpp.require(XmlPullParser.END_TAG, null, "MoreData");
			}
			else
				XmlPullParserUtil.skipChildren(xpp);
		}

		xpp.require(XmlPullParser.END_TAG, null, "Item");
	}

	public void prependData(byte[] data)
	{
		if (mData == null || mData.length == 0)
			mData = data;
		else if (data.length > 0)
		{
			int nlen = mData.length + data.length;
			byte[] n = new byte[nlen];

			System.arraycopy(data, 0, n, 0, data.length);
			System.arraycopy(mData, 0, n, data.length, mData.length);

			mData = n;
		}
	}

	public byte[] getData()
	{
		return mData;
	} 

	public boolean hasMoreData()
	{
		return mMoreData;
	}

	public String getSourceId()
	{
		return mSourceId;
	}

	public String getTargetId()
	{
		return mTargetId;
	}

	public void setTargetId(String id)
	{
		mTargetId = id;
	}

	public void setTargetId(long id)
	{
		mTargetId = String.valueOf(id);
	}

	public void setMimeType(String type)
	{
		mMimeType = type;
	}

	public String getMimeType()
	{
		return mMimeType;
	}
}
