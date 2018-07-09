package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;

import com.squareup.picasso.Picasso;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ShareCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.graphics.Palette;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.squareup.picasso.Target;


/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "ARG_ITEM_ID";
    //before turning page
    public static final String ARG_NEEDS_TRANSITION = "ARG_NEEDS_TRANSITION";
    public static final String ARG_DELAY_TEXT_LOAD = "ARG_DELAY_TEXT_LOAD";
    private static final float PARALLAX_FACTOR = 1.25f;


    private boolean mFailedTextLoad = false;
    private Cursor mCursor;
    private String mTextPreload;
    private long mItemId;
    private boolean mNeedsTransition;
    private boolean mDelayTextLoad;

    private View mRootView;
    private TextView mBodyView;
    private int mMutedColor = 0xFF333333;
    public NestedScrollView mScrollView;
    private ColorDrawable mStatusBarColorDrawable;

    private View mPhotoContainerView;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId, boolean needsTransition) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        arguments.putBoolean(ARG_NEEDS_TRANSITION, needsTransition);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args.containsKey(ARG_ITEM_ID)) {
            mItemId = args.getLong(ARG_ITEM_ID);
        }
        if (args.containsKey(ARG_NEEDS_TRANSITION)) {
            mNeedsTransition = args.getBoolean(ARG_NEEDS_TRANSITION);
        }

        getActivityCast().addFragment(mItemId, this);
        if (mNeedsTransition) //!getActivityCast().isEnterTransitionFinished
        {
            getActivityCast().signMeUpForDelayedTextLoad(this);
        }

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);

        mScrollView = (NestedScrollView) mRootView.findViewById(R.id.scrollview);

        mPhotoContainerView = mRootView.findViewById(R.id.photo_container);

        mStatusBarColorDrawable = new ColorDrawable(0);


        //TODO FAB
//        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
//                        .setType("text/plain")
//                        .setText("Some sample text")
//                        .getIntent(), getString(R.string.action_share)));
//            }
//        });

        bindViews();

        updateStatusBar();
        return mRootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearText();
        getActivityCast().removeFragment(mItemId);
    }

    //load the text after animation because it causes an inexplicable error
    // when large texts are loaded. before
    public void loadText(){

        if (this.isDetached()){
            return;
        }

        if (mTextPreload == null){
            // goodness, the activity seemed to have RACED
            // to get here before the text loaded????
            if (mCursor == null){
                // if we failed the race, then it triggers flag
                // so on finish load cursor handler can load the text.
                mFailedTextLoad = true;
                return;
            }else{
                mTextPreload = mCursor.getString(ArticleLoader.Query.BODY);
            }
        }

        if (mBodyView.getText().length() <= 10 && mBodyView.getText() == getString(R.string.not_available_string)) {
            mBodyView.setText(Html.fromHtml(mTextPreload.replaceAll("(\r\n|\n)", "<br />")));
        }
    }

    public void clearText(){
        mBodyView.setVisibility(View.GONE);
        //mBodyView.setText(getString(R.string.not_available_string));
    }

    // TODO well nested scroll view is something else.
    public void saveScrollY(){
        if (mScrollView != null) {
            int scrollY = mScrollView.getScrollY();
            getActivityCast().IdToScrollY.put(mItemId, scrollY);
        }
    }
    public void loadScrollY(){
        if (mScrollView != null && getActivityCast().IdToScrollY.containsKey(mItemId)) {
            mScrollView.scrollTo(0, getActivityCast().IdToScrollY.get(mItemId));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveScrollY();
    }

    private void updateStatusBar() {
        int color = 0;

        color = Color.argb(255,
                (int) (Color.red(mMutedColor) * 0.9),
                (int) (Color.green(mMutedColor) * 0.9),
                (int) (Color.blue(mMutedColor) * 0.9));
        mStatusBarColorDrawable.setColor(color);
//        mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    private Date parsePublishedDate() {
        try {
            String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
            return dateFormat.parse(date);
        } catch (ParseException ex) {
            Log.e(TAG, ex.getMessage());
            Log.i(TAG, "passing today's date");
            return new Date();
        }
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        TextView titleView = (TextView) mRootView.findViewById(R.id.article_title);
        TextView bylineView = (TextView) mRootView.findViewById(R.id.article_byline);
        bylineView.setMovementMethod(new LinkMovementMethod());
        mBodyView = (TextView) mRootView.findViewById(R.id.article_body);


        mBodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Rosario-Regular.ttf"));

        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);
            titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));

            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                bylineView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + " by <font color='#ffffff'>"
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            } else {
                // If date is before 1902, just show the string
                bylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + " by <font color='#ffffff'>"
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + "</font>"));

            }

            mTextPreload = mCursor.getString(ArticleLoader.Query.BODY);
            if (mFailedTextLoad || getActivityCast().isEnterTransitionFinished){
                mFailedTextLoad = false;
                loadText();
            }

            loadScrollY();

            final Target imageTarget = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    if (bitmap != null) {
                        Palette p = Palette.generate(bitmap, 12);
                        mMutedColor = p.getDarkMutedColor(0xFF333333);
                        mRootView.findViewById(R.id.meta_bar)
                                .setBackgroundColor(mMutedColor);
                        updateStatusBar();
                    }
                    startPostponedEnterTransition();
                }

                @Override
                public void onBitmapFailed(Drawable errorDrawable) {
                    startPostponedEnterTransition();
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            };

            Picasso.with(getActivity())
                    .load(mCursor.getString(ArticleLoader.Query.PHOTO_URL))
                    .noFade()
                    .into(imageTarget);

        } else {
            mRootView.setVisibility(View.GONE);
            titleView.setText(getString(R.string.not_available_string));
            bylineView.setText(getString(R.string.not_available_string));
            mBodyView.setText(getString(R.string.not_available_string));
        }
    }

    private void startPostponedEnterTransition() {
        if (mNeedsTransition) {
            getActivityCast().mBarPicture.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getActivityCast().mBarPicture.getViewTreeObserver().removeOnPreDrawListener(this);
                    getActivity().startPostponedEnterTransition();
                    return true;
                }
            });

        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();

    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }
}
