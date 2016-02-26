package com.vuze.android.remote;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

/**
 * https://gist.github.com/ksc91u/4c6bcbeff74357540a92
 * Fixes for some issues with the new DrawerLayout.
 * ksc91u/SynoDrawerLayout.java
 *
 * Fixes focus issues in the base DrawerLayout. This uses code inspired from
 * ViewPager to ensure that focus goes to the right place depending on drawer
 * state, namely first to any opened drawer, and if no drawer is opened to the
 * main content.
 *
 * Original:
 * https://gist.github.com/npombourcq/5636071
 * npombourcq / DrawerLayout.java
 */
public final class DrawerLayout extends android.support.v4.widget.DrawerLayout {

    private DrawerListener m_wrappedListener;

    private final DrawerListener m_drawerListener = new DrawerListener() {

        @Override
        public void onDrawerOpened(View drawerView) {
            // if the content has focus, transfer focus to the drawer
            if (getContentView().hasFocus())
                drawerView.requestFocus(View.FOCUS_FORWARD);

            if (m_wrappedListener != null)
                m_wrappedListener.onDrawerOpened(drawerView);
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            if (drawerView.hasFocus())
                getContentView().requestFocus(View.FOCUS_FORWARD);

            if (m_wrappedListener != null)
                m_wrappedListener.onDrawerClosed(drawerView);
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            if (m_wrappedListener != null)
                m_wrappedListener.onDrawerSlide(drawerView, slideOffset);
        }

        @Override
        public void onDrawerStateChanged(int newState) {
            if (m_wrappedListener != null)
                m_wrappedListener.onDrawerStateChanged(newState);
        }

    };

    private static final OnTouchListener s_drawerTouchListener = new OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            // prevent touch events from going through the drawer
            return true;
        }

    };

    private final OnHierarchyChangeListener m_hierarchyChangeListener = new OnHierarchyChangeListener() {

        @Override
        public void onChildViewAdded(View parent, View child) {
            if (DrawerLayout.this == parent && child != getContentView())
                child.setOnTouchListener(s_drawerTouchListener);
        }

        @Override
        public void onChildViewRemoved(View parent, View child) {

        }

    };

    public DrawerLayout(Context context) {
        super(context);
        init();
    }

    public DrawerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public DrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        if(isInTouchMode()){
            return;
        }else{
            setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            super.setDrawerListener(m_drawerListener);
            setOnHierarchyChangeListener(m_hierarchyChangeListener);
        }
    }

    private View getContentView() {
        return getChildAt(0);
    }

    @Override
    public void setDrawerListener(DrawerListener listener) {
        m_wrappedListener = listener;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if(isInTouchMode()){
            super.addFocusables(views, direction, focusableMode);
        }else{
            addFocusablesOnTV(views, direction, focusableMode);
        }
    }


    public void addFocusablesOnTV(ArrayList<View> views, int direction, int focusableMode) {
        // logic derived from ViewPager
        final int focusableCount = views.size();
        final int descendantFocusability = getDescendantFocusability();

        if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) {
            boolean opened = false;
            for (int i = 1; i < getChildCount(); i++) {
                final View drawerView = getChildAt(i);
                if (isDrawerOpen(drawerView)) {
                    opened = true;
                    drawerView.addFocusables(views, direction, focusableMode);
                }
            }

            if (!opened) {
                getContentView().addFocusables(views, direction, focusableMode);
            }
        }

        // we add ourselves (if focusable) in all cases except for when we are
        // FOCUS_AFTER_DESCENDANTS and there are some descendants focusable.
        // this is
        // to avoid the focus search finding layouts when a more precise search
        // among the focusable children would be more interesting.
        if (descendantFocusability != FOCUS_AFTER_DESCENDANTS ||
            // No focusable descendants
            (focusableCount == views.size())) {
            // Note that we can't call the superclass here, because it will
            // add all views in. So we need to do the same thing View does.
            if (!isFocusable()) {
                return;
            }
            if ((focusableMode & FOCUSABLES_TOUCH_MODE) == FOCUSABLES_TOUCH_MODE && isInTouchMode()
                && !isFocusableInTouchMode()) {
                return;
            }
            views.add(this);
        }
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if(isInTouchMode()){
            return super.onRequestFocusInDescendants(direction,previouslyFocusedRect);
        }else{
            return onRequestFocusInDescendantsOnTV(direction,previouslyFocusedRect);
        }
    }

    protected boolean onRequestFocusInDescendantsOnTV(int direction, Rect previouslyFocusedRect) {
        boolean opened = false;
        for (int i = 1; i < getChildCount(); i++) {
            final View drawerView = getChildAt(i);
            if (isDrawerOpen(drawerView)) {
                opened = true;
                if (drawerView.requestFocus(direction, previouslyFocusedRect))
                    return true;
            }
        }

        if (!opened) {
            return getContentView().requestFocus(direction, previouslyFocusedRect);
        }

        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if(isInTouchMode()){
            return super.dispatchPopulateAccessibilityEvent(event);
        }else{
            return dispatchPopulateAccessibilityEventOnTV(event);
        }
    }

    public boolean dispatchPopulateAccessibilityEventOnTV(AccessibilityEvent event) {
        boolean opened = false;
        for (int i = 1; i < getChildCount(); i++) {
            final View drawerView = getChildAt(i);
            if (isDrawerOpen(drawerView)) {
                opened = true;
                if (drawerView.dispatchPopulateAccessibilityEvent(event))
                    return true;
            }
        }

        if (!opened)
            return getContentView().dispatchPopulateAccessibilityEvent(event);

        return false;
    }

}