package com.absmobile.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Calibratable 2-D image-plane strike-zone overlay. */
public class ZoneOverlay extends View {
    public volatile boolean calibration = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF zone = new RectF(0.39f, 0.29f, 0.61f, 0.69f);
    private float lastX, lastY;
    private boolean moving;

    public ZoneOverlay(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setCalibration(boolean enabled) {
        calibration = enabled;
        invalidate();
    }

    /** Returns a defensive copy in normalized view coordinates. */
    public synchronized RectF getZoneNormalized() {
        return new RectF(zone);
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        RectF r = new RectF(zone.left * w, zone.top * h, zone.right * w, zone.bottom * h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x183CF5A5);
        canvas.drawRect(r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(calibration ? 5f : 3f);
        paint.setColor(calibration ? 0xFFFFD166 : 0xDD42F5A7);
        canvas.drawRect(r, paint);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0x8842F5A7);
        canvas.drawLine(r.centerX(), r.top, r.centerX(), r.bottom, paint);
        canvas.drawLine(r.left, r.centerY(), r.right, r.centerY(), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!calibration) return false;
        float x = event.getX() / Math.max(1, getWidth());
        float y = event.getY() / Math.max(1, getHeight());
        synchronized (this) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = x; lastY = y; moving = true; return true;
                case MotionEvent.ACTION_MOVE:
                    if (moving) {
                        float dx = x - lastX, dy = y - lastY;
                        float width = zone.width(), height = zone.height();
                        float left = Math.max(0f, Math.min(1f - width, zone.left + dx));
                        float top = Math.max(0f, Math.min(1f - height, zone.top + dy));
                        zone.set(left, top, left + width, top + height);
                        lastX = x; lastY = y; invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    moving = false; return true;
                default: return true;
            }
        }
    }
}
