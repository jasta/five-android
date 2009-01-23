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

public class SyncAuthInfo
{
	public enum Auth
	{
		NONE
	};

	private static final SyncAuthInfo mNone = new SyncAuthInfo(Auth.NONE);
	private Auth mType;

	protected SyncAuthInfo(Auth type)
	{
		mType = type;
	}

	public static SyncAuthInfo getInstance(Auth type)
	{
		return mNone;
	}

	public Auth getType()
	{
		return mType;
	}

	public String getUsername()
	{
		return null;
	}

	public String getPassword()
	{
		return null;
	}
}
