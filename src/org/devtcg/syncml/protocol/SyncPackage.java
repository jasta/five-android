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

import java.util.ArrayList;

import org.devtcg.syncml.parser.*;

import java.net.*;
import java.io.*;
import org.kxml2.wap.syncml.SyncML;
import org.kxml2.wap.WbxmlParser;
import org.xmlpull.v1.*;

public class SyncPackage
{
	protected ArrayList<BaseCommand> mCommands =
	  new ArrayList<BaseCommand>(3);

	protected long mID;
	protected SyncSession mSession;
	protected long mNextCmdId = 1;

	protected int mMaxSize = 0;
	protected boolean mFinal = false;

	public SyncPackage(SyncSession session, InputStream msg)
	  throws Exception
	{
		mSession = session;

//		WbxmlParser p = SyncML.createParser();
		WbxmlParser p = WbxmlParserWithOpaque.createSyncmlParser();
		p.setInput(msg, null);

		processDocument(p);
	}
	
	public void processDocument(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		xpp.nextTag();
		xpp.require(XmlPullParser.START_TAG, null, "SyncML");

		xpp.nextTag();
		xpp.require(XmlPullParser.START_TAG, null, "SyncHdr");

		processSyncHdr(xpp);

		xpp.nextTag();
		xpp.require(XmlPullParser.START_TAG, null, "SyncBody");

		processSyncBody(xpp);

		xpp.nextTag();
		xpp.require(XmlPullParser.END_TAG, null, "SyncML");
	}

	private void processSyncHdr(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		long id = -1;

		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			if (xpp.getName().equals("MsgID") == true)
				id = Long.valueOf(xpp.nextText());
			else
				XmlPullParserUtil.skipChildren(xpp);
		}

		xpp.require(XmlPullParser.END_TAG, null, "SyncHdr");

		if (id < 0)
			throw new XmlPullParserException("Illegal or missing MsgID", xpp, null);

		mID = id;
	}

	private void processSyncBody(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			String name = xpp.getName();

			if (name.equals("Final") == true)
			{
				mFinal = true;
				xpp.next();
				xpp.require(XmlPullParser.END_TAG, null, "Final");
			}
			else if (name.equals("Alert") == true)
				mCommands.add(new AlertCommand(xpp));
			else if (name.equals("Status") == true)
				mCommands.add(new StatusCommand(xpp));
			else if (name.equals("Sync") == true)
				mCommands.add(new SyncCommand(xpp));
			else
				XmlPullParserUtil.skipChildren(xpp);
		}

		xpp.require(XmlPullParser.END_TAG, null, "SyncBody");
	}

	public SyncPackage(SyncSession session, long id)
	{
		mSession = session;
		mID = id;
		mFinal = true;
	}

	public void setMaxMsgSize(int max)
	{
		mMaxSize = max;
	}

	public long getId()
	{
		return mID;
	}

	public boolean isFinal()
	{
		return mFinal;
	}

	public void setFinal(boolean f)
	{
		mFinal = f;
	}

	public void addCommand(BaseCommand cmd)
	{
		assert cmd.getId() == -1;
		cmd.setId(mNextCmdId++);
		mCommands.add(cmd);
	}

	public int getCommandLength()
	{
		return mCommands.size();
	}

	public BaseCommand getCommand(int n)
	{
		return mCommands.get(n);
	}

	private static void writeTagWithText(XmlSerializer xs, String tag, String text)
	  throws IOException
	{
		xs.startTag(null, tag);
		xs.text(text);
		xs.endTag(null, tag);
	}

	private static void writeTagWithText(XmlSerializer xs, String tag, long id)
	  throws IOException
	{
		writeTagWithText(xs, tag, String.valueOf(id));
	}

	private static void writeTagWithText(XmlSerializer xs, String tag, int id)
	  throws IOException
	{
		writeTagWithText(xs, tag, String.valueOf(id));
	}

	public void write(XmlSerializer xs)
	  throws IOException
	{
		xs.startDocument(null, null);

		xs.startTag(null, "SyncML");

		xs.startTag(null, "SyncHdr");
			writeTagWithText(xs, "VerDTD", "1.1");
			writeTagWithText(xs, "VerProto", "SyncML/1.1");
			writeTagWithText(xs, "SessionID", mSession.getId());
			writeTagWithText(xs, "MsgID", mID);
			xs.startTag(null, "Target");
				writeTagWithText(xs, "LocURI", mSession.getTargetURI());
			xs.endTag(null, "Target");
			xs.startTag(null, "Source");
				writeTagWithText(xs, "LocURI", mSession.getSourceURI());
			xs.endTag(null, "Source");

			if (mMaxSize > 0)
			{
				xs.startTag(null, "Meta");
					writeTagWithText(xs, "MaxMsgSize", mMaxSize);
				xs.endTag(null, "Meta");
			}

		xs.endTag(null, "SyncHdr");

		xs.startTag(null, "SyncBody");

		assert mCommands.size() > 0;

		for (BaseCommand cmd: mCommands)
			cmd.write(xs);

		if (isFinal() == true)
		{
			xs.startTag(null, "Final");
			xs.endTag(null, "Final");
		}

		xs.endTag(null, "SyncBody");

		xs.endTag(null, "SyncML");

		xs.endDocument();
	}
}
