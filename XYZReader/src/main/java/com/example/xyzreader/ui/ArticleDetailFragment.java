/*
 * Copyright 2018 Soojeong Shin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.Loader;
import android.support.v7.graphics.Palette;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import static com.example.xyzreader.ui.ArticleListActivity.EXTRA_TEXT_SIZE;


public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";
    private static final String ARG_IMAGE_POSITION = "arg_image_position";
    private static final String ARG_STARTING_IMAGE_POSITION = "arg_starting_image_position";

    private int mPosition;
    private int mStartingPosition;

    /** A string for the text size currently set in Preferences */
    private String mTextSizeStr;

    /** Index value for substring method */
    private static final int START_INDEX = 0;
    private static final int END_INDEX = 1000;

    public static final String ARG_ITEM_ID = "item_id";
    private static final float PARALLAX_FACTOR = 1.25f;

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mVibrantColor = 0xFF006F7A;
    private ObservableScrollView mScrollView;
    private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    private ColorDrawable mStatusBarColorDrawable;

    private int mTopInset;
    private View mPhotoContainerView;
    private ImageView mPhotoView;
    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;

    private static final String BY_FONT_COLOR = " by <font color='#ffffff'>";
    private static final String FONT = "</font>";
    private static final String REPLACEMENT_BR = "<br />";

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    /** An arbitrary listener for image loading */
    private Target mTarget = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            if (bitmap != null) {
                // Generate the palette asynchronously using an AsyncTask to gather
                // the Palette swatch information from the bitmap
                // Reference: @see "https://github.com/codepath/android_guides/wiki/Dynamic-Color-using-Palettes"
                // @see "https://developer.android.com/training/material/palette-colors#java"
                Palette.from(bitmap).maximumColorCount(12).generate(new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(Palette palette) {
                        Palette.Swatch vibrant = palette.getVibrantSwatch();
                        if (vibrant != null) {
                            mVibrantColor = vibrant.getRgb();
                            mRootView.findViewById(R.id.meta_bar)
                                    .setBackgroundColor(mVibrantColor);
                        }
                    }
                });
                mPhotoView.setImageBitmap(bitmap);
                updateStatusBar();
            }
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            Log.e(TAG, "Failed to load images");
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId, int position, int startingPosition) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        arguments.putInt(ARG_IMAGE_POSITION, position);
        arguments.putInt(ARG_STARTING_IMAGE_POSITION, startingPosition);

        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }
        if (getArguments().containsKey(ARG_IMAGE_POSITION)) {
            mPosition = getArguments().getInt(ARG_IMAGE_POSITION);
        }
        if (getArguments().containsKey(ARG_STARTING_IMAGE_POSITION)) {
            mStartingPosition = getArguments().getInt(ARG_STARTING_IMAGE_POSITION);
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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mDrawInsetsFrameLayout = mRootView.findViewById(R.id.draw_insets_frame_layout);
        mDrawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                mTopInset = insets.top;
            }
        });

        mScrollView = mRootView.findViewById(R.id.scrollview);
        mScrollView.setCallbacks(new ObservableScrollView.Callbacks() {
            @Override
            public void onScrollChanged() {
                mScrollY = mScrollView.getScrollY();
                getActivityCast().onUpButtonFloorChanged(mItemId, ArticleDetailFragment.this);
                mPhotoContainerView.setTranslationY((int) (mScrollY - mScrollY / PARALLAX_FACTOR));
                updateStatusBar();
            }
        });

        // Hide FAB when the user scrolls down and show FAB when scrolling up the layout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hideFab();
        }

        // Get the text size string via Intent
        mTextSizeStr = getActivityCast().getIntent().getStringExtra(EXTRA_TEXT_SIZE);

        mPhotoView = mRootView.findViewById(R.id.photo);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Make sure the Fragments are using the same transition name
            String transitionName = getString(R.string.transition_photo) + mPosition;
            mPhotoView.setTransitionName(transitionName);
        }

        mPhotoContainerView = mRootView.findViewById(R.id.photo_container);

        mStatusBarColorDrawable = new ColorDrawable(0);

        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String title = mCursor.getString(ArticleLoader.Query.TITLE);
                String author = mCursor.getString(ArticleLoader.Query.AUTHOR);
                String text = title + getString(R.string.share_text_by) + author;
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText(text)
                        .getIntent(), getString(R.string.action_share)));
            }
        });

        // It is recommended to remove the call to bindViews() since at this point the loader was
        // not initialized yet, which happens inside the onActivityCreated(), so there are still
        // no items to display. By calling this method here, we'll probably have a null Cursor.
        // Reference: @see "https://discussions.udacity.com/t/weird-behaviour-of-shared-transition/293114"
        // However, after removing bindViews(), when pressing the up button in the DetailActivity and
        // then click the item in MainActivity, mPhotoView sometimes load a different image. So I
        // choose to call bindViews() from onCreateView().
        bindViews();
        updateStatusBar();
        return mRootView;
    }

    /**
     * Hide FAB when the user scrolls down and show FAB when scrolling up the layout.
     * https://stackoverflow.com/questions/34560770/hide-fab-in-nestedscrollview-when-scrolling
     */
    @TargetApi(Build.VERSION_CODES.M)
    private void hideFab() {
        mScrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (scrollY > oldScrollY) {
                    // Hide FAB
                    mRootView.findViewById(R.id.share_fab).setVisibility(View.INVISIBLE);
                } else {
                    // Show FAB
                    mRootView.findViewById(R.id.share_fab).setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void updateStatusBar() {
        int color = 0;
        if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mVibrantColor) * 0.9),
                    (int) (Color.green(mVibrantColor) * 0.9),
                    (int) (Color.blue(mVibrantColor) * 0.9));
        }
        mStatusBarColorDrawable.setColor(color);
        mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
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

        TextView titleView = mRootView.findViewById(R.id.article_title);
        TextView bylineView = mRootView.findViewById(R.id.article_byline);
        bylineView.setMovementMethod(new LinkMovementMethod());
        TextView bodyView = mRootView.findViewById(R.id.article_body);

        // Set text size based on the Value in SharedPreferences
        setBodyTextSize(bodyView);

        // Make bodyView selectable on long click
        setBodyTextSelectable(bodyView);

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
                                + BY_FONT_COLOR
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + FONT));

            } else {
                // If date is before 1902, just show the string
                bylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + BY_FONT_COLOR
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)
                                + FONT));

            }
            // Truncate the text to avoid a delay with the transition
            bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)
                    .substring(START_INDEX, END_INDEX).replaceAll("(\r\n|\n)", REPLACEMENT_BR)));
            // Use Picasso library to load the images
            // Reference: @see "https://stackoverflow.com/questions/20181491/use-picasso-to-get-a-callback-with-a-bitmap"
            Picasso.with(getActivity())
                    .load(mCursor.getString(ArticleLoader.Query.PHOTO_URL))
                    .error(R.drawable.photo_error)
                    .into(mTarget);

            // Start the postponed transition in an OnPreDrawListener, which will be called after
            // the shared element has been measured and laid out.
            scheduleStartPostponedTransition(mPhotoView);
        } else {
            mRootView.setVisibility(View.GONE);
            titleView.setText("N/A");
            bylineView.setText("N/A" );
            bodyView.setText("N/A");
        }
    }

    /**
     * Set text size based on the Value in SharedPreferences.
     */
    private void setBodyTextSize(TextView textView) {
        if (mTextSizeStr.equals(getString(R.string.pref_text_size_small))) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getActivityCast()
                    .getResources().getDimension(R.dimen.sp18));
        } else if (mTextSizeStr.equals(getString(R.string.pref_text_size_medium))) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getActivityCast()
                    .getResources().getDimension(R.dimen.sp20));
        } else if (mTextSizeStr.equals(getString(R.string.pref_text_size_large))) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getActivityCast()
                    .getResources().getDimension(R.dimen.sp22));
        } else if (mTextSizeStr.equals(getString(R.string.pref_text_size_extra_large))) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getActivityCast()
                    .getResources().getDimension(R.dimen.sp24));
        }
    }

    /**
     * This method allows the user to select body text when the user performs the long click.
     * If setting text to be selectable without setting OnLongClickListener, one click on the
     * TextView will scroll right away, so set it inside setOnLongClickListener to avoid this.
     */
    private void setBodyTextSelectable(final TextView textView) {
        textView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                textView.setTextIsSelectable(true);
                return false;
            }
        });
    }

    /**
     * Start the postponed transition in an OnPreDrawListener, which will be called after
     * the shared element has been properly loaded.
     */
    private void scheduleStartPostponedTransition(final View sharedElement) {
        if (mPosition == mStartingPosition) {
            sharedElement.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    sharedElement.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ActivityCompat.startPostponedEnterTransition(getActivityCast());
                    }
                    return true;
                }
            });
        }
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> cursorLoader, Cursor cursor) {
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
    public void onLoaderReset(@NonNull Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    public int getUpButtonFloor() {
        if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
                : mPhotoView.getHeight() - mScrollY;
    }

    @Nullable
    ImageView getPhotoView() {
        if (isViewInBounds(getActivityCast().getWindow().getDecorView(), mPhotoView)) {
            return mPhotoView;
        }
        return null;
    }

    private static boolean isViewInBounds(@NonNull View container, @NonNull View view) {
        Rect containerBounds = new Rect();
        container.getHitRect(containerBounds);
        return view.getLocalVisibleRect(containerBounds);
    }
}
