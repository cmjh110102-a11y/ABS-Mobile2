package com.absmobile.app;

import android.graphics.RectF;
import android.os.SystemClock;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * Experimental on-device ball candidate detector.
 * It uses luma + frame-difference connected components, then short track continuity.
 * This is NOT a trained baseball detector and must be validated with real pitch footage.
 */
public final class BaseballPitchAnalyzer implements ImageAnalysis.Analyzer {
    public interface Listener {
        RectF zone();
        void onTracking(String message, float x, float y, float confidence);
        void onDecision(boolean strike, float confidence, String reason);
    }

    private static final int SAMPLE = 3;
    private static final int MAX_TRACK = 8;
    private final Listener listener;
    private final int targetIntervalMs;
    private byte[] previous;
    private int previousW, previousH;
    private final ArrayDeque<Point> track = new ArrayDeque<>();
    private long lastAnalyzeMs, lastDecisionMs;
    private volatile boolean enabled = true;
    private volatile boolean resetTrack = false;

    private static final class Point {
        final float x, y, confidence;
        final long time;
        Point(float x, float y, float c, long t) { this.x=x; this.y=y; confidence=c; time=t; }
    }
    private static final class Candidate {
        float x, y, score;
        Candidate(float x, float y, float score) { this.x=x; this.y=y; this.score=score; }
    }

    public BaseballPitchAnalyzer(Listener listener, boolean balanced) {
        this.listener = listener;
        this.targetIntervalMs = balanced ? 33 : 16;
    }

    public void setEnabled(boolean value) { enabled = value; if (!value) resetTrack = true; }

    @Override public void analyze(ImageProxy image) {
        try {
            if (resetTrack) { track.clear(); previous = null; resetTrack = false; }
            long now = SystemClock.elapsedRealtime();
            if (!enabled || now - lastAnalyzeMs < targetIntervalMs) return;
            lastAnalyzeMs = now;
            ImageProxy.PlaneProxy plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int width = image.getWidth(), height = image.getHeight();
            int sw = Math.max(1, width / SAMPLE), sh = Math.max(1, height / SAMPLE);
            byte[] current = new byte[sw * sh];
            int rowStride = plane.getRowStride(), pixelStride = plane.getPixelStride();
            for (int y=0; y<sh; y++) {
                int srcY = Math.min(height-1, y*SAMPLE);
                for (int x=0; x<sw; x++) {
                    int srcX = Math.min(width-1, x*SAMPLE);
                    int idx = srcY * rowStride + srcX * pixelStride;
                    current[y*sw+x] = buffer.get(idx);
                }
            }
            Candidate candidate = null;
            if (previous != null && previousW == sw && previousH == sh) {
                candidate = findBrightMovingComponent(current, previous, sw, sh);
            }
            previous = current; previousW = sw; previousH = sh;
            if (candidate == null) {
                if (track.size() > 0 && now - track.peekLast().time > 260) track.clear();
                return;
            }

            float nx = candidate.x / Math.max(1, sw-1);
            float ny = candidate.y / Math.max(1, sh-1);
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation == 90) { float oldX=nx; nx=1f-ny; ny=oldX; }
            else if (rotation == 180) { nx=1f-nx; ny=1f-ny; }
            else if (rotation == 270) { float oldX=nx; nx=ny; ny=1f-oldX; }
            if (!track.isEmpty()) {
                Point last = track.peekLast();
                float distance = (float)Math.hypot(nx-last.x, ny-last.y);
                if (distance > 0.35f) track.clear();
            }
            track.addLast(new Point(nx, ny, candidate.score, now));
            while (track.size() > MAX_TRACK) track.removeFirst();
            listener.onTracking("BALL CANDIDATE", nx, ny, candidate.score);
            evaluateCrossing(now);
        } catch (Throwable ignored) {
            // Camera analysis must never crash the preview pipeline.
        } finally {
            image.close();
        }
    }

    private Candidate findBrightMovingComponent(byte[] cur, byte[] prev, int w, int h) {
        int n = w*h;
        boolean[] mask = new boolean[n], seen = new boolean[n];
        for (int i=0; i<n; i++) {
            int lum = cur[i] & 0xff;
            int diff = Math.abs(lum - (prev[i] & 0xff));
            mask[i] = lum >= 150 && diff >= 18;
        }
        int[] queue = new int[n];
        Candidate best = null;
        for (int start=0; start<n; start++) {
            if (!mask[start] || seen[start]) continue;
            int head=0, tail=0, minX=w, minY=h, maxX=0, maxY=0, sumX=0, sumY=0, sumLum=0;
            seen[start]=true; queue[tail++]=start;
            while (head<tail && tail<1200) {
                int p=queue[head++], x=p%w, y=p/w;
                minX=Math.min(minX,x); maxX=Math.max(maxX,x);
                minY=Math.min(minY,y); maxY=Math.max(maxY,y);
                sumX+=x; sumY+=y; sumLum+=(cur[p]&0xff);
                for (int yy=Math.max(0,y-1); yy<=Math.min(h-1,y+1); yy++) {
                    for (int xx=Math.max(0,x-1); xx<=Math.min(w-1,x+1); xx++) {
                        int q=yy*w+xx;
                        if (mask[q] && !seen[q]) { seen[q]=true; queue[tail++]=q; }
                    }
                }
            }
            int area=tail, bw=maxX-minX+1, bh=maxY-minY+1;
            if (area < 2 || area > 36 || bw > 12 || bh > 12) continue;
            float aspect = bw/(float)Math.max(1,bh);
            if (aspect < 0.45f || aspect > 2.2f) continue;
            float compact = area/(float)(bw*bh);
            float brightness = (sumLum/(float)area)/255f;
            float score = Math.min(0.99f, 0.25f + compact*0.30f + brightness*0.35f);
            if (best == null || score > best.score) best = new Candidate(sumX/(float)area, sumY/(float)area, score);
        }
        return best;
    }

    private void evaluateCrossing(long now) {
        if (track.size() < 3 || now-lastDecisionMs < 1800) return;
        Point a=null, b=null;
        for (Point p : track) { a=b; b=p; }
        if (a == null || b == null) return;
        RectF z = listener.zone();
        float dx=b.x-a.x, dy=b.y-a.y;
        // Detect the dominant travel axis so side-view and behind-plate camera
        // placements can both cross the calibrated 2-D zone.
        boolean horizontal = Math.abs(dx) >= Math.abs(dy);
        float t;
        boolean crossed;
        boolean strike;
        if (horizontal) {
            crossed = (a.x < z.right && b.x >= z.right && dx > 0)
                    || (a.x > z.left && b.x <= z.left && dx < 0);
            if (!crossed) return;
            float edge = dx > 0 ? z.right : z.left;
            t = Math.abs(dx) < 0.0001f ? 0.5f : (edge-a.x)/dx;
            float y = a.y + dy*t;
            strike = y >= z.top && y <= z.bottom;
        } else {
            crossed = (a.y < z.bottom && b.y >= z.bottom && dy > 0)
                    || (a.y > z.top && b.y <= z.top && dy < 0);
            if (!crossed) return;
            float edge = dy > 0 ? z.bottom : z.top;
            t = Math.abs(dy) < 0.0001f ? 0.5f : (edge-a.y)/dy;
            float x = a.x + dx*t;
            strike = x >= z.left && x <= z.right;
        }
        float confidence=Math.min(0.70f, (a.confidence+b.confidence)/2f);
        lastDecisionMs=now;
        track.clear();
        listener.onDecision(strike, confidence,
                "2-D image-plane estimate · calibrated zone crossing");
    }
}
