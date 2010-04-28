package org.devtcg.five.widget;

import org.devtcg.five.provider.util.AbstractDAOItem;

import android.content.Context;
import android.database.Cursor;
import android.widget.ResourceCursorAdapter;

public abstract class AbstractDAOItemAdapter<T extends AbstractDAOItem>
		extends ResourceCursorAdapter
{
	protected T mItemDAO;

	public AbstractDAOItemAdapter(Context context, int layout, Cursor c)
	{
		this(context, layout, c, true);
	}

	public AbstractDAOItemAdapter(Context context, int layout, Cursor c, boolean autoRequery)
	{
		super(context, layout, c, autoRequery);
		attachItemDAO(c);
	}

	protected T newItemDAO(Cursor cursor)
	{
		throw new UnsupportedOperationException("You must override newItemDAO to use this adapter");
	}

	/** @deprecated Extend {@link #newItemDAO(Cursor)} instead. */
	protected void onAttachItemDAO(Cursor cursor)
	{
		mItemDAO = newItemDAO(cursor);
	}

	private void attachItemDAO(Cursor cursor)
	{
		if (cursor == null)
			mItemDAO = null;
		else if (mItemDAO == null || mItemDAO.getCursor() != cursor)
			onAttachItemDAO(cursor);
	}

	@Override
	public void notifyDataSetChanged()
	{
		attachItemDAO(getCursor());
		super.notifyDataSetChanged();
	}

	@Override
	public void notifyDataSetInvalidated()
	{
		attachItemDAO(null);
		super.notifyDataSetInvalidated();
	}

	public T getItemDAO(int position)
	{
		if (getItem(position) != null)
			return mItemDAO;
		else
			return null;
	}
}
