package com.example.xyzreader.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Display;
import android.widget.FrameLayout;

import com.example.xyzreader.R;

/**
 * Created by denpa on 7/4/2018.
 */

public class MaxScreenHeightFrameLayout extends FrameLayout {

  public int mMaxHeight = 0;

  public MaxScreenHeightFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.MaxScreenHeightFrameLayout,
            0, 0);
    Display display = ((Activity) context).getWindowManager().getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);
    mMaxHeight = size.y;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    if (mMaxHeight > 0){
      int hSize = MeasureSpec.getSize(heightMeasureSpec);
      int hMode = MeasureSpec.getMode(heightMeasureSpec);

      switch (hMode){
        case MeasureSpec.AT_MOST:
          heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(hSize, mMaxHeight), MeasureSpec.AT_MOST);
          break;
        case MeasureSpec.UNSPECIFIED:
          heightMeasureSpec = MeasureSpec.makeMeasureSpec(mMaxHeight, MeasureSpec.AT_MOST);
          break;
        case MeasureSpec.EXACTLY:
          heightMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(hSize, mMaxHeight), MeasureSpec.EXACTLY);
          break;
      }
    }

    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
  }
}
