package org.devtcg.five.music.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.devtcg.five.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

/**
 * Simple extension of BitmapFactory to provide a few extra convenience
 * methods.
 */
public class BetterBitmapFactory extends BitmapFactory
{
	/**
	 * Decode a Uri as a bitmap, as ImageView.setImageURI would do.
	 */
	public static Bitmap decodeURI(Context ctx, Uri uri)
	{
		InputStream in = null;

		try {
			in = ctx.getContentResolver().openInputStream(uri);
			return decodeStream(in);
		} catch (IOException e) {
			return null;
		} finally {
			try {
				if (in != null)
					in.close();
			} catch (IOException e) {}
		}
	}

	/**
	 * Decode a Uri as a bitmap, using the supplied resource drawable
	 * as a fallback. 
	 */
	public static Bitmap decodeUriWithFallback(Context ctx, String uri, int resId)
	{
		if (uri == null)
			return decodeResource(ctx.getResources(), resId);
		else
		{
			Bitmap r = decodeURI(ctx, Uri.parse(uri));

			if (r == null)
				r = decodeResource(ctx.getResources(), resId);

			return r;
		}		
	}
}
