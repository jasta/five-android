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

package org.devtcg.five.widget;

import java.text.DateFormat;
import java.util.Date;

import org.devtcg.five.R;
import org.devtcg.five.activity.SourceAdd;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.provider.util.Sources;
import org.devtcg.five.service.MetaService;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

public class ServerPreference extends Preference
{
	private SourceItem mServer;
	private static DateFormat mFormatter =
		DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

	private boolean mIsSyncing;

	public ServerPreference(Context context)
	{
		this(context, null);
	}

	public ServerPreference(Context context, AttributeSet attrs)
	{
		/*
		 * We inherit from dialogPreferenceStyle in order to get the down arrow
		 * circle button to the right of the preference. This could be done
		 * manually, but this seems like the easiest way without added
		 * fragility.
		 */
		this(context, attrs, 0);
	}

	public ServerPreference(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		setWidgetLayoutResource(R.layout.server_preference_widget);
	}

	public void init()
	{
		/*
		 * XXX: While it seems like we support multiple sources, we actually do
		 * not. We simply assume that if there are any rows, "the" server is the
		 * first one.
		 */
		Cursor cursor = Sources.getSources(getContext());
		mServer = new SourceItem(cursor);
		mServer.moveToFirst();
		cursor.registerContentObserver(new ServerContentObserver(new Handler()));
		cursor.registerDataSetObserver(new ServerDataSetObserver());

		refresh();
	}

	public void cleanup()
	{
		if (mServer != null)
		{
			mServer.close();
			mServer = null;
		}
	}

	public boolean isEmpty()
	{
		return mServer == null || mServer.isEmpty();
	}

	public void setIsSyncing(boolean isSyncing)
	{
		if (mIsSyncing != isSyncing)
		{
			mIsSyncing = isSyncing;
			notifyChanged();
		}
	}

	private void refresh()
	{
		setIsSyncing(MetaService.isSyncing());

		if (mServer.isEmpty())
		{
			setTitle(R.string.no_server);
			setSummary(R.string.no_server_summary);
		}
		else
		{
			SourceItem server = mServer;

			setTitle(server.getHostLabel());

			String status = server.getStatus();
			if (status != null)
				setSummary(status);
			else
			{
				long rev = server.getLastSyncTime();

				if (rev == 0)
					setSummary(R.string.never_synchronized);
				else
					setSummary(mFormatter.format(new Date(rev)));
			}
		}
	}

	@Override
	protected void onBindView(View view)
	{
		super.onBindView(view);

		ImageView syncIcon = (ImageView)view.findViewById(R.id.sync_active);
		syncIcon.setVisibility(mIsSyncing ? View.VISIBLE : View.INVISIBLE);

		final AnimationDrawable anim = (AnimationDrawable)syncIcon.getDrawable();

		if (mIsSyncing)
		{
			/*
			 * Why do I need to post a runnable here? This code was taken from
			 * GoogleSubscribedFeedsProvider's SyncStateCheckBoxPreference which does it this way.
			 */
			syncIcon.post(new Runnable() {
				public void run() {
					anim.start();
				}
			});
		}
		else
		{
			anim.stop();
		}
	}

	@Override
	protected void onClick()
	{
		if (mServer.isEmpty())
			SourceAdd.actionAddSource(getContext());
		else
			SourceAdd.actionEditSource(getContext(), mServer.getUri());
	}

	private class ServerContentObserver extends ContentObserver
	{
		public ServerContentObserver(Handler handler)
		{
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange)
		{
			mServer.getCursor().requery();
		}
	}

	private class ServerDataSetObserver extends DataSetObserver
	{
		@Override
		public void onChanged()
		{
			if (mServer.getCursor().getCount() > 0)
				mServer.getCursor().moveToFirst();

			refresh();
		}

		@Override
		public void onInvalidated() {}
	}
}
