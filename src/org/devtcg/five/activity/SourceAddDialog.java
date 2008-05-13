package org.devtcg.five.activity;

import org.devtcg.five.R;
import org.devtcg.five.provider.Five;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class SourceAddDialog extends Dialog
{
	private static final String TAG = "SourceAddDialog";
	
	private OnSourceAddListener mListener;
	
	private EditText mLabel;
	private EditText mHostname;
	private EditText mPassword;
	
	public interface OnSourceAddListener
	{
		void sourceAdd(SourceAddDialog dialog, String name, 
		  String host, String password);
	}
	
	public SourceAddDialog(Context ctx)
	{
		this(ctx, null);
	}

	public SourceAddDialog(Context ctx, final OnSourceAddListener l)
	{
		super(ctx);

		mListener = new OnSourceAddListener()
		{
			public void sourceAdd(SourceAddDialog dialog, String name, 
			  String host, String password)
			{
				ContentResolver content = 
				  getContext().getContentResolver();

				ContentValues v = new ContentValues();

				if (name.equals("") == true)
					name = "<Default>";

				v.put(Five.Sources.NAME, name);
				v.put(Five.Sources.HOST, host);
				v.put(Five.Sources.PORT, 5545);

				content.insert(Five.Sources.CONTENT_URI, v);

				if (l != null)
					l.sourceAdd(dialog, name, host, password);
			}
		};

		setContentView(R.layout.source_add);
		setTitle("Add Server Source");

		Button add = (Button)findViewById(R.id.add);
		add.setOnClickListener(mAddClick);

		Button cancel = (Button)findViewById(R.id.cancel);
		cancel.setOnClickListener(mCancelClick);

		mLabel = (EditText)findViewById(R.id.source_label);
		mHostname = (EditText)findViewById(R.id.source_host);
		mPassword = (EditText)findViewById(R.id.source_password);
	}

	public void setSourceAddListener(OnSourceAddListener l)
	{
		mListener = l;
	}

	private Button.OnClickListener mAddClick = new Button.OnClickListener()
	{
		public void onClick(View v)
		{
			mListener.sourceAdd(SourceAddDialog.this,
			  mLabel.getText().toString(),
			  mHostname.getText().toString(),
			  mPassword.getText().toString());

			dismiss();
		}
	};

	private Button.OnClickListener mCancelClick = new Button.OnClickListener()
	{
		public void onClick(View v)
		{
			cancel();
		}
	};
}
