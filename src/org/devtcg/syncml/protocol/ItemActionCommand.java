/*
 * $Id: AddCommand.java 569 2008-08-24 00:11:18Z jasta00 $
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

import org.devtcg.syncml.protocol.SyncItem;
import org.devtcg.syncml.parser.XmlPullParserUtil;

import java.util.ArrayList;
import org.xmlpull.v1.*;
import java.io.IOException;

public abstract class ItemActionCommand extends BaseCommand
{
	protected ArrayList<SyncItem> mItems =
	  new ArrayList<SyncItem>(1);

	protected String mMimeType;
	protected int mSize;

	public ItemActionCommand(String command, XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		super(command);

		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			String name = xpp.getName();

			if (name.equals("CmdID") == true)
				mID = Long.valueOf(xpp.nextText());
			else if (name.equals("Meta") == true)
				handleMeta(xpp);
			else if (name.equals("Item") == true)
			{
				SyncItem item = new SyncItem(xpp);

				if (mMimeType != null)
					item.setMimeType(mMimeType);

				mItems.add(item);
			}
			else
				XmlPullParserUtil.skipChildren(xpp);
		}

		xpp.require(XmlPullParser.END_TAG, null, command);
	}

	private void handleMeta(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			String name = xpp.getName();

			if (name.equals("Type") == true)
				mMimeType = xpp.nextText();
			else if (name.equals("Size") == true)
				mSize = Integer.valueOf(xpp.nextText());
			else /* What? */
				XmlPullParserUtil.skipChildren(xpp);
		}
	}

	public int getItemLength()
	{
		return mItems.size();
	}

	public SyncItem getItem(int n)
	{
		return mItems.get(n);
	}
}
