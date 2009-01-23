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

import org.devtcg.syncml.model.DatabaseMapping;
import org.devtcg.syncml.parser.XmlPullParserUtil;

import org.xmlpull.v1.*;
import java.io.IOException;

public class AlertCommand extends BaseCommand
{
	protected int mCode;
	protected DatabaseMapping mDB;

	public AlertCommand(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		super("Alert");

		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			String name = xpp.getName();

			if (name.equals("CmdID") == true)
				mID = Long.valueOf(xpp.nextText());
			else if (name.equals("Data") == true)
				mCode = Integer.valueOf(xpp.nextText());
			else
				XmlPullParserUtil.skipChildren(xpp);
		}

		xpp.require(XmlPullParser.END_TAG, null, "Alert");
	}

	public AlertCommand(int code, DatabaseMapping db)
	{
		super("Alert");

		mCode = code;
		mDB = db;
	}

	public int getCode()
	{
		return mCode;
	}

	public DatabaseMapping getDB()
	{
		return mDB;
	}

	public void write(XmlSerializer xs)
	  throws IOException
	{
		xs.startTag(null, "Alert");

		xs.startTag(null, "CmdID").text(String.valueOf(getId())).endTag(null, "CmdID");
		xs.startTag(null, "Data").text(String.valueOf(mCode)).endTag(null, "Data");
		xs.startTag(null, "Item");
			xs.startTag(null, "Target").startTag(null, "LocURI").text(mDB.getName()).endTag(null, "LocURI").endTag(null, "Target");
			xs.startTag(null, "Source").startTag(null, "LocURI").text(mDB.getName()).endTag(null, "LocURI").endTag(null, "Source");
			xs.startTag(null, "Meta");
				xs.startTag(null, "Anchor");
					xs.startTag(null, "Last").text(String.valueOf(mDB.getLastAnchor())).endTag(null, "Last");
					xs.startTag(null, "Next").text(String.valueOf(mDB.getNextAnchor())).endTag(null, "Next");
				xs.endTag(null, "Anchor");
			xs.endTag(null, "Meta");
		xs.endTag(null, "Item");

		xs.endTag(null, "Alert");
	}
}
