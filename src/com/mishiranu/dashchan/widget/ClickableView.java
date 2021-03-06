/*
 * Copyright 2014-2016 Fukurou Mishiranu
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mishiranu.dashchan.widget;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.FrameLayout;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;

// Shows long click transitions. Can work as ListView parent without click listeners.
public class ClickableView extends FrameLayout implements View.OnClickListener, View.OnLongClickListener
{
	private WeakReference<AbsListView> mListParent;
	
	private View.OnClickListener mOnClickListener;
	private View.OnLongClickListener mOnLongClickListener;
	
	private boolean mCallClickOnUp = false;
	
	public ClickableView(Context context)
	{
		super(context);
		init();
	}
	
	public ClickableView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init();
	}
	
	private void init()
	{
		setBackgroundResource(ResourceUtils.getResourceId(getContext(), android.R.attr.selectableItemBackground, 0));
		super.setOnClickListener(this);
		super.setOnLongClickListener(this);
	}
	
	private boolean mIsTap = false;
	
	private final Runnable mTapRunnable = () ->
	{
		mIsTap = true;
		Drawable drawable = getBackground();
		if (drawable != null)
		{
			drawable.setState(ResourceUtils.PRESSED_STATE);
			drawable = drawable.getCurrent();
			if (drawable instanceof TransitionDrawable)
			{
				((TransitionDrawable) drawable).startTransition(ViewConfiguration.getLongPressTimeout());
			}
		}
	};
	
	private final Runnable mClickRunnable = () -> mOnClickListener.onClick(ClickableView.this);
	
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (!isEnabled()) return false;
		switch (event.getAction())
		{
			case MotionEvent.ACTION_DOWN:
			{
				mCallClickOnUp = false;
				mIsTap = false;
				Drawable drawable = getBackground();
				boolean listParent = isListParent();
				if ((mOnLongClickListener != null || listParent) && drawable != null)
				{
					if (!(listParent && C.API_LOLLIPOP && drawable instanceof RippleDrawable))
					{
						postDelayed(mTapRunnable, ViewConfiguration.getTapTimeout());
					}
				}
				break;
			}
			case MotionEvent.ACTION_UP:
			{
				if (mCallClickOnUp)
				{
					post(mClickRunnable);
					mCallClickOnUp = false;
				}
			}
			case MotionEvent.ACTION_CANCEL:
			{
				removeCallbacks(mTapRunnable);
				Drawable drawable = getBackground();
				if (mIsTap && !C.API_LOLLIPOP && drawable != null)
				{
					int[] lastState = drawable.getState();
					drawable.setState(ResourceUtils.PRESSED_STATE);
					Drawable current = drawable.getCurrent();
					if (current instanceof TransitionDrawable) ((TransitionDrawable) current).resetTransition();
					drawable.setState(lastState);
				}
				break;
			}
		}
		return super.onTouchEvent(event);
	}
	
	private static AbsListView getListViewParent(View view)
	{
		view = ListViewUtils.getRootViewInList(view);
		return view != null && view.getParent() instanceof AbsListView ? (AbsListView) view.getParent() : null;
	}
	
	@Override
	protected void onAttachedToWindow()
	{
		super.onAttachedToWindow();
		AbsListView listView = getListViewParent(this);
		if (listView != null) mListParent = new WeakReference<>(listView); else mListParent = null;
	}
	
	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();
		mListParent = null;
	}
	
	@Override
	public void setOnClickListener(OnClickListener l)
	{
		mOnClickListener = l;
	}
	
	@Override
	public void setOnLongClickListener(OnLongClickListener l)
	{
		mOnLongClickListener = l;
	}
	
	private boolean isListParent()
	{
		return mListParent != null && mOnClickListener == null && mOnLongClickListener == null;
	}
	
	private int getListPosition(AbsListView listView)
	{
		View view = ListViewUtils.getRootViewInList(this);
		if (view != null) return listView.getPositionForView(view); else return AbsListView.INVALID_POSITION;
	}
	
	private long mLastClick;

	@Override
	public void onClick(View v)
	{
		long t = System.currentTimeMillis();
		if (t - mLastClick < 200L) return;
		mLastClick = t;
		if (mOnClickListener != null) mOnClickListener.onClick(v); else if (isListParent())
		{
			AbsListView listView = mListParent.get();
			if (listView != null)
			{
				View view = ListViewUtils.getRootViewInList(this);
				if (view != null)
				{
					int position = getListPosition(listView);
					if (position >= 0)
					{
						listView.performItemClick(this, position, listView.getItemIdAtPosition(position));
					}
				}
			}
		}
	}
	
	@Override
	public boolean onLongClick(View v)
	{
		if (mOnLongClickListener != null)
		{
			return mOnLongClickListener.onLongClick(v);
		}
		else if (isListParent())
		{
			AbsListView listView = mListParent.get();
			if (listView != null)
			{
				View view = ListViewUtils.getRootViewInList(this);
				if (view != null)
				{
					int position = getListPosition(listView);
					if (position >= 0)
					{
						try
						{
							return (boolean) PERFORM_LONG_PRESS.invoke(listView, this, position,
									listView.getItemIdAtPosition(position));
						}
						catch (Exception e)
						{
							
						}
					}
				}
			}
		}
		else if (mOnClickListener != null)
		{
			mCallClickOnUp = true;
			return true;
		}
		return false;
	}
	
	private static final Method PERFORM_LONG_PRESS;
	
	static
	{
		try
		{
			PERFORM_LONG_PRESS = AbsListView.class.getDeclaredMethod("performLongPress", View.class,
					int.class, long.class);
			PERFORM_LONG_PRESS.setAccessible(true);
		}
		catch (Exception e)
		{
			throw new RuntimeException();
		}
	}
}