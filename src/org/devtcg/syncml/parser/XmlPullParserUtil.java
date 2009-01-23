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

package org.devtcg.syncml.parser;

import java.io.*;
import org.xmlpull.v1.*;

public class XmlPullParserUtil
{
	public static void skipChildren(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		int type;

		while (xpp.next() != XmlPullParser.END_TAG)
		{
			if (xpp.getEventType() == XmlPullParser.START_TAG)
				skipChildren(xpp);
		}
	}
}
