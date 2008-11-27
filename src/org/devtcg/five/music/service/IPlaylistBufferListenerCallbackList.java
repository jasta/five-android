package org.devtcg.five.music.service;

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
