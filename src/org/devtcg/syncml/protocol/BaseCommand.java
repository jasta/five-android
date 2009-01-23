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

public abstract class BaseCommand
{
	protected String mType;
	protected long mID = -1;

	public BaseCommand(String type)
	{
		/* XXX */
		mType = type;
	}

	public String getType()
	{
		return mType;
	}

	public long getId()
	{
		return mID;
	}

	public void setId(long id)
	{
		mID = id;
	}

	public void write(XmlSerializer xs)
	  throws IOException
	{
		throw new RuntimeException("Not implemented");
	}
}
