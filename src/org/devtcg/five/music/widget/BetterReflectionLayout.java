package org.devtcg.five.music.widget;

import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class BetterReflectionLayout extends ViewGroup
{
	public static final String TAG = "BetterReflectionLayout";
	
	private ReflectionDrawable mReflection;
	private Drawable mDivider;

	public BetterReflectionLayout(Context ctx)
	{
		super(ctx);
		init(ctx);
	}

	public BetterReflectionLayout(Context ctx, AttributeSet attrs)
	{
		super(ctx, attrs);
		init(ctx);
	}

	public void init(Context context)
	{
		setDividerDrawable(context.getResources().getDrawable(android.R.drawable.divider_horizontal_dark));
	}
	
	public void setDividerDrawable(Drawable d)
	{
		mDivider = d;
	}

	public void setImageResource(int id)
	{
		setImageBitmap
		  (BitmapFactory.decodeResource(getContext().getResources(), id));
	}

	public void setImageURI(Uri uri)
	{
		InputStream stream = null;
		
		try {
			stream = getContext().getContentResolver().openInputStream(uri);
			setImageBitmap(BitmapFactory.decodeStream(stream));
		} catch (IOException e) {
			Log.w(TAG, "Couldn't load bitmap from uri: " + uri.toString(), e);
			setImageBitmap(null);
		} finally {
			try {
				stream.close();
			} catch (IOException e) {}
		}
	}

	public void setImageBitmap(Bitmap bmp)
	{
		Rect bounds;

		if (mReflection != null)
			bounds = mReflection.copyBounds();
		else
			bounds = null;

		mReflection = new ReflectionDrawable(bmp);

		if (bounds != null)
			mReflection.setBounds(bounds);
		else
			requestLayout();

		invalidate();
	}
	
	@Override
	public void addView(View child, int index, LayoutParams params)
	{
		super.addView(child, index, params);
		
		int n = getChildCount();
		while (n-- > 0)
		{
			View v = getChildAt(n);
			v.setBackgroundColor(0xbb666666);
		}
	}
	
	@Override
	protected void dispatchDraw(Canvas canvas)
	{
		mReflection.draw(canvas);

		super.dispatchDraw(canvas);
		
		mDivider.draw(canvas);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		int selfw = getMeasuredWidth();
		int selfh = getMeasuredHeight();

		int bottom = selfh;
		int childh = 0;

		int n = getChildCount();
		for (int i = n - 1; i >= 0; i--)
		{
			View child = getChildAt(i);
			int cw = child.getMeasuredWidth();
			int ch = child.getMeasuredHeight();

			child.layout(0, bottom - ch, selfw, bottom);
			bottom -= ch;
			
			if (child.getVisibility() == View.VISIBLE)
				childh += ch;
		}

		mDivider.setBounds(0, selfh - childh, selfw,
		  selfh - childh + mDivider.getIntrinsicHeight());
	}

	@Override
	protected void onMeasure(int wspec, int hspec)
	{
		/* Just let View take care of what's right... */
		super.onMeasure(wspec, hspec);

		int selfw = getMeasuredWidth();
		int selfh = getMeasuredHeight();

		mReflection.setBounds(0, 0, selfw, selfh);
		
		int childh = 0;

		int n = getChildCount();
		for (int i = 0; i < n - 1; i++)
		{
			View child = getChildAt(i);			
			child.measure
			  (getChildMeasureSpec(wspec, 0, child.getLayoutParams().width),
			   getChildMeasureSpec(hspec, 0, child.getLayoutParams().height));
		}

		if (n > 0)
		{
			View child = getChildAt(n - 1);
			
			int poolh = mReflection.getScaledPoolRect().height();
			int poolhspec;

			if (poolh > 0)
			{
				poolhspec = MeasureSpec.makeMeasureSpec(poolh,
				  MeasureSpec.EXACTLY);
			}
			else
			{
				poolhspec =
				  getChildMeasureSpec(hspec, 0, child.getLayoutParams().height);
			}

			child.measure
			  (getChildMeasureSpec(wspec, 0, child.getLayoutParams().width),
			   poolhspec);
		}
	}

	private static final class ReflectionDrawable extends Drawable
	{
		private static final int color0 = 0xbb666666;
		private static final int color1 = 0xff666666;

		private final Bitmap mBitmap;
		private final Paint mRealPaint;

		/* Drawing dimensions in terms of the bitmap. */
		private final Rect mRealRect;

		/* Location of the reflecting pool (a subrect of mRealRect). */
		private final Rect mPoolRect;

		/* Scaled rect in terms of the parent. */
		private final Rect mScaledPoolRect;

		/* Scale necessary for mRealRect to meet the drawable's bounds. */
		private float mScaleX = 0f;
		private float mScaleY = 0f;

		public ReflectionDrawable(Bitmap bmp)
		{
			super();

			mBitmap = bmp;
			mRealPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

			mRealRect = new Rect(0, 0, bmp.getWidth(), bmp.getHeight());
			mPoolRect = new Rect();
			mScaledPoolRect = new Rect();
		}

		public Bitmap getBitmap()
		{
			return mBitmap;
		}

		@Override
		protected void onBoundsChange(Rect bounds)
		{
			if (bounds.right > bounds.bottom)
			{
				mScaleX = (float)bounds.width() / (float)mRealRect.width();
				mScaleY = (float)bounds.height() / (float)mRealRect.height();

				mPoolRect.setEmpty();
				mScaledPoolRect.setEmpty();
			}
			else
			{
				/* Not a bug, since we keep aspect ratio we require that
				 * width equals height. */
				mScaleX = (float)bounds.width() / (float)mRealRect.width();
				mScaleY = (float)bounds.width() / (float)mRealRect.height();

				int poolh = bounds.bottom - bounds.right;
				int scaledh = (int)((float)poolh / mScaleY);

				mPoolRect.set(mRealRect.left, mRealRect.bottom - scaledh,
				  mRealRect.right, mRealRect.bottom);

				mScaledPoolRect.left = (int)(mPoolRect.left * mScaleX);
				mScaledPoolRect.right = (int)(mPoolRect.right * mScaleX);
				mScaledPoolRect.top = (int)(mPoolRect.top * mScaleY);
				mScaledPoolRect.bottom = (int)(mPoolRect.bottom * mScaleY);
			}

			super.onBoundsChange(bounds);
		}
		
		public Rect getScaledPoolRect()
		{
			return mScaledPoolRect;
		}

		@Override
		public void draw(Canvas canvas)
		{
			canvas.save();
			canvas.scale(mScaleX, mScaleY);
			canvas.drawBitmap(mBitmap, null, mRealRect, mRealPaint);
			canvas.restore();
		
			if (mPoolRect.isEmpty() == false)
			{
				canvas.save();
				canvas.scale(mScaleX, -mScaleY);
				canvas.translate(0, -(mRealRect.bottom * 2));
				canvas.drawBitmap(mBitmap, mPoolRect, mPoolRect, mRealPaint);
				canvas.translate(0, mPoolRect.height());
				canvas.restore();
			}
		}

		@Override
		public int getOpacity()
		{
			return PixelFormat.TRANSLUCENT;
		}

		@Override
		public void setAlpha(int alpha) {}

		@Override
		public void setColorFilter(ColorFilter cf) {}
	}
}
