/*
 * $Id: ReplaceCommand.java 569 2008-08-24 00:11:18Z jasta00 $
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

import java.io.IOException;
import org.xmlpull.v1.*;

public class ReplaceCommand extends ItemActionCommand
{
	public ReplaceCommand(XmlPullParser xpp)
	  throws XmlPullParserException, IOException
	{
		super("Replace", xpp);
	}
}
