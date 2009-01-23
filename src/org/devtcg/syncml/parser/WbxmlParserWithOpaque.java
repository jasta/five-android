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

import org.xmlpull.v1.*;
import org.kxml2.wap.*;
import org.kxml2.wap.syncml.SyncML;
import java.io.*;

/**
 * Intermediate class to get at OPAQUE data encoded in the document.  This
 * class is made unnecessary by kxml2 2.3.0, but earlier versions are provided
 * by Android which otherwise work as we need.
 */
public class WbxmlParserWithOpaque extends WbxmlParser
{
	protected InputStream mIn;
	protected int mWapCode;
	protected byte[] mOpaqueData;

	public static WbxmlParser createSyncmlParser()
	{
		WbxmlParser p = new WbxmlParserWithOpaque();
		p.setTagTable(0, SyncML.TAG_TABLE_0);
		p.setTagTable(1, SyncML.TAG_TABLE_1);
		return p;
	}

	public void setInput(InputStream in, String enc)
	  throws XmlPullParserException
	{
		super.setInput(in, enc);
		mIn = in;
	}

	public void parseWapExtension(int id)
	  throws IOException, XmlPullParserException
	{
		switch (id)
		{
			case Wbxml.OPAQUE:
				int len = myReadInt();
				byte[] buf = new byte[len];

				for (int i = 0; i < len; i++)
					buf[i] = (byte)myReadByte();

				mWapCode = id;
				mOpaqueData = buf;
				break;

			default:
				super.parseWapExtension(id);
		}
	}

	/* Copied from kxml2 2.2.2. */
	protected int myReadByte()
	  throws IOException
	{
		int i = mIn.read();
		if (i == -1)
			throw new IOException("Unexpected EOF");

		return i;
	}

	/* Copied from kxml2 2.2.2. */
	protected int myReadInt()
	  throws IOException
	{
		int result = 0;
		int i;

		do {
			i = myReadByte();
			result = (result << 7) | (i & 0x7f);
		}
		while ((i & 0x80) != 0);

		return result;
	}

	public int myGetWapCode()
	{
		return mWapCode;
	}

	public Object myGetWapExtensionData()
	{
		return (Object)mOpaqueData;
	}
}
