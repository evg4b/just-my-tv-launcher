package com.justmytv.tvhome;

import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * One focus ring for the whole screen, moved by a spring (PLAN 7.4). It lives in glass_host above the grid.
 * While the grid scrolls the host itself is shifted, so the ring stays on its tile without disturbing the spring.
 */
final class FocusGlass {
    private final FrameLayout host;
    private final View view;
    private final GradientDrawable ring = new GradientDrawable();
    private final int stroke;
    private final SpringAnimation sx, sy;
    private final float pad;
    private boolean shown;

    FocusGlass(FrameLayout host, float density) {
        this.host = host;
        pad = 6 * density;
        stroke = Math.round(2 * density);
        view = new View(host.getContext());
        setMoving(false);
        view.setBackground(ring);
        view.setVisibility(View.INVISIBLE);
        host.addView(view, new FrameLayout.LayoutParams(0, 0));
        sx = spring(DynamicAnimation.TRANSLATION_X);
        sy = spring(DynamicAnimation.TRANSLATION_Y);
    }

    private SpringAnimation spring(DynamicAnimation.ViewProperty p) {
        return new SpringAnimation(view, p).setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
    }

    void follow(View card) {
        // glass_host and the grid are siblings: convert through the common parent
        ViewGroup common = (ViewGroup) host.getParent();
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        common.offsetDescendantRectToMyCoords(card, r);
        float w = card.getWidth() * GridMetrics.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * GridMetrics.FOCUS_SCALE + 2 * pad;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            view.setLayoutParams(lp);
        }
        float x = r.exactCenterX() - w / 2f - host.getLeft() - host.getTranslationX();
        float y = r.exactCenterY() - h / 2f - host.getTop() - host.getTranslationY();
        if (!shown) {                       // first focus: appear in place, no flight from the corner
            sx.cancel();
            sy.cancel();
            view.setTranslationX(x);
            view.setTranslationY(y);
            view.setVisibility(View.VISIBLE);
            shown = true;
        } else {
            sx.animateToFinalPosition(x);
            sy.animateToFinalPosition(y);
        }
    }

    /** The grid scrolled by dy: carry the ring along. */
    void scrolled(int dy) {
        host.setTranslationY(host.getTranslationY() - dy);
    }

    void setCorner(float cornerPx) {
        ring.setCornerRadius(cornerPx * GridMetrics.FOCUS_SCALE + pad);
    }

    /** Move mode shows an amber ring instead of the accent. */
    void setMoving(boolean moving) {
        ring.setStroke(stroke, moving ? 0xFFFFB74D : 0xFF8AB4F8);   // accent: Couchy's default
    }

    void hide() {
        view.setVisibility(View.INVISIBLE);
        shown = false;
    }
}
