/*
 * $Id: MetaServiceTest.java 578 2008-08-24 18:06:59Z jasta00 $
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

import java.util.HashSet;
import java.util.Set;

import android.content.Intent;
import android.os.RemoteException;
import android.test.ServiceTestCase;
import android.util.Log;

public class MetaServiceTest extends ServiceTestCase<MetaService>
{
	public static final String TAG = "MetaServiceTest";

	public MetaServiceTest()
	{
		super(MetaService.class);
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
		return new Intent(getContext(), MetaService.class);
	}
	
	private IMetaService getInterface()
	{
		return IMetaService.Stub.asInterface(bindService(getIntent()));
	}
	
	public void testStartable()
	{
		startService(getIntent());
	}

	public void testBindable()
	{
		bindService(getIntent());
	}

	public void testObservedSync()
	  throws RemoteException
	{
		(new ObservedSyncHelper(getInterface())).run();
	}

	private class ObservedSyncHelper
	{
		private IMetaService mService;
		private volatile boolean mDone = false;
		
		/* We shouldn't need to synchronize this since we know we won't
		 * look at it until after the sync is over (which we guarantee to
		 * be true). */
		private Set<Long> mWhatSynced = new HashSet<Long>();

		public ObservedSyncHelper(IMetaService service)
		{
			mService = service;
		}

		public void run()
		  throws RemoteException
		{
			IMetaService service = mService;

			assertFalse("Service is currently syncing, despite the test not " +
			  "having begun yet.", service.isSyncing());

			service.registerObserver(mObserver);

			boolean result = service.startSync();
			assertTrue("Synchronization failed to start.", result);

			Log.d(TAG, "started sync...");

			synchronized(this)
			{
				while (mDone == false)
				{
					try { 
						wait(); 
					} catch (InterruptedException e) {}
				}
			}

			Log.d(TAG, "ending sync...");

			service.unregisterObserver(mObserver);

			assertTrue("Number of sources synchronized must be at least 1.",
			  mWhatSynced.size() > 0);
		}

		/* There is an assumption here that each call to our observer
		 * will be from the same binder thread.  Hopefully that's true. */
		private final IMetaObserver.Stub mObserver = new IMetaObserver.Stub()
		{
			private HashSet<Long> mCurrent = new HashSet<Long>();
			private boolean mIsSyncing = false;

			public void beginSource(long sourceId) throws RemoteException
			{
				assertFalse(mDone);
				assertTrue(mIsSyncing);

				assertFalse(mWhatSynced.contains(sourceId));
				assertFalse(mCurrent.contains(sourceId));
				mCurrent.add(sourceId);
			}

			public void beginSync() throws RemoteException
			{
				Log.d(TAG, "beginSync...");
				
				assertFalse(mDone);
				assertFalse(mIsSyncing);
				mIsSyncing = true;
			}

			public void endSource(long sourceId) throws RemoteException
			{
				assertFalse(mDone);
				assertTrue(mIsSyncing);

				assertTrue("endSource() not paired with beginSource()?",
				  mCurrent.contains(sourceId));
				mCurrent.remove(sourceId);

				assertFalse("Synchronized the same source multiple times?",
				  mWhatSynced.contains(sourceId));
				mWhatSynced.add(sourceId);
			}

			public void endSync() throws RemoteException
			{
				assertFalse(mDone);
				assertTrue(mIsSyncing);
				
				Log.d(TAG, "endSync...");

				synchronized(ObservedSyncHelper.this)
				{
					mDone = true;
					ObservedSyncHelper.this.notify();
				}
				
				Log.d(TAG, "Phew!");
			}

			public void updateProgress(long sourceId, int itemNo,
			  int itemCount) throws RemoteException
			{
				assertFalse(mDone);
				assertTrue(mIsSyncing);

				assertTrue(mCurrent.contains(sourceId));
				assertFalse(mWhatSynced.contains(sourceId));
			}
		};
	}
}
