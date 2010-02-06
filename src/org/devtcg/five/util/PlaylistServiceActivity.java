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

package org.devtcg.five.util;

import org.devtcg.five.service.IPlaylistMoveListener;
import org.devtcg.five.service.IPlaylistService;
import org.devtcg.five.service.PlaylistService;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;

public abstract class PlaylistServiceActivity
  extends ServiceActivity<IPlaylistService>
{
	private TrackChangeListener mTrackChangeListener;
	private Handler mHandler = new Handler();

	@Override
	protected Intent getServiceIntent()
	{
		return new Intent(this, PlaylistService.class);
	}

	protected IPlaylistService getServiceInterface(IBinder service)
	{
		return IPlaylistService.Stub.asInterface(service);
	}

	public void setTrackChangeListener(TrackChangeListener l)
	{
		mTrackChangeListener = l;

		if (mService != null)
		{
			try {
				sendInitialTrackChange();

				if (l == null)
					mService.unregisterOnMoveListener(mPlaylistMoveListener);
				else
					mService.registerOnMoveListener(mPlaylistMoveListener);
			} catch (RemoteException e) {}
		}
	}

	protected void onAttached()
	{
		try {
			if (mTrackChangeListener != null)
			{
				sendInitialTrackChange();
				mService.registerOnMoveListener(mPlaylistMoveListener);
			}
		} catch (RemoteException e) {}
	}

	protected void onDetached()
	{
		try {
			if (mTrackChangeListener != null)
				mService.unregisterOnMoveListener(mPlaylistMoveListener);
		} catch (RemoteException e) {}
	}

	private void sendInitialTrackChange()
	  throws RemoteException
	{
		if (mTrackChangeListener == null)
			return;

		int pos;
		long songId;

		if ((pos = mService.getPosition()) >= 0)
			songId = mService.getSongAt(pos);
		else
			songId = -1;

		mTrackChangeListener.onTrackChange(songId);
	}

	private final IPlaylistMoveListener.Stub mPlaylistMoveListener =
	  new IPlaylistMoveListener.Stub()
	{
		public void onAdvance() throws RemoteException
		{
			throw new RuntimeException("BUG");
		}

		public void onJump(int pos) throws RemoteException
		{
			final long songId = mService.getSongAt(pos);
			assert songId >= 0;

			mHandler.post(new Runnable() {
				public void run() {
					if (mTrackChangeListener != null)
						mTrackChangeListener.onTrackChange(songId);
				}
			});
		}

		public void onPause() throws RemoteException
		{
		}

		public void onPlay() throws RemoteException
		{
		}

		public void onSeek(long pos) throws RemoteException
		{
		}

		public void onStop() throws RemoteException
		{
			mHandler.post(new Runnable() {
				public void run() {
					if (mTrackChangeListener != null)
						mTrackChangeListener.onTrackChange(-1);
				}
			});
		}

		public void onUnpause() throws RemoteException
		{
		}
	};

	public interface TrackChangeListener
	{
		public void onTrackChange(long songId);
	}
}
