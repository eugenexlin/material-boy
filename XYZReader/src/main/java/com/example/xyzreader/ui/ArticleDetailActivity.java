package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.app.SharedElementCallback;
import android.support.v4.app.ShareCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.xyzreader.ui.ArticleListActivity.EXTRA_CURRENT_POSITION;
import static com.example.xyzreader.ui.ArticleListActivity.EXTRA_STARTING_POSITION;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */
public class ArticleDetailActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    static final String TEXT_SCROLL_POSITION = "TEXT_SCROLL_POSITION";

    private Cursor mCursor;

    public HashMap<Long, Integer> IdToScrollY = new HashMap<>();

    //id of the entry, we will convert it to position as soon as cursor ready
    private long mStartId;
    private long mInitialId;
    private boolean IsBarProbablyShowingPicture = true;

    private int mStartPosition;
    private int mSelectedPosition;

    private long mSelectedItemId;
    private int mSelectedItemUpButtonFloor = Integer.MAX_VALUE;
    private int mTopInset;

    FloatingActionButton mFAB;

    public ImageView mBarPicture;

    private AppBarLayout mAppBar;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;
//    private View mUpButtonContainer;
//    private View mUpButton;

    private ArticleDetailFragment mDetailFragment;
    private boolean isFinishTransition;

    public boolean isEnterTransitionFinished;
    // we delay the loading of text for 2 reasons
    // loading the text on the page with shared element
    // COMPLETELY SNUFFS OUT YOUR APP WITH NO EXPLANATION.
    // next, for the viewpager pages not on main
    // we need delay also to not do it during animation because
    // it makes it choppy and ugly
    private ArticleDetailFragment delayedTextLoadFragment;
    public void signMeUpForDelayedTextLoad(ArticleDetailFragment frag){
        delayedTextLoadFragment = frag;
    }

    private HashMap<Long, ArticleDetailFragment> mFragments = new HashMap<>();
    public void addFragment(Long id, ArticleDetailFragment frag){
        mFragments.put(id, frag);
    }
    public void removeFragment(Long id){
        mFragments.remove(id);
    }

    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {

            if (isFinishTransition) {
                // TODO REMOVE THIS TO HAVE RE ENTER TRANSITION
                // BUT IT WILL BE BUGGY FOR WEIRD OPEN GL REASONS I THINK.
                // views flash or twitch for 1 frame.
                // caused by large text existing on fragment.
                boolean SKIP_REENTER_TRANSITION = true;
                if (SKIP_REENTER_TRANSITION){
                    names.clear();
                    sharedElements.clear();
                    return;
                }



                names.clear();
                sharedElements.clear();
                if(!IsBarProbablyShowingPicture){
                    return;
                }
                names.add(mBarPicture.getTransitionName());
                sharedElements.put(mBarPicture.getTransitionName(), mBarPicture);
            }
        }
    };

    @Override
    public void finishAfterTransition() {
        isFinishTransition = true;
        Intent data = new Intent();
        data.putExtra(EXTRA_STARTING_POSITION, mStartPosition);
        data.putExtra(EXTRA_CURRENT_POSITION, mSelectedPosition);
        setResult(RESULT_OK, data);
        super.finishAfterTransition();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_article_detail);
        postponeEnterTransition();
        setEnterSharedElementCallback(mCallback);
        getWindow().getSharedElementEnterTransition().addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) mAppBar.getLayoutParams();
                AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
                if(behavior!=null) {
                    CoordinatorLayout clRoot = (CoordinatorLayout) findViewById(R.id.cl_root);
                    behavior.onNestedFling(clRoot, mAppBar, null, 0, 0.05f, true);
                }
                isEnterTransitionFinished = true;

                if (delayedTextLoadFragment != null){
                    delayedTextLoadFragment.loadText();
                }
            }

            @Override
            public void onTransitionCancel(Transition transition) {
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }
        });

        mBarPicture= (ImageView) findViewById(R.id.backdrop);

        mFAB = (FloatingActionButton) findViewById(R.id.fab_share);
        mFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mFragments.get(mSelectedItemId).getShareText();
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder
                        .from(ArticleDetailActivity.this)
                    .setType("text/plain")
                    .setText(text)
                    .getIntent(), getString(R.string.action_share)));
                // TODO FAB there is probably some way to
                // generate a link to force you to download the app
                // and then this would just send that intent URL link
                // that would open the article detail activity directly.
            }
        });

        mAppBar = (AppBarLayout) findViewById(R.id.appbar);
        mAppBar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
