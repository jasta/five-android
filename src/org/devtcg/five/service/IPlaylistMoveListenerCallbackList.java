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

package org.devtcg.five.service;

import android.os.RemoteCallbackList;
import android.os.RemoteException;

public class IPlaylistMoveListenerCallbackList extends
  RemoteCallbackList<IPlaylistMoveListener>
{
	public IPlaylistMoveListenerCallbackList()
	{
		super();
	}

	public void broadcastOnJump(int pos)
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onJump(pos);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnAdvance()
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onAdvance();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnSeek(long pos)
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onSeek(pos);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnPlay()
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onPlay();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnPause()
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onPause();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnUnpause()
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onUnpause();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnStop()
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onStop();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}
}
