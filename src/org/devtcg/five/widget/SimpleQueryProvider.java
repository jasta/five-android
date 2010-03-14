package org.devtcg.five.widget;

import org.devtcg.five.Constants;

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;
import android.widget.FilterQueryProvider;

/**
 * Basic query provider helper which automatically generates query selection
 * criteria based on partial string matching semantics on a single column.
 */
public abstract class SimpleQueryProvider implements FilterQueryProvider
{
	private final String mColumnWhere;
	private final String mColumnName;

	public SimpleQueryProvider(String columnName)
	{
		mColumnWhere = "UPPER(" + columnName + ") GLOB ?";
		mColumnName = columnName;
	}

	public String getColumnName()
	{
		return mColumnName;
	}

	public Cursor runQuery(CharSequence constraint)
	{
		String sel;
		String[] args;

		if (Constants.DEBUG)
			Log.d(Constants.TAG, "runQuery, " + mColumnName + ": " + constraint);

		if (TextUtils.isEmpty(constraint))
		{
			sel = null;
			args = null;
		}
		else
		{
			sel = mColumnWhere;

			String wildcard = constraint.toString().replace(' ', '*');
			args = new String[] { "*" + wildcard.toUpperCase() + "*" };
		}

		return getFilterCursor(sel, args);
	}

	protected abstract Cursor getFilterCursor(String selection, String[] args);
}
