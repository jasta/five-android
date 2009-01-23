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

import org.devtcg.syncml.protocol.SyncItem;
import org.devtcg.syncml.parser.XmlPullParserUtil;

import java.util.ArrayList;
import org.xmlpull.v1.*;
import java.io.IOException;

public class SyncCommand extends BaseCommand
{
	protected ArrayList<BaseCommand> mCommands =
	  new ArrayList<BaseCommand>(4);

	protected String mTargetId;
	protected String mSourceId;

	/* kxml2 incorrectly specifies this as NumberOfChanged in the SyncML
	 * string table. */
	private static final String NUMBER_OF_CHANGES_HACK = "NumberOfChanged";
	protected int mNumChanges = -1;

	public SyncCommand(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		super("Sync");

		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			String name = xpp.getName();

			if (name.equals("CmdID") == true)
				mID = Long.valueOf(xpp.nextText());
			else if (name.equals(NUMBER_OF_CHANGES_HACK) == true)
				mNumChanges = Integer.valueOf(xpp.nextText());
			else if (name.equals("Add") == true)
				mCommands.add(new AddCommand(xpp));
			else if (name.equals("Replace") == true)
				mCommands.add(new ReplaceCommand(xpp));
			else if (name.equals("Delete") == true)
				mCommands.add(new DeleteCommand(xpp));
			else
				XmlPullParserUtil.skipChildren(xpp);
		}

		xpp.require(XmlPullParser.END_TAG, null, "Sync");
	}

	public SyncCommand()
	{
		super("Sync");
	}

	public int getCommandLength()
	{
		return mCommands.size();
	}

	public BaseCommand getCommand(int n)
	{
		return mCommands.get(n);
	}

	public String getSourceId()
	{
		return mSourceId;
	}

	public void setSourceId(String id)
	{
		mSourceId = id;
	}

	public String getTargetId()
	{
		return mTargetId;
	}

	public void setTargetId(String id)
	{
		mTargetId = id;
	}

	public int getNumChanges()
	{
		return mNumChanges;
	}

	public void setNumChanges(int num)
	{
		mNumChanges = num;
	}

	public void write(XmlSerializer xs)
	  throws IOException
	{
		xs.startTag(null, "Sync");

		xs.startTag(null, "CmdID").text(String.valueOf(getId())).endTag(null, "CmdID");
		xs.startTag(null, "Target").startTag(null, "LocURI").text(mTargetId).endTag(null, "LocURI").endTag(null, "Target");
		xs.startTag(null, "Source").startTag(null, "LocURI").text(mSourceId).endTag(null, "LocURI").endTag(null, "Source");
		xs.startTag(null, NUMBER_OF_CHANGES_HACK).text(String.valueOf(mNumChanges)).endTag(null, NUMBER_OF_CHANGES_HACK);

		xs.endTag(null, "Sync");
	}
}
