/*
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

package org.devtcg.five.service;

import android.content.Intent;
import android.os.IBinder;
import android.test.ServiceTestCase;

public class PlaylistServiceTest extends ServiceTestCase<PlaylistService>
{
	public PlaylistServiceTest()
	{
		super(PlaylistService.class);
	}

	@Override
	protected void setUp()
	  throws Exception
	{
		super.setUp();
	}

	public void testPreconditions() {}

	private Intent getIntent()
	{
		return new Intent(getContext(), PlaylistService.class);
	}

	private IPlaylistService getInterface()
	{
		return IPlaylistService.Stub.asInterface(bindService(getIntent()));
	}

	public void testStartable()
	{
		startService(getIntent());
	}

	public void testBindable()
	{
		IBinder service = bindService(getIntent());
	}
}
