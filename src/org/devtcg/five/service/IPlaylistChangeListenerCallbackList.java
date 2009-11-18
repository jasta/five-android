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

public class IPlaylistChangeListenerCallbackList
  extends RemoteCallbackList<IPlaylistChangeListener>
{
	public IPlaylistChangeListenerCallbackList()
	{
		super();
	}

	public void broadcastOnClear()
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onClear();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnLoadPlaylistRef(long playlistId)
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onLoadPlaylistRef(playlistId);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}


	public void broadcastOnMove(long songId, int oldpos, int newpos)
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onMove(songId, oldpos, newpos);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnInsert(long songId, int pos)
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onInsert(songId, pos);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnRemove(int pos)
	{
		int n = beginBroadcast();

		for (int i = 0; i < n; i++)
		{
			try {
				getBroadcastItem(i).onRemove(pos);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}
}