//                IsBarProbablyShowingPicture =
//                        (appBarLayout.getTotalScrollRange() + verticalOffset
//                                >
//                                320 );

                if (verticalOffset == 0) {
                    mFAB.hide();
                }else{
                    mFAB.show();
                }

                IsBarProbablyShowingPicture =
                        (appBarLayout.getTotalScrollRange() + verticalOffset
                                >
                        appBarLayout.getTotalScrollRange()/2 );
//                System.out.println(appBarLayout.getTotalScrollRange() + " " + verticalOffset);
            }
        });

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                onBackPressed();
            }
        });

        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().getData() != null) {
                mStartId = ItemsContract.Items.getItemId(getIntent().getData());
            }
        }

        getLoaderManager().initLoader(0, null, this);

        mPagerAdapter = new MyPagerAdapter(getFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageMargin((int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));
        mPager.setPageMarginDrawable(new ColorDrawable(0x22000000));

        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
            }

            @Override
            public void onPageSelected(int position) {
                if (mCursor != null) {
                    mCursor.moveToPosition(position);
                }

                mSelectedPosition = position;
                mSelectedItemId = mCursor.getLong(ArticleLoader.Query._ID);

                if (mFragments.containsKey(mSelectedItemId)){
                    mFragments.get(mSelectedItemId).loadText();
                }

                mBarPicture.setTransitionName(mCursor.getString(ArticleLoader.Query.TITLE));
                Picasso.with(ArticleDetailActivity.this)
                        .load(mCursor.getString(ArticleLoader.Query.PHOTO_URL))
                        .placeholder(R.drawable.empty_detail)
                        .noFade()
                        .into(mBarPicture);
//                updateUpButtonPosition();

            }
        });

    }

    @Override
    public void onBackPressed() {

        // we need to clear out text to make the shared element transition work.
        // why is it so much working around shared element transition
        // adding it was the biggest mistake
        // and 90% of this whole project's time
        for(Map.Entry<Long, ArticleDetailFragment> entry : mFragments.entrySet()) {
            Long id = entry.getKey();
            ArticleDetailFragment frag = entry.getValue();
            frag.clearText();
        }

        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_CURRENT_POSITION, mSelectedPosition);
        outState.putSerializable(TEXT_SCROLL_POSITION, IdToScrollY);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        HashMap<Long,Integer> scrolls = (HashMap<Long, Integer>) savedInstanceState.getSerializable(TEXT_SCROLL_POSITION);
        if (scrolls != null){
            IdToScrollY = scrolls;
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        mPagerAdapter.notifyDataSetChanged();

        // some oddity with starter code, as the StartId is always
        // going to be some huge number.

        if (mStartId > 0) {
            mInitialId = mStartId;
            mCursor.moveToFirst();
            // TODO: optimize
            while (!mCursor.isAfterLast()) {
                if (mCursor.getLong(ArticleLoader.Query._ID) == mStartId) {
                    mStartPosition = mCursor.getPosition();
                    mSelectedPosition = mStartPosition;
                    mPager.setCurrentItem(mStartPosition, false);

                    mBarPicture.setTransitionName(mCursor.getString(ArticleLoader.Query.TITLE));
                    Picasso.with(ArticleDetailActivity.this)
                            .load(mCursor.getString(ArticleLoader.Query.PHOTO_URL))
                            .placeholder(R.drawable.empty_detail)
                            .noFade()
                            .into(mBarPicture);
                    break;
                }
                mCursor.moveToNext();
            }
            mStartId = 0;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        mPagerAdapter.notifyDataSetChanged();
    }

    private class MyPagerAdapter extends FragmentStatePagerAdapter {
        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            mDetailFragment = (ArticleDetailFragment) object;
            if (mDetailFragment != null) {

            }
        }

        @Override
        public Fragment getItem(int position) {
            mCursor.moveToPosition(position);
            return ArticleDetailFragment
                    .newInstance(
                            mCursor.getLong(ArticleLoader.Query._ID),
                            position == mStartPosition);
        }

        @Override
        public int getCount() {
            return (mCursor != null) ? mCursor.getCount() : 0;
        }
    }
}
