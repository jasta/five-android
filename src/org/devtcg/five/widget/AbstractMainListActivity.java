package org.devtcg.five.widget;

import java.lang.ref.WeakReference;

import org.devtcg.five.R;
import org.devtcg.five.activity.Main;
import org.devtcg.five.activity.Player;

import android.app.Activity;
import android.app.ListActivity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.AbsListView.OnScrollListener;

/**
 * Abstract base class for ArtistList and AlbumList.
 */
public abstract class AbstractMainListActivity extends ListActivity implements OnScrollListener
{
	protected AbstractMainItemAdapter<?, ?> mAdapter;

	private final OptionsMenuHelper mMenuHelper = new OptionsMenuHelper(this);

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		mAdapter = createListAdapter();
		setListAdapter(mAdapter);

		ListView listView = getListView();
		listView.setFastScrollEnabled(true);
		listView.setTextFilterEnabled(true);
		listView.setOnScrollListener(this);
	}

	@Override
	protected void onDestroy()
	{
		mAdapter.changeCursor(null);
		super.onDestroy();
	}

	protected abstract AbstractMainItemAdapter<?, ?> createListAdapter();
	protected abstract AbstractMainItemAdapter<?, ?> getAdapter();

	@Override
	protected void onPause()
	{
		super.onPause();
		mAdapter.dispatchOnPause();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		mAdapter.dispatchOnResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		return mMenuHelper.dispatchOnCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		return mMenuHelper.dispatchOnOptionsItemSelected(item);
	}

	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		mAdapter.dispatchScrollStateChanged(view, scrollState);
	}

	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
			int totalItemCount)
	{
		/* Do nothing... */
	}

	protected class QueryProvider extends SimpleQueryProvider
	{
		private final Uri mBaseUri;

		public QueryProvider(String columnName, Uri baseUri)
		{
			super(columnName);
			mBaseUri = baseUri;
		}

		@Override
		public Cursor getFilterCursor(String selection, String[] args)
		{
			return getContentResolver().query(mBaseUri, null,
					selection, args, getColumnName() + " COLLATE UNICODE ASC");
		}
	};

	/**
	 * Helper to display and respond to common screen menu controls.
	 */
	public static class OptionsMenuHelper
	{
		private WeakReference<Activity> mContext;

		public OptionsMenuHelper(Activity context)
		{
			mContext = new WeakReference<Activity>(context);
		}

		private Activity getContext()
		{
			return mContext.get();
		}

		public boolean dispatchOnCreateOptionsMenu(Menu menu)
		{
			getContext().getMenuInflater().inflate(R.menu.browse_controls, menu);
			return true;
		}

		public boolean dispatchOnOptionsItemSelected(MenuItem item)
		{
			switch (item.getItemId())
			{
				case R.id.return_library:
					Main.show(getContext());
					return true;

				case R.id.goto_player:
					Player.show(getContext());
					return true;
			}

			return false;
		}
	}
}
