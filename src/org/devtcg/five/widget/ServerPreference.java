package org.devtcg.five.widget;

import org.devtcg.five.R;
import org.devtcg.five.activity.SourceAdd;
import org.devtcg.five.provider.util.SourceItem;
import org.devtcg.five.provider.util.Sources;
import org.devtcg.five.util.DateUtils;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.preference.Preference;
import android.util.AttributeSet;

public class ServerPreference extends Preference
{
	private SourceItem mServer;

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
		this(context, attrs, android.R.attr.dialogPreferenceStyle);
	}

	public ServerPreference(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
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

	private void refresh()
	{
		if (mServer.isEmpty())
		{
			setTitle(R.string.no_server);
			setSummary(R.string.click_to_add);
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
				int rev = (int)(server.getRevision() / 1000);

				if (rev == 0)
					setSummary(R.string.never_synchronized);
				else
				{
					setSummary(getContext().getString(R.string.last_synchronized,
							DateUtils.formatTimeAgo(rev)));
				}
			}
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
