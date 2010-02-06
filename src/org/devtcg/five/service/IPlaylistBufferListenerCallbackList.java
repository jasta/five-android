/*
 * Copyright (C) 2010 Josh Guilfoyle <jasta@devtcg.org>
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

public class IPlaylistBufferListenerCallbackList extends
  RemoteCallbackList<IPlaylistBufferListener>
{
	public IPlaylistBufferListenerCallbackList()
	{
		super();
	}

	public 	void broadcastOnBufferingUpdate(long songId, int bufferPercent)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).onBufferingUpdate(songId, bufferPercent);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}
}
