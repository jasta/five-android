package org.devtcg.five.service;

import android.os.RemoteCallbackList;
import android.os.RemoteException;

public class IPlaylistDownloadListenerCallbackList extends
  RemoteCallbackList<IPlaylistDownloadListener>
{
	public IPlaylistDownloadListenerCallbackList()
	{
		super();
	}

	public 	void broadcastOnDownloadBegin(long songId)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).onDownloadBegin(songId);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnDownloadProgressUpdate(long songId, int percent)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).onDownloadProgressUpdate(songId, percent);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnDownloadError(long songId, String err)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).onDownloadError(songId, err);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnDownloadFinish(long songId)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).onDownloadFinish(songId);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastOnDownloadCancel(long songId)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).onDownloadCancel(songId);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}
}
