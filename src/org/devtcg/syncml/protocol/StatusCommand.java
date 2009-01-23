/*
 * $Id: StatusCommand.java 9 2008-01-24 17:35:25Z jasta $
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

public class StatusCommand extends BaseCommand
{
	protected long mCmdId;
	protected long mMsgId;
	protected String mCmd;
	protected String mSourceRef;
	protected String mTargetRef;
	protected int mStatus;

	/* Hack, not sure why we use an <Item> for this data... */
	protected boolean mAnchorHack = false;
	protected long mNextAnchor;
	protected long mLastAnchor;

	public StatusCommand(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		super("Status");

		while (xpp.nextTag() == XmlPullParser.START_TAG)
		{
			String name = xpp.getName();

			if (name.equals("CmdID") == true)
				mID = Long.valueOf(xpp.nextText());
			else if (name.equals("MsgRef") == true)
				mMsgId = Long.valueOf(xpp.nextText());
			else if (name.equals("CmdRef") == true)
				mCmdId = Long.valueOf(xpp.nextText());
			else if (name.equals("Cmd") == true)
				mCmd = xpp.nextText();
			else if (name.equals("SourceRef") == true)
				mSourceRef = xpp.nextText();
			else if (name.equals("TargetRef") == true)
				mTargetRef = xpp.nextText();
			else if (name.equals("Data") == true)
				mStatus = Integer.valueOf(xpp.nextText());
			else
				XmlPullParserUtil.skipChildren(xpp);
		}

		xpp.require(XmlPullParser.END_TAG, null, "Status");
	}

	public StatusCommand(long msgId, long cmdId, String cmd)
	{
		super("Status");

		mMsgId = msgId;
		mCmdId = cmdId;
		mCmd = cmd;
	}

	public long getMsgId()
	{
		return mMsgId;
	}

	public long getCmdId()
	{
		return mCmdId;
	}

	public String getCmd()
	{
		return mCmd;
	}

	public void setSourceRef(String ref)
	{
		mSourceRef = ref;
	}

	public void setTargetRef(String ref)
	{
		mTargetRef = ref;
	}

	public int getStatus()
	{
		return mStatus;
	}

	public void setStatus(int status)
	{
		mStatus = status;
	}

	public void setAnchorHack(long last, long next)
	{
		mAnchorHack = true;

		mLastAnchor = last;
		mNextAnchor = next;
	}

	public void write(XmlSerializer xs)
	  throws IOException
	{
		xs.startTag(null, "Status");

		xs.startTag(null, "CmdID").text(String.valueOf(getId())).endTag(null, "CmdID");
		xs.startTag(null, "MsgRef").text(String.valueOf(mMsgId)).endTag(null, "MsgRef");
		xs.startTag(null, "CmdRef").text(String.valueOf(mCmdId)).endTag(null, "CmdRef");
		xs.startTag(null, "Cmd").text(mCmd).endTag(null, "Cmd");
		xs.startTag(null, "SourceRef").text(mSourceRef).endTag(null, "SourceRef");
		xs.startTag(null, "TargetRef").text(mTargetRef).endTag(null, "TargetRef");
		xs.startTag(null, "Data").text(String.valueOf(mStatus)).endTag(null, "Data");

		if (mAnchorHack == true)
		{
			xs.startTag(null, "Item");
				xs.startTag(null, "Data");
					xs.startTag(null, "Anchor");
						xs.startTag(null, "Next").text(String.valueOf(mNextAnchor)).endTag(null, "Next");
						xs.startTag(null, "Last").text(String.valueOf(mLastAnchor)).endTag(null, "Last");
					xs.endTag(null, "Anchor");
				xs.endTag(null, "Data");
			xs.endTag(null, "Item");
		}

		xs.endTag(null, "Status");
	}
}
