package com.example.xyzreader.ui;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.app.SharedElementCallback;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;
import com.squareup.picasso.Picasso;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 *
 *
 * NOTE image transition code referenced from https://github.com/alexjlockwood/adp-activity-transitions/
 */
public class ArticleListActivity extends Activity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private ArticleListActivity mActivity;

    private Bundle mReenterState;

    static final String EXTRA_STARTING_POSITION = "EXTRA_STARTING_POSITION";
    static final String EXTRA_CURRENT_POSITION = "EXTRA_CURRENT_POSITION";

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            if (mReenterState != null) {
                int startingPosition = mReenterState.getInt(EXTRA_STARTING_POSITION);
                int currentPosition = mReenterState.getInt(EXTRA_CURRENT_POSITION);
                if (startingPosition != currentPosition) {
                    View newSharedElement = mRecyclerView
                            .findViewWithTag(getTagFromPosition(currentPosition));
                    if (newSharedElement != null) {
                        String newTransitionName = newSharedElement.getTransitionName();
                        names.clear();
                        names.add(newTransitionName);
                        sharedElements.clear();
                        sharedElements.put(newTransitionName, newSharedElement);
                    }
                }

                mReenterState = null;
            } else {
//                View navigationBar = findViewById(android.R.id.navigationBarBackground);
//                View statusBar = findViewById(android.R.id.statusBarBackground);
//                if (navigationBar != null) {
//                    names.add(navigationBar.getTransitionName());
//                    sharedElements.put(navigationBar.getTransitionName(), navigationBar);
//                }
//                if (statusBar != null) {
//                    names.add(statusBar.getTransitionName());
//                    sharedElements.put(statusBar.getTransitionName(), statusBar);
//                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        mActivity = this;

        setExitSharedElementCallback(mCallback);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        final View toolbarContainerView = findViewById(R.id.toolbar_container);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        mReenterState = new Bundle(data.getExtras());
        int startingPosition = mReenterState.getInt(EXTRA_STARTING_POSITION);
        int currentPosition = mReenterState.getInt(EXTRA_CURRENT_POSITION);
        if (startingPosition != currentPosition) {
            mRecyclerView.scrollToPosition(currentPosition);
        }
        postponeEnterTransition();
        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                mRecyclerView.requestLayout();
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(mCursor, position);
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public String getTagFromPosition(int position) {
        return "TRANSITION_TAG_" + position;
    }

    private class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public final ImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;

        private long mId;

        public ViewHolder(View view) {
            super(view);
            view.setOnClickListener(this);
            thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }

        public void bind(Cursor cursor, int position) {
            cursor.moveToPosition(position);
            titleView.setText(cursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate;
            try {
                String date = cursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                publishedDate =  dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                publishedDate =  new Date();
            }
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + cursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                                + "<br/>" + " by "
                                + cursor.getString(ArticleLoader.Query.AUTHOR)));
            }

            thumbnailView.setTransitionName(cursor.getString(ArticleLoader.Query.TITLE));
            thumbnailView.setTag(getTagFromPosition(position));

            mId = cursor.getLong(ArticleLoader.Query._ID);

            Picasso.with(mActivity)
                    .load(cursor.getString(ArticleLoader.Query.PHOTO_URL))
                    .noFade()
                    .into(thumbnailView);

        }

        @Override
        public void onClick(View v) {
            String transitionName = ViewCompat.getTransitionName(thumbnailView);
            Intent intent = new Intent(ArticleListActivity.this, ArticleDetailActivity.class);
            intent.setData(ItemsContract.Items.buildItemUri(mId));

            ActivityOptions options =
                    ActivityOptions.makeSceneTransitionAnimation(
                            mActivity,
                            thumbnailView,
                            transitionName);
            startActivity(intent, options.toBundle());
        }
    }
}
