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

package org.devtcg.syncml.protocol;

import java.util.UUID;
import java.util.HashMap;
import org.devtcg.syncml.transport.SyncConnection;
import org.devtcg.syncml.model.DatabaseMapping;
import java.io.ByteArrayOutputStream;

/**
 * SyncML session, containing a single message-passing instance with the
 * server.
 */
public class SyncSession
{
	protected SyncConnection mConn;
	protected String mId;
	protected long mNextMsgId = 1;

	protected HashMap<Long, BaseCommand> mPending =
	  new HashMap<Long, BaseCommand>();

	public static final int MAX_MSG_SIZE = 4096;

	public static final int ALERT_CODE_TWO_WAY_SYNC = 200;
	public static final int ALERT_CODE_ONE_WAY_FROM_SERVER = 204;
	public static final int ALERT_CODE_REFRESH_FROM_SERVER = 210;

	public SyncSession(SyncConnection conn)
	{
		this(conn, UUID.randomUUID().toString());
	}

	protected SyncSession(SyncConnection conn, String id)
	{
		mConn = conn;
		mId = id;
	}

	public String getId()
	{
		return mId;
	}

	public String getTargetURI()
	{
		return mConn.getTargetURI();
	}

	public String getSourceURI()
	{
		return mConn.getSourceURI();
	}

	public void open()
	{
		mConn.open();
	}
	
	public void close()
	{
		mConn.close();
	}

	public void sync(DatabaseMapping db)
	  throws Exception
	{
		sync(db, ALERT_CODE_TWO_WAY_SYNC);
	}

