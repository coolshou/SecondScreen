package com.melnykov.fab;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.ImageButton;

import com.farmerbb.secondscreen.R;

/**
 * Android Google+ like floating action button which reacts on the attached list view scrolling events.
 *
 * @author Oleksandr Melnykov
 */
public class FloatingActionButton extends ImageButton {
    private static final int TRANSLATE_DURATION_MILLIS = 200;
    private FabOnScrollListener mOnScrollListener;

    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_MINI = 1;

    protected AbsListView mListView;

    private boolean mVisible;

    private int mColorNormal;
    private int mColorPressed;
    private int mColorRipple;
    private boolean mShadow;
    private int mType;

    private final Interpolator mInterpolator = new AccelerateDecelerateInterpolator();

    public FloatingActionButton(Context context) {
        this(context, null);
    }

    public FloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FloatingActionButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private boolean isLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = getDimension(
                mType == TYPE_NORMAL ? R.dimen.fab_size_normal : R.dimen.fab_size_mini);
        if (mShadow && !isLollipop()) {
            int shadowSize = getDimension(R.dimen.fab_shadow_size);
            size += shadowSize * 2;
        }
        setMeasuredDimension(size, size);
    }

    private void init(Context context, AttributeSet attributeSet) {
        mVisible = true;
        mColorNormal = getColor(android.R.color.holo_blue_dark);
        mColorPressed = getColor(android.R.color.holo_blue_light);
        mColorRipple = getColor(android.R.color.white);
        mType = TYPE_NORMAL;
        mShadow = true;
        if (attributeSet != null) {
            initAttributes(context, attributeSet);
        }
        updateBackground();
    }

    private void initAttributes(Context context, AttributeSet attributeSet) {
        TypedArray attr = getTypedArray(context, attributeSet, R.styleable.FloatingActionButton);
        if (attr != null) {
            try {
                mColorNormal = attr.getColor(R.styleable.FloatingActionButton_fab_colorNormal,
                        getColor(android.R.color.holo_blue_dark));
                mColorPressed = attr.getColor(R.styleable.FloatingActionButton_fab_colorPressed,
                        getColor(android.R.color.holo_blue_light));
                mColorRipple = attr.getColor(R.styleable.FloatingActionButton_fab_colorRipple,
                        getColor(android.R.color.white));
                mShadow = attr.getBoolean(R.styleable.FloatingActionButton_fab_shadow, true);
                mType = attr.getInt(R.styleable.FloatingActionButton_fab_type, TYPE_NORMAL);
            } finally {
                attr.recycle();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean updateBackgroundLollipop(Drawable content) {
        if (isLollipop()) {
            setElevation(mShadow ? getDimension(R.dimen.fab_elevation_lollipop) : 0.0f);
            RippleDrawable drawable = new RippleDrawable(new ColorStateList(new int[][]{{}}, new int[]{mColorRipple}), content, null);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    int size = getDimension(mType == TYPE_NORMAL ? R.dimen.fab_size_normal : R.dimen.fab_size_mini);
                    outline.setOval(0, 0, size, size);
                }
            });
            setClipToOutline(true);
            setBackground(drawable);
            return true;
        }
        return false;
    }

    private void updateBackground() {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, createDrawable(mColorPressed));
        drawable.addState(new int[]{}, createDrawable(mColorNormal));
        if (!updateBackgroundLollipop(drawable)) {
            setBackgroundCompat(drawable);
        }
    }

    private Drawable createDrawable(int color) {
        OvalShape ovalShape = new OvalShape();
        ShapeDrawable shapeDrawable = new ShapeDrawable(ovalShape);
        shapeDrawable.getPaint().setColor(color);

        if (mShadow && !isLollipop()) {
            LayerDrawable layerDrawable = new LayerDrawable(
                    new Drawable[]{getResources().getDrawable(R.drawable.shadow),
                            shapeDrawable});
            int shadowSize = getDimension(
                    mType == TYPE_NORMAL ? R.dimen.fab_shadow_size : R.dimen.fab_mini_shadow_size);
            layerDrawable.setLayerInset(1, shadowSize, shadowSize, shadowSize, shadowSize);
            return layerDrawable;
        } else {
            return shapeDrawable;
        }
    }

    private TypedArray getTypedArray(Context context, AttributeSet attributeSet, int[] attr) {
        return context.obtainStyledAttributes(attributeSet, attr, 0, 0);
    }

    private int getColor(int id) {
        return getResources().getColor(id);
    }

    private int getDimension(int id) {
        return getResources().getDimensionPixelSize(id);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    private void setBackgroundCompat(Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            setBackground(drawable);
        } else {
            setBackgroundDrawable(drawable);
        }
    }

    /**
     * @deprecated to be removed in next release.
     * Now {@link com.melnykov.fab.ScrollDirectionDetector} is used to detect scrolling direction.
     */
    @Deprecated
    protected int getListViewScrollY() {
        View topChild = mListView.getChildAt(0);
        return topChild == null ? 0 : mListView.getFirstVisiblePosition() * topChild.getHeight() -
                topChild.getTop();
    }

    private int getMarginBottom() {
        int marginBottom = 0;
        final ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            marginBottom = ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
        }
        return marginBottom;
    }

    public void setColorNormal(int color) {
        if (color != mColorNormal) {
            mColorNormal = color;
            updateBackground();
        }
    }

    public void setColorNormalResId(int colorResId) {
        setColorNormal(getColor(colorResId));
    }

    public int getColorNormal() {
        return mColorNormal;
    }

    public void setColorPressed(int color) {
        if (color != mColorPressed) {
            mColorPressed = color;
            updateBackground();
        }
    }

    public void setColorPressedResId(int colorResId) {
        setColorPressed(getColor(colorResId));
    }

    public int getColorPressed() {
        return mColorPressed;
    }

    public void setColorRipple(int color) {
        if (color != mColorRipple) {
            mColorRipple = color;
            updateBackground();
        }
    }

    public void setColorRippleResId(int colorResId) {
        setColorRipple(getColor(colorResId));
    }

    public int getColorRipple() {
        return mColorRipple;
    }

    public void setShadow(boolean shadow) {
        if (shadow != mShadow) {
            mShadow = shadow;
            updateBackground();
        }
    }

    public boolean hasShadow() {
        return mShadow;
    }

    public void setType(int type) {
        if (type != mType) {
            mType = type;
            updateBackground();
        }
    }

    public int getType() {
        return mType;
    }

    protected AbsListView.OnScrollListener getOnScrollListener() {
        return mOnScrollListener;
    }

    public void show() {
        show(true);
    }

    public void hide() {
        hide(true);
    }

    public void show(boolean animate) {
        toggle(true, animate, false);
    }

    public void hide(boolean animate) {
        toggle(false, animate, false);
    }

    private void toggle(final boolean visible, final boolean animate, boolean force) {
        if (mVisible != visible || force) {
            mVisible = visible;
            int height = getHeight();
            if (height == 0 && !force) {
                ViewTreeObserver vto = getViewTreeObserver();
                if (vto.isAlive()) {
                    vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            ViewTreeObserver currentVto = getViewTreeObserver();
                            if (currentVto.isAlive()) {
                                currentVto.removeOnPreDrawListener(this);
                            }
                            toggle(visible, animate, true);
                            return true;
                        }
                    });
                    return;
                }
            }
            int translationY = visible ? 0 : height + getMarginBottom();
            if (animate) {
                animate().setInterpolator(mInterpolator)
                        .setDuration(TRANSLATE_DURATION_MILLIS)
                        .translationY(translationY);
            } else {
                setTranslationY(translationY);
            }
        }
    }

    /**
     * If need to use custom {@link android.widget.AbsListView.OnScrollListener},
     * pass it to {@link #attachToListView(android.widget.AbsListView, com.melnykov.fab.FloatingActionButton.FabOnScrollListener)}
     */
    public void attachToListView(AbsListView listView) {
        attachToListView(listView, new FabOnScrollListener());
    }

    /**
     * If need to use custom {@link android.widget.AbsListView.OnScrollListener},
     * pass it to {@link #attachToListView(android.widget.AbsListView, com.melnykov.fab.FloatingActionButton.FabOnScrollListener)}
     */

    public void attachToListView(AbsListView listView, FabOnScrollListener onScrollListener) {
        mListView = listView;
        mOnScrollListener = onScrollListener;
        onScrollListener.setFloatingActionButton(this);
        onScrollListener.setListView(listView);
        mListView.setOnScrollListener(onScrollListener);
    }

    public static class FabOnScrollListener extends ScrollDirectionDetector {
        private FloatingActionButton mFloatingActionButton;

        public FabOnScrollListener() {
            setScrollDirectionListener(new ScrollDirectionListener() {
                @Override
                public void onScrollDown() {
                    mFloatingActionButton.show();
                }

                @Override
                public void onScrollUp() {
                    mFloatingActionButton.hide();
                }
            });
        }

        public void setFloatingActionButton(FloatingActionButton floatingActionButton) {
            mFloatingActionButton = floatingActionButton;
        }
    }
}
