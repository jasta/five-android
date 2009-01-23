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

package org.devtcg.syncml.transport;

import java.io.InputStream;
import java.io.IOException;
import org.devtcg.syncml.protocol.SyncPackage;
import org.devtcg.syncml.protocol.SyncAuthInfo;

/**
 * Manages transport-specific connection details.
 */
public abstract class SyncConnection
{
	protected SyncAuthInfo mAuth;
	protected String mTarget;
	protected String mSource;
	protected boolean mOpened;

	public SyncConnection(String name)
	{
		mTarget = name;
	}

	public String getName()
	{
		return mTarget;
	}

	public void setAuthentication(SyncAuthInfo info)
	{
		mAuth = info;
	}

	public SyncAuthInfo getAuthentication()
	{
		return mAuth;
	}

	public String getTargetURI()
	{
		return mTarget;
	}

	public String getSourceURI()
	{
		return mSource;
	}

	public void setSourceURI(String ident)
	{
		mSource = ident;
	}

	public boolean isOpened()
	{
		return mOpened;
	}

	public abstract void open();
	public abstract void sendPackage(SyncPackage msg) throws Exception;
	public abstract InputStream recvPackage() throws Exception;
	public abstract void releaseConnection() throws IOException;
	public abstract void close();
}