	/* This is absolute rubbish.  Should be redesigned using java.nio.*, and
	 * far less sucky. */
	public void sync(DatabaseMapping db, int code)
	  throws Exception
	{
		if (mConn.isOpened() == false)
			throw new IllegalStateException("Must call open() first.");

		syncInit(db, code);	

		SyncPackage in = new SyncPackage(this, mConn.recvPackage());
		mConn.releaseConnection();

		SyncPackage out = obtainSyncPackage();

		int n = in.getCommandLength();

		AlertCommand alert = null;
		StatusCommand alertStatus = null;

		for (int i = 0; i < n; i++)
		{
			BaseCommand cmd = in.getCommand(i);

			if (cmd instanceof StatusCommand)
			{
				StatusCommand statusCmd = (StatusCommand)cmd;

				BaseCommand pending = mPending.remove(statusCmd.getCmdId());

				if (pending == null)
				{
					System.out.println("Received shit we didn't expect");
					continue;
				}

				if (statusCmd.getCmd().equals("Alert") == true)
					alertStatus = statusCmd;
			}
			else if (cmd instanceof AlertCommand)
			{
				if (alert != null)
					System.out.println("Multiple alert commands?  Answering only one...");

				alert = (AlertCommand)cmd;
			}
			else
			{
				System.out.println("Hmm, didn't expect this?\n");
			}
		}

		if (alertStatus == null)
			throw new IllegalStateException("No <Status> command sent for our <Alert");

		/* Refresh required, try to restart the session. */
		if (alertStatus.getStatus() == 508)
		{
			if (code == 210)
				throw new IllegalStateException("Inconsistent protocol state: refresh requested during refresh operation");

			mId = UUID.randomUUID().toString();
			sync(db, 210);
			return;
		}
		else if (alertStatus.getStatus() != 200)
		{
			throw new IllegalStateException("Our <Alert> received unexpected status " + alertStatus.getStatus());
		}
	
		if (alert == null)
			throw new IllegalStateException("No <Alert> command sent from server");

		/* TODO: We should get code and anchors from the alert commands, but
		 * our crappy parser doesn't support it yet. */
		db.beginSyncLocal(code, db.getLastAnchor(), db.getNextAnchor());

		StatusCommand reply =
		  new StatusCommand(in.getId(), alert.getId(), alert.getType());

		reply.setAnchorHack(db.getLastAnchor(), db.getNextAnchor());
		reply.setSourceRef(db.getName());
		reply.setTargetRef(db.getName());
		reply.setStatus(200);

		out.addCommand(reply);

		SyncCommand dummySync = new SyncCommand();
		dummySync.setTargetId(db.getName());
		dummySync.setSourceId(db.getName());
		dummySync.setNumChanges(0);

		out.addCommand(dummySync);

		mConn.sendPackage(out);

		/* To implement <MoreData /> */
		ByteArrayOutputStream mLastItemData = null;

		boolean firstMessage = true;

		/* Changes to false on errors. */
		boolean updateAnchors = true;

		while (true)
		{
			in = new SyncPackage(this, mConn.recvPackage());
			mConn.releaseConnection();
			out = obtainSyncPackage();

			n = in.getCommandLength();

			MapCommand map = null;

			for (int i = 0; i < n; i++)
			{
				BaseCommand cmd = in.getCommand(i);
				String type = cmd.getType();

				if (type.equals("Sync") == true)
				{
					StatusCommand status =
					  new StatusCommand(in.getId(), cmd.getId(), cmd.getType());

					status.setSourceRef(db.getName());
					status.setTargetRef(db.getName());
					status.setStatus(200);

					out.addCommand(status);

					SyncCommand syncCmd = (SyncCommand)cmd;

					if (firstMessage == true)
					{
						db.beginSyncRemote(syncCmd.getNumChanges());
						firstMessage = false;
					}

					for (int j = 0; j < syncCmd.getCommandLength(); j++)
					{
						BaseCommand subCmd = syncCmd.getCommand(j);

						if (subCmd.getType().equals("Add") == true)
						{
							StatusCommand subStatus =
							  new StatusCommand(in.getId(), subCmd.getId(), subCmd.getType());

							subStatus.setSourceRef(db.getName());
							subStatus.setTargetRef(db.getName());

							AddCommand addCmd = (AddCommand)subCmd;

							assert addCmd.getItemLength() == 1;
							SyncItem item = addCmd.getItem(0);

							int ret = 0;

							if (item.hasMoreData() == true)
							{
								/* 213 - Chunk accepted. */
								ret = 213;

								if (mLastItemData == null)
									mLastItemData = new ByteArrayOutputStream();

								mLastItemData.write(item.getData());
							}
							else
							{
								if (mLastItemData != null)
								{
									item.prependData(mLastItemData.toByteArray());
									mLastItemData = null;
								}

								ret = db.insert(item);
							}

							subStatus.setStatus(ret);

							if (ret < 200 || ret >= 300)
								updateAnchors = false;
							else
							{
								if (map == null)
								{
									map = new MapCommand();
									map.setTargetId(db.getName());
									map.setSourceId(db.getName());
								}

								if (item.getTargetId() == null)
									throw new Exception("db.insert did not setTargetId, but returned status 201");

								MapItem mitem = new MapItem();
								mitem.setTargetId(item.getSourceId());
								mitem.setSourceId(item.getTargetId());
								
								map.addItem(mitem);
							}

							out.addCommand(subStatus);
						}
						else if (subCmd.getType().equals("Delete") == true ||
						  subCmd.getType().equals("Replace") == true)
						{
							StatusCommand subStatus =
							  new StatusCommand(in.getId(), subCmd.getId(), subCmd.getType());

							subStatus.setSourceRef(db.getName());
							subStatus.setTargetRef(db.getName());

							ItemActionCommand actCmd =
							  (ItemActionCommand)subCmd;
							assert actCmd.getItemLength() == 1;
							SyncItem item = actCmd.getItem(0);

							int ret;

							if (actCmd.getType().equals("Delete") == true)
								ret = db.delete(item);
							else /* if (actCmd.getType().equals("Replace") == true) */
								ret = db.update(item);

							if (ret < 200 || ret >= 300)
								updateAnchors = false;

							subStatus.setStatus(ret);

							out.addCommand(subStatus);
						}
						else
						{
							throw new IllegalStateException("Encountered unexpected command: " + subCmd.getType());
						}
					}
				}
				else
				{
//					throw new IllegalStateException("Expected <Sync> command only, got: " + type);
				}
			}

			if (map != null)
			{
				out.addCommand(map);
				mPending.put(map.getId(), map);
			}

			out.setFinal(in.isFinal());

			/* Weird, nothing to send? */
			if (in.isFinal() == false && out.getCommandLength() == 0)
			{
				if (mLastItemData == null)
					throw new IllegalStateException("Live lock detected: nothing left to say to server");

				AlertCommand alertNext = new AlertCommand(222, db);
				out.addCommand(alertNext);
				mPending.put(alertNext.getId(), alertNext);
			}

			mConn.sendPackage(out);

			if (in.isFinal() == true)
				break;
		}

		in = new SyncPackage(this, mConn.recvPackage());
		mConn.releaseConnection();

		n = in.getCommandLength();

		for (int i = 0; i < n; i++)
		{
			BaseCommand cmd = in.getCommand(i);

			if (cmd.getType().equals("Status") == true)
			{
				StatusCommand statusCmd = (StatusCommand)cmd;

				BaseCommand pending = mPending.remove(statusCmd.getCmdId());

				if (pending == null)
				{
					System.out.println("Received shit we didn't expect");
					continue;
				}

				int status = statusCmd.getStatus();

				if (status != 200)
					throw new Exception("Shit, didn't accept our changes!");
			}
		}

		System.out.println("Phew, all done!");

		mPending.clear();

		db.endSync(updateAnchors);
	}

	private void syncInit(DatabaseMapping db, int code)
	  throws Exception
	{
		SyncPackage msg = obtainSyncPackage();

		AlertCommand cmd =
		  new AlertCommand(code, db);

		msg.setMaxMsgSize(MAX_MSG_SIZE);
		msg.addCommand(cmd);
		mPending.put(cmd.getId(), cmd);

		mConn.sendPackage(msg);
	}

	protected SyncPackage obtainSyncPackage()
	{
		return new SyncPackage(this, mNextMsgId++);
	}

	@Override
	protected void finalize() throws Throwable
	{
		try
		{
			if (mConn.isOpened() == true)
				mConn.close();
		}
		finally
		{
			super.finalize();
		}
	}
}
