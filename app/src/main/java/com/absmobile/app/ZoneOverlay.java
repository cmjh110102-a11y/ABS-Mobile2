package com.absmobile.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class ZoneOverlay extends View {
    public boolean calibration = false;

    private final Paint paint = new Paint(3);
    private final RectF zone = new RectF(0.39f, 0.28f, 0.61f, 0.70f);
    private float downX;
    private float downY;
    private boolean moving = false;

    public ZoneOverlay(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setCalibration(boolean enabled) {
        calibration = enabled;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        RectF rect = new RectF(
                zone.left * width,
                zone.top * height,
                zone.right * width,
                zone.bottom * height);

        paint.setColor(0xCC42F5A7);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        canvas.drawRect(rect, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!calibration) {
            return false;
        }

        float x = event.getX() / getWidth();
        float y = event.getY() / getHeight();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                moving = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (moving) {
                    float dx = x - downX;
                    float dy = y - downY;
                    zone.offset(dx, dy);
                    zone.left = Math.max(0f, zone.left);
                    zone.top = Math.max(0f, zone.top);
                    zone.right = Math.min(1f, zone.right);
                    zone.bottom = Math.min(1f, zone.bottom);
                    downX = x;
                    downY = y;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                moving = false;
                break;
        }
        return true;
    }
}
