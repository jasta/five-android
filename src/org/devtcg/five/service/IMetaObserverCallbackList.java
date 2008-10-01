package org.devtcg.five.service;

import android.os.RemoteCallbackList;
import android.os.RemoteException;

public class IMetaObserverCallbackList extends
  RemoteCallbackList<IMetaObserver>
{
	public IMetaObserverCallbackList()
	{
		super();
	}

	public void broadcastBeginSync()
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).beginSync();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastEndSync()
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).endSync();
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastBeginSource(long sourceId)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).beginSource(sourceId);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastEndSource(long sourceId)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).endSource(sourceId);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}

	public void broadcastUpdateProgress(long sourceId, int itemNo, int itemCount)
	{
		int N = beginBroadcast();

		for (int i = 0; i < N; i++)
		{
			try {
				getBroadcastItem(i).updateProgress(sourceId, itemNo, itemCount);
			} catch (RemoteException e) {}
		}

		finishBroadcast();
	}
}
