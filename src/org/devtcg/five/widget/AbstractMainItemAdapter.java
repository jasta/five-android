package org.devtcg.five.widget;

import java.util.HashSet;

import org.devtcg.five.Constants;
import org.devtcg.five.R;
import org.devtcg.five.provider.Five;
import org.devtcg.five.provider.util.AbstractDAOItem;
import org.devtcg.five.util.AsyncBitmapHandler;
import org.devtcg.five.util.LogUtils;
import org.devtcg.five.util.MemCache;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AlphabetIndexer;
import android.widget.FilterQueryProvider;
import android.widget.SectionIndexer;

/**
 * Base list adapter used by the primary artist and album list screens. Provides
 * the animated emblem loading logic and an alphabet fast scroller.
 * <p>
 * This could be factored into two separate classes, one more of a controller
 * model and the other an actual list adapter but since only two screens use
 * this currently I don't think it's worth the extra effort.
 */
public abstract class AbstractMainItemAdapter
		<ItemHolder extends MainItemHolder, ItemDAO extends AbstractDAOItem>
		extends AbstractDAOItemAdapter<ItemDAO> implements SectionIndexer
{
	/**
	 * Animation duration when an image is loaded and displayed for the first
	 * time.
	 */
	private static final int ON_LOAD_FADE_IN_DURATION = 175;

	private static final boolean DEBUG_BITMAP_LOADS =
		LogUtils.isLoggable("BitmapLoads", Log.VERBOSE);

	private final Context mContext;

	private final AlphabetIndexer mIndexer;

	private static final MemCache<Long, Bitmap> sBitmapCache =
		new MemCache<Long, Bitmap>();

	private final HashSet<View> mViewsMissingImagery = new HashSet<View>();

	private final FetchMissingImageryHandler mHandler = new FetchMissingImageryHandler();

	private AsyncBadgeLoader mBitmapLoader;

	private int mScrollState;

	public AbstractMainItemAdapter(Context context, int layout, FilterQueryProvider provider)
	{
		super(context, layout, provider.runQuery(null));
		mContext = context;

		setFilterQueryProvider(provider);

		mIndexer = new AlphabetIndexer(getCursor(),
				getCursor().getColumnIndexOrThrow(Five.Music.Artists.NAME),
				context.getResources().getString(R.string.alphabet));
	}

	private void cleanupBackgroundOperations()
	{
		mHandler.cancelFetchMissingRequest();

		if (mBitmapLoader != null) {
			mBitmapLoader.cancelOperations();
		}
	}

	@Override
	public void changeCursor(Cursor cursor)
	{
		cleanupBackgroundOperations();
		super.changeCursor(cursor);
		mIndexer.setCursor(cursor);
	}

	public void dispatchScrollStateChanged(AbsListView view, int scrollState)
	{
		mScrollState = scrollState;
		if (scrollState == AbstractMainListActivity.SCROLL_STATE_FLING)
			cleanupBackgroundOperations();
		else
			mHandler.sendFetchMissingRequest();
	}

	public void dispatchOnResume()
	{
		mScrollState = AbstractMainListActivity.SCROLL_STATE_IDLE;
	}

	public void dispatchOnPause()
	{
		cleanupBackgroundOperations();
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor)
	{
		MainItemHolder holder = getHolder(view);

		Uri photoUri = getCurrentRowBadgeUri();
		holder.bindTo(mItemDAO.getId(), cursor.getPosition(), photoUri);

		if (photoUri == null)
			holder.badgeView.setImageResource(holder.defaultBadgeResource);
		else
		{
			Bitmap bmp = sBitmapCache.get(mItemDAO.getId());
			if (bmp != null)
			{
				holder.badgeView.setImageBitmap(bmp);
				mViewsMissingImagery.remove(view);
			}
			else
			{
				holder.badgeTransition.resetTransition();
				holder.badgeView.setImageDrawable(holder.badgeTransition);
				mViewsMissingImagery.add(view);

				if (mScrollState != AbstractMainListActivity.SCROLL_STATE_FLING)
					mHandler.sendFetchMissingRequest();
			}
		}

		holder.badgeNeedsRevealing = false;
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent)
	{
		View view = super.newView(context, cursor, parent);
		view.setTag(newHolder(view));
		return view;
	}

	protected abstract ItemHolder newHolder(View view);

	@SuppressWarnings("unchecked")
	protected final ItemHolder getHolder(View view)
	{
		return (ItemHolder)view.getTag();
	}

	protected abstract Uri getCurrentRowBadgeUri();

	public int getPositionForSection(int section)
	{
		return mIndexer.getPositionForSection(section);
	}

	public int getSectionForPosition(int position)
	{
		return mIndexer.getSectionForPosition(position);
	}

	public Object[] getSections()
	{
		return mIndexer.getSections();
	}

	private AsyncBadgeLoader getOrCreateBitmapLoader()
	{
		if (mBitmapLoader == null)
			mBitmapLoader = new AsyncBadgeLoader();

		return mBitmapLoader;
	}

	private class FetchMissingImageryHandler extends Handler
	{
		private static final int MSG_FETCH_MISSING = 0;

		/**
		 * Slight delay imposed to handle the first-time loading case where not
		 * flinging but where every item is going to call
		 * {@link #sendFetchMissingRequest()}. With a slight delay, we can
		 * ensure that under normal load all items fade-in at the same time.
		 */
		private static final int SHORT_FETCH_DELAY = 200;

		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case MSG_FETCH_MISSING:
					AsyncBadgeLoader bitmapLoader = getOrCreateBitmapLoader();

					for (View row: mViewsMissingImagery)
					{
						ItemHolder holder = getHolder(row);
						if (holder.badgeUri != null)
						{
							if (DEBUG_BITMAP_LOADS) {
								Log.d(Constants.TAG, "Reading badge: " + holder.badgeUri);
							}
							bitmapLoader.startDecode(holder.position,
									Pair.create(holder.id, row), holder.badgeUri);
						}
					}

					/*
					 * Send a sort of termination request that we'll use to
					 * reflect updates to the UI only when all requests are
					 * processed. Prevents kind of a weird looking tile-loading
					 * problem that exists in the contacts app (which doesn't
					 * use this trick).
					 */
					bitmapLoader.sendSentinel();

					break;
			}
		}

		public void cancelFetchMissingRequest()
		{
			removeMessages(MSG_FETCH_MISSING);
		}

		public void sendFetchMissingRequest()
		{
			removeMessages(MSG_FETCH_MISSING);

			if (!mViewsMissingImagery.isEmpty())
				sendMessageDelayed(obtainMessage(MSG_FETCH_MISSING), SHORT_FETCH_DELAY);
		}
	}

	private class AsyncBadgeLoader extends AsyncBitmapHandler
	{
		private static final int SENTINEL_TOKEN = -1;

		public AsyncBadgeLoader()
		{
			super(mContext.getContentResolver());
		}

		public void sendSentinel()
		{
			startDecode(SENTINEL_TOKEN, null, null);
		}

		@SuppressWarnings("unchecked")
		@Override
		public void onDecodeComplete(int token, Object cookie, Bitmap result)
		{
			if (token == SENTINEL_TOKEN)
			{
				/*
				 * Loop through all views that may need revealing. Some of these
				 * items may have changed or may no longer need revealing so
				 * it's important to check that condition inside the loop.
				 */
				for (View row: mViewsMissingImagery)
				{
					ItemHolder holder = getHolder(row);

					if (holder.badgeNeedsRevealing)
					{
						Bitmap bitmap = sBitmapCache.get(holder.id);
						if (bitmap != null)
						{
							BitmapDrawable drawable = new BitmapDrawable(mContext.getResources(),
									bitmap);
							drawable.setBounds(holder.badgeTransition.getBounds());
							holder.badgeTransition.setDrawableByLayerId(MainItemHolder.SECOND_LAYER_ID,
									drawable);
							holder.badgeTransition.startTransition(ON_LOAD_FADE_IN_DURATION);
						}

						holder.badgeNeedsRevealing = false;
					}
				}

				mViewsMissingImagery.clear();
			}
			else
			{
				Pair<Long, View> data = (Pair<Long, View>)cookie;
				long artistId = data.first;
				View row = data.second;
				ItemHolder holder = getHolder(row);

				sBitmapCache.put(artistId, result);

				if (holder.id == artistId)
					holder.badgeNeedsRevealing = true;
			}
		}
	}
}
