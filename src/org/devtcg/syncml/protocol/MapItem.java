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

import org.xmlpull.v1.*;
import java.io.IOException;

public class MapItem
{
	protected String mSourceId;
	protected String mTargetId;

	public MapItem()
	{
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

	public void write(XmlSerializer xs)
	  throws IOException
	{
		xs.startTag(null, "MapItem");

		xs.startTag(null, "Target").startTag(null, "LocURI").text(mTargetId).endTag(null, "LocURI").endTag(null, "Target");
		xs.startTag(null, "Source").startTag(null, "LocURI").text(mSourceId).endTag(null, "LocURI").endTag(null, "Source");
		
		xs.endTag(null, "MapItem");
	}
}
