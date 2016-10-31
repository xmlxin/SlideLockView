package com.ouyben.slidelockview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import static android.content.ContentValues.TAG;

/**
 * TODO :
 * Created by owen
 * on 2016-10-10.
 */

public class SlideLockView extends View {

    private Bitmap mLockBitmap;
    private int mLockDrawableId;
    private Paint mPaint;
    private int mLockRadius;
    private String mTipText;
    private int mTipsTextSize;
    private int mTipsTextColor;
    private Rect mTipsTextRect = new Rect();

    private int height, with;
    private float mLocationX;
    private boolean mIsDragable = false;
    private OnLockListener mLockListener;

    public SlideLockView(Context context) {
        this(context, null);
    }

    public SlideLockView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlideLockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray tp = context.obtainStyledAttributes(attrs, R.styleable.SlideLockView, defStyleAttr, 0);
        mLockDrawableId = tp.getResourceId(R.styleable.SlideLockView_lock_drawable, -1);
        mLockRadius = tp.getDimensionPixelOffset(R.styleable.SlideLockView_lock_radius, 1);
        mTipText = tp.getString(R.styleable.SlideLockView_lock_tips_tx);
        mTipsTextSize = tp.getDimensionPixelOffset(R.styleable.SlideLockView_locl_tips_tx_size, 12);
        mTipsTextColor = tp.getColor(R.styleable.SlideLockView_lock_tips_tx_color, Color.BLACK);

        tp.recycle();

        if (mLockDrawableId == -1) {
            throw new RuntimeException("未设置滑动解锁图片");
        }

        init(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        height = getMeasuredHeight();
        with = getMeasuredWidth();
    }

    private void init(Context context) {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(mTipsTextSize);
        mPaint.setColor(mTipsTextColor);

        mLockBitmap = BitmapFactory.decodeResource(context.getResources(), mLockDrawableId);
        int oldSize = mLockBitmap.getHeight();
        int newSize = mLockRadius * 2;
        float scale = newSize * 1.0f / oldSize;
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        mLockBitmap = Bitmap.createBitmap(mLockBitmap, 0, 0, oldSize, oldSize, matrix, true);
    }

    /**
     * TODO: 重绘控件
     *
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {

        canvas.getClipBounds(mTipsTextRect);
        int cHeight = mTipsTextRect.height();
        int cWidth = mTipsTextRect.width();
        mPaint.setTextAlign(Paint.Align.LEFT);
        mPaint.setColor(mTipsTextColor);// 重绘字体颜色
        mPaint.getTextBounds(mTipText, 0, mTipText.length(), mTipsTextRect);
        float x = cWidth / 2f - mTipsTextRect.width() / 2f - mTipsTextRect.left;
        float y = cHeight / 2f + mTipsTextRect.height() / 2f - mTipsTextRect.bottom;
        canvas.drawText(mTipText, x, y, mPaint);

        int rightMax = getWidth() - mLockRadius * 2;
        // 保证滑动图片绘制居中 (height / 2 - mLockRadius)
        if (mLocationX < 0) {
            canvas.drawBitmap(mLockBitmap, 0, height / 2 - mLockRadius, mPaint);
        } else if (mLocationX > rightMax) {
            canvas.drawBitmap(mLockBitmap, rightMax, height / 2 - mLockRadius, mPaint);
        } else {
            canvas.drawBitmap(mLockBitmap, mLocationX, height / 2 - mLockRadius, mPaint);
        }

    }

    /**
     * TODO: 滑动事件
     * 1、当触摸屏幕是触发ACTION_DOWN事件，计算时候触摸到锁，只有当触到锁的时候才能滑动；
     * 2、手指移动时，获得新的位置后计算新的位置，然后重新绘制，若移动到另一端表示解锁成功，执行回调方法解锁成功；
     * 3、手指离开屏幕后重新reset View,动画回到初始位置
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                float xPos = event.getX();
                float yPos = event.getY();
                if (isTouchLock(xPos, yPos)) {
                    Log.e(TAG, "onTouchEvent: 触摸目标");
                    mLocationX = xPos - mLockRadius;
                    mIsDragable = true;
                    invalidate();
                } else {
                    mIsDragable = false;
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {// 当图片过小, 这个方法可以很好重置,保证用户体验流畅
                if (!mIsDragable)
                    return true;
                resetLock();
                break;
            }
            case MotionEvent.ACTION_MOVE: {

                if (!mIsDragable)
                    return true;

                int rightMax = getWidth() - mLockRadius * 2;
                resetLocationX(event.getX(), rightMax);
                invalidate();

                if (mLocationX >= rightMax) {
                    mIsDragable = false;
                    mLocationX = 0;
                    invalidate();
                    if (mLockListener != null) {
                        mLockListener.onOpenLockSuccess();
                    }
                    Log.e("AnimaterListener", "解锁成功");
                }

                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!mIsDragable)
                    return true;
                resetLock();
                break;
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * TODO: 回到初始位置
     */
    private void resetLock() {
        ValueAnimator anim = ValueAnimator.ofFloat(mLocationX, 0);
        anim.setDuration(300);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mLocationX = (Float) valueAnimator.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    private void resetLocationX(float eventXPos, float rightMax) {

        float xPos = eventXPos;
        mLocationX = xPos - mLockRadius;
        if (mLocationX < 0) {
            mLocationX = 0;
        } else if (mLocationX >= rightMax) {
            mLocationX = rightMax;
        }
    }

    /**
     * TODO: 判断是不是在目标点上
     *
     * @param xPos
     * @param yPox
     * @return
     */
    private boolean isTouchLock(float xPos, float yPox) {
        float centerX = mLocationX + mLockRadius;
        float diffX = xPos - centerX;
        float diffY = yPox - mLockRadius;

        return diffX * diffX + diffY * diffY < mLockRadius * mLockRadius;
    }


    public void setLockListener(OnLockListener lockListener) {
        this.mLockListener = lockListener;
    }

    public interface OnLockListener {
        void onOpenLockSuccess();
    }

    public String getTipText() {
        return mTipText;
    }

    public void setTipText(String tipText) {
        mTipText = tipText;
    }

    public int getTipsTextColor() {
        return mTipsTextColor;
    }

    public void setTipsTextColor(int tipsTextColor) {
        mTipsTextColor = tipsTextColor;
    }
}
