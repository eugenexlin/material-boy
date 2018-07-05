package com.example.xyzreader.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.example.xyzreader.R;

/**
 * Created by denpa on 7/4/2018.
 */

public class RatioFrameLayout extends FrameLayout {

  private float mHeightFromWidthMultiplier = 1.0f;

  public RatioFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.RatioFrameLayout,
            0, 0);
    try {
      mHeightFromWidthMultiplier =
              a.getFloat(R.styleable.RatioFrameLayout_heightFromWidthMultiplier, 1.0f);
    } finally {
      a.recycle();
    }
  }

  @Override protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec)
  {
    int originalWidth = MeasureSpec.getSize(widthMeasureSpec);
    int calculatedHeight = (int) (originalWidth * mHeightFromWidthMultiplier);

    super.onMeasure(
            MeasureSpec.makeMeasureSpec(originalWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(calculatedHeight, MeasureSpec.EXACTLY));
  }
}
