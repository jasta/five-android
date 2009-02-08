package org.devtcg.five.activity;

import java.text.ParseException;
import java.util.regex.Pattern;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SourceAdd extends Activity
{
	private static final int DEFAULT_PORT = 5545;
	
	private Button mNext;
	private EditText mLabel;
	private EditText mHostname;
	private EditText mPassword;
	
	/* The source we're editing if called that way. */
	private Uri mExisting;

	public static void actionAddSource(Context context)
	{
		context.startActivity(new Intent(context, SourceAdd.class));
	}

	public static void actionEditSource(Context context, long sourceId)
	{
		Intent i = new Intent(context, SourceAdd.class);
		i.setAction(Intent.ACTION_EDIT);
		i.setData(ContentUris.withAppendedId(Five.Sources.CONTENT_URI,
		  sourceId));
		context.startActivity(i);
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		setContentView(R.layout.source_add);

		mLabel = (EditText)findViewById(R.id.source_label);
		mHostname = (EditText)findViewById(R.id.source_host);
		mPassword = (EditText)findViewById(R.id.source_password);

		mNext = (Button)findViewById(R.id.next);
		mNext.setOnClickListener(mNextClick);

		mHostname.requestFocus();

		/* Differentiates the add versus edit cases. */
		handleIntent(getIntent());
	}
	
	private void handleIntent(Intent i)
	{
		String action = i.getAction();
		Uri data = i.getData();
		
		if (action == null || data == null)
			return;
		
		if (action.equals(Intent.ACTION_EDIT) == true)
		{
			String[] fields = { Five.Sources._ID,
			  Five.Sources.NAME, Five.Sources.HOST, Five.Sources.PORT };
			Cursor c = getContentResolver().query(data, fields,
			  null, null, null);

			try {
				if (c.moveToFirst() == false)
					return;

				mExisting = data;
				mLabel.setText(c.getString(1));
				
				StringBuilder host = new StringBuilder(c.getString(2));
				if (c.isNull(3) == false)
				{
					int port = c.getInt(3);
					if (port != DEFAULT_PORT)
						host.append(':').append(port);
				}
				
				mHostname.setText(host.toString());
			} finally {
				c.close();
			}
		}
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode,
      Intent data)
    {
		if (resultCode == RESULT_OK)
			finish();
    }

	private OnClickListener mNextClick = new OnClickListener()
	{
		private void insertValues(ContentValues values, String name,
		  String host)
		  throws ParseException
		{
			values.put(Five.Sources.NAME, name);

			int colon;
			if ((colon = host.indexOf(':')) >= 0)
			{
				String port = host.substring(colon + 1);

				if (colon == 0 || Pattern.matches("^\\d+$", port) == false)
					throw new ParseException("Unable to parse hostname, expected <hostname>[:<port>]", colon);

				values.put(Five.Sources.HOST, host.substring(0, colon));
				values.put(Five.Sources.PORT, Integer.parseInt(port));
			}
			else
			{
				values.put(Five.Sources.HOST, host);
				values.put(Five.Sources.PORT, DEFAULT_PORT);
			}
		}
		
		public void onClick(View v)
		{
			ContentValues values = new ContentValues();

			String name = mLabel.getText().toString();
			String host = mHostname.getText().toString();

			if (TextUtils.isEmpty(name) == true)
				name = "<Default>";
			
			try {
				insertValues(values, name, host);
			} catch (ParseException e) {
				Toast.makeText(SourceAdd.this, e.getMessage(),
				  Toast.LENGTH_LONG).show();
				return;
			}

			Uri uri;

			if (mExisting == null)
			{
				uri = getContentResolver()
				  .insert(Five.Sources.CONTENT_URI, values);

				/* If check settings fails, we'll be returned to this
				 * activity and the user will be clicking next multiple
				 * times until they get it right.  We want to make sure
				 * we're using update() not insert() for this case. */
				mExisting = uri;
			}
			else
			{
				uri = mExisting;
				getContentResolver().update(uri, values, null, null);
			}

			/* Store the result as if success, but as a convenience for
			 * the user we'll go ahead and check that there is a server
			 * waiting for us.  If not, we'll return back to this edit
			 * screen. */
			SourceCheckSettings.actionCheckSettings(SourceAdd.this, uri);
		}
	};
}
