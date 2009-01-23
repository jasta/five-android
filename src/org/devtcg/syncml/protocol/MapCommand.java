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

import org.devtcg.syncml.protocol.MapItem;

import java.util.ArrayList;
import org.xmlpull.v1.*;
import java.io.IOException;

public class MapCommand extends BaseCommand
{
	protected ArrayList<MapItem> mItems =
	  new ArrayList<MapItem>(16);

	protected String mTargetId;
	protected String mSourceId;

	public MapCommand()
	{
		super("Map");
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

	public int getItemLength()
	{
		return mItems.size();
	}

	public MapItem getItem(int n)
	{
		return mItems.get(n);
	}

	public void addItem(MapItem item)
	{
		mItems.add(item);
	}

	public void write(XmlSerializer xs)
	  throws IOException
	{
		assert mItems.isEmpty() == false;

		xs.startTag(null, "Map");

		xs.startTag(null, "CmdID").text(String.valueOf(getId())).endTag(null, "CmdID");
		xs.startTag(null, "Target").startTag(null, "LocURI").text(mTargetId).endTag(null, "LocURI").endTag(null, "Target");
		xs.startTag(null, "Source").startTag(null, "LocURI").text(mSourceId).endTag(null, "LocURI").endTag(null, "Source");
	
		for (MapItem item: mItems)
			item.write(xs);

		xs.endTag(null, "Map");
	}
}
