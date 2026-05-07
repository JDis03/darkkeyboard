/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.dark.keyboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.dark.keyboard.Keyboard.Key;

import java.util.List;

/**
 * Extended keyboard view with support for extension keyboards, multi-touch,
 * phone keyboard, and special key handling (F-keys, modifiers, navigation).
 *
 * <p>This view extends {@link LatinKeyboardBaseView} to add:
 * <ul>
 *   <li>Extension keyboard popup (5th row)</li>
 *   <li>Phone keyboard mode</li>
 *   <li>Multi-touch disambiguation</li>
 *   <li>Special keycodes (F1-F12, Ctrl, Alt, Meta, Fn, etc.)</li>
 *   <li>Language switching gestures</li>
 *   <li>Auto-play mode for testing</li>
 * </ul>
 */
public class LatinKeyboardView extends LatinKeyboardBaseView {
    private static final String TAG = "HK/LatinKeyboardView";

    // NOTE: The keycode list needs to stay in sync with the res/values/keycodes.xml file.

    /**
     * Custom negative keycodes for special functionality.
     * Note: These negative values avoid conflicts with standard KeyEvent keycodes.
     * Consider renumbering if KeyEvent adds more negative keycodes in the future.
     */
    
    /** Special keycode for options menu */
    static final int KEYCODE_OPTIONS = -100;
    /** Special keycode for long press on options key */
    static final int KEYCODE_OPTIONS_LONGPRESS = -101;
    /** Special keycode for voice input */
    static final int KEYCODE_VOICE = -102;
    /** Special keycode for F1 key (settings/help) */
    static final int KEYCODE_F1 = -103;
    /** Special keycode for switching to next language */
    static final int KEYCODE_NEXT_LANGUAGE = -104;
    /** Special keycode for switching to previous language */
    static final int KEYCODE_PREV_LANGUAGE = -105;
    /** Special keycode for clipboard access */
    static final int KEYCODE_CLIPBOARD = -106;
    /** Special keycode for compose key */
    static final int KEYCODE_COMPOSE = -10024;

    // The following keycodes match (negative) KeyEvent keycodes.
    // Would be better to use the real KeyEvent values, but many don't exist prior to the
    // Honeycomb API (level 11).
    
    static final int KEYCODE_DPAD_UP = -19;
    static final int KEYCODE_DPAD_DOWN = -20;
    static final int KEYCODE_DPAD_LEFT = -21;
    static final int KEYCODE_DPAD_RIGHT = -22;
    static final int KEYCODE_DPAD_CENTER = -23;
    static final int KEYCODE_ALT_LEFT = -57;
    static final int KEYCODE_PAGE_UP = -92;
    static final int KEYCODE_PAGE_DOWN = -93;
    static final int KEYCODE_ESCAPE = -111;
    static final int KEYCODE_FORWARD_DEL = -112;
    static final int KEYCODE_CTRL_LEFT = -113;
    static final int KEYCODE_CAPS_LOCK = -115;
    static final int KEYCODE_SCROLL_LOCK = -116;
    static final int KEYCODE_META_LEFT = -117;
    static final int KEYCODE_FN = -119;
    static final int KEYCODE_SYSRQ = -120;
    static final int KEYCODE_BREAK = -121;
    static final int KEYCODE_HOME = -122;
    static final int KEYCODE_END = -123;
    static final int KEYCODE_INSERT = -124;
    static final int KEYCODE_FKEY_F1 = -131;
    static final int KEYCODE_FKEY_F2 = -132;
    static final int KEYCODE_FKEY_F3 = -133;
    static final int KEYCODE_FKEY_F4 = -134;
    static final int KEYCODE_FKEY_F5 = -135;
    static final int KEYCODE_FKEY_F6 = -136;
    static final int KEYCODE_FKEY_F7 = -137;
    static final int KEYCODE_FKEY_F8 = -138;
    static final int KEYCODE_FKEY_F9 = -139;
    static final int KEYCODE_FKEY_F10 = -140;
    static final int KEYCODE_FKEY_F11 = -141;
    static final int KEYCODE_FKEY_F12 = -142;
    static final int KEYCODE_NUM_LOCK = -143;

    /** The phone keyboard layout used for numeric input mode */
    @Nullable
    private Keyboard mPhoneKeyboard;

    /** Whether the extension of this keyboard is visible */
    private boolean mExtensionVisible;
    
    /** The view that is shown as an extension of this keyboard */
    @Nullable
    private LatinKeyboardView mExtension;
    
    /** The popup window that contains the extension of this keyboard */
    @Nullable
    private PopupWindow mExtensionPopup;
    
    /** Whether this view is an extension of another keyboard */
    private boolean mIsExtensionType;
    
    /** Flag tracking if this is the first touch event in the current sequence */
    private boolean mFirstEvent;

    /** Whether we've started dropping move events because we found a big jump */
    private boolean mDroppingEvents;
    
    /**
     * Whether multi-touch disambiguation needs to be disabled for any reason. There are 2 reasons
     * for this to happen:
     * <ol>
     *   <li>A real multi-touch event has occurred</li>
     *   <li>We've opened an extension keyboard</li>
     * </ol>
     */
    private boolean mDisableDisambiguation;
    
    /** The distance threshold at which we start treating the touch session as a multi-touch */
    private int mJumpThresholdSquare = Integer.MAX_VALUE;
    
    /** The Y coordinate of the last row */
    private int mLastRowY;
    
    private int mExtensionLayoutResId = 0;
    
    @Nullable
    private LatinKeyboard mExtensionKeyboard;

    /**
     * Constructor used when creating from XML with just Context and AttributeSet.
     *
     * @param context the application context
     * @param attrs   the attribute set from XML
     */
    public LatinKeyboardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Constructor used when creating from XML with Context, AttributeSet, and default style.
     *
     * @param context  the application context
     * @param attrs    the attribute set from XML
     * @param defStyle the default style resource
     */
    public LatinKeyboardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // Note: Consider creating LatinKeyboardView-specific attributes in the future
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.LatinKeyboardBaseView, defStyle, R.style.LatinKeyboardBaseView);
        LayoutInflater inflate =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        int previewLayout = 0;
        int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            if (attr == R.styleable.LatinKeyboardBaseView_keyPreviewLayout) {
                previewLayout = a.getResourceId(attr, 0);
                if (previewLayout == R.layout.null_layout) previewLayout = 0;
            } else if (attr == R.styleable.LatinKeyboardBaseView_keyPreviewOffset) {
                mPreviewOffset = a.getDimensionPixelOffset(attr, 0);
            } else if (attr == R.styleable.LatinKeyboardBaseView_keyPreviewHeight) {
                mPreviewHeight = a.getDimensionPixelSize(attr, 80);
            } else if (attr == R.styleable.LatinKeyboardBaseView_popupLayout) {
                mPopupLayout = a.getResourceId(attr, 0);
                if (mPopupLayout == R.layout.null_layout) mPopupLayout = 0;
            }
        }

        final Resources res = getResources();

        // If true, popups are forced to remain inside the keyboard area. If false,
        // they can extend above it. Enable clipping just for Android P since drawing
        // outside the keyboard area doesn't work on that version.
        boolean clippingEnabled = (Build.VERSION.SDK_INT >= 28 /* Build.VERSION_CODES.P */);

        if (previewLayout != 0) {
            mPreviewPopup = new PopupWindow(context);
            if (!isInEditMode())
                Log.i(TAG, "new mPreviewPopup " + mPreviewPopup + " from " + this);
            mPreviewText = (TextView) inflate.inflate(previewLayout, null);
            mPreviewTextSizeLarge = (int) res.getDimension(R.dimen.key_preview_text_size_large);
            mPreviewPopup.setContentView(mPreviewText);
            mPreviewPopup.setBackgroundDrawable(null);
            mPreviewPopup.setTouchable(false);
            mPreviewPopup.setAnimationStyle(R.style.KeyPreviewAnimation);
            mPreviewPopup.setClippingEnabled(clippingEnabled);
        } else {
            mShowPreview = false;
        }

        if (mPopupLayout != 0) {
            mMiniKeyboardParent = this;
            mMiniKeyboardPopup = new PopupWindow(context);
            if (!isInEditMode())
                Log.i(TAG, "new mMiniKeyboardPopup " + mMiniKeyboardPopup + " from " + this);
            mMiniKeyboardPopup.setBackgroundDrawable(null);
            mMiniKeyboardPopup.setAnimationStyle(R.style.MiniKeyboardAnimation);
            mMiniKeyboardPopup.setClippingEnabled(clippingEnabled);
            mMiniKeyboardVisible = false;
        }
    }

    /**
     * Sets the phone keyboard layout.
     *
     * @param phoneKeyboard the keyboard to use in phone mode
     */
    public void setPhoneKeyboard(@Nullable Keyboard phoneKeyboard) {
        mPhoneKeyboard = phoneKeyboard;
    }

    /**
     * Sets the resource ID for the extension keyboard layout.
     *
     * @param id the layout resource ID for the extension keyboard
     */
    public void setExtensionLayoutResId(int id) {
        mExtensionLayoutResId = id;
    }
    
    @Override
    public void setPreviewEnabled(boolean previewEnabled) {
        if (getKeyboard() == mPhoneKeyboard) {
            // Phone keyboard never shows popup preview (except language switch).
            super.setPreviewEnabled(false);
        } else {
            super.setPreviewEnabled(previewEnabled);
        }
    }

    /**
     * Sets the keyboard layout.
     * Resets state from the old keyboard and initializes multi-touch thresholds.
     *
     * @param newKeyboard the keyboard to display
     */
    @Override
    public void setKeyboard(@NonNull Keyboard newKeyboard) {
        final Keyboard oldKeyboard = getKeyboard();
        if (oldKeyboard instanceof LatinKeyboard) {
            // Reset old keyboard state before switching to new keyboard.
            ((LatinKeyboard) oldKeyboard).keyReleased();
        }
        super.setKeyboard(newKeyboard);
        
        // One-seventh of the keyboard width seems like a reasonable threshold
        mJumpThresholdSquare = newKeyboard.getMinWidth() / 7;
        mJumpThresholdSquare *= mJumpThresholdSquare;
        
        // Get Y coordinate of the last row based on the row count, assuming equal height
        final int numRows = newKeyboard.mRowCount;
        mLastRowY = (newKeyboard.getHeight() * (numRows - 1)) / numRows;
        
        mExtensionKeyboard = ((LatinKeyboard) newKeyboard).getExtension();
        if (mExtensionKeyboard != null && mExtension != null) {
            mExtension.setKeyboard(mExtensionKeyboard);
        }
        setKeyboardLocal(newKeyboard);
    }

    /**
     * Enables the slide key hack for language switching via spacebar swipe.
     *
     * @return always true
     */
    @Override
    /*package*/ boolean enableSlideKeyHack() {
        return true;
    }

    /**
     * Handles long press events on keys.
     * Special handling for options key, D-pad center (compose), and phone keyboard '0' (plus).
     *
     * @param key the key that was long-pressed
     * @return true if the long press was handled
     */
    @Override
    protected boolean onLongPress(@NonNull Key key) {
        PointerTracker.clearSlideKeys();

        final int primaryCode = key.codes[0];
        if (primaryCode == KEYCODE_OPTIONS) {
            return invokeOnKey(KEYCODE_OPTIONS_LONGPRESS);
        } else if (primaryCode == KEYCODE_DPAD_CENTER) {
            return invokeOnKey(KEYCODE_COMPOSE);
        } else if (primaryCode == '0' && getKeyboard() == mPhoneKeyboard) {
            // Long pressing on 0 in phone number keypad gives you a '+'.
            return invokeOnKey('+');
        } else {
            return super.onLongPress(key);
        }
    }

    /**
     * Invokes a key press without touch coordinates.
     *
     * @param primaryCode the key code to invoke
     * @return always true
     */
    private boolean invokeOnKey(int primaryCode) {
        getOnKeyboardActionListener().onKey(primaryCode, null,
                LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE,
                LatinKeyboardBaseView.NOT_A_TOUCH_COORDINATE);
        return true;
    }

    /**
     * Checks for sudden jumps in pointer location that could be due to multi-touch
     * being treated as a move by firmware/hardware.
     *
     * <p>Once a sudden jump is detected, all subsequent move events are discarded until an UP
     * is received. When a sudden jump is detected, an UP event is simulated at the last position,
     * and when the sudden moves subside, a DOWN event is simulated for the second key.
     *
     * @param me the motion event to check
     * @return true if the event was consumed (shouldn't continue to be handled by KeyboardView)
     */
    private boolean handleSuddenJump(@NonNull MotionEvent me) {
        final int action = me.getAction();
        final int x = (int) me.getX();
        final int y = (int) me.getY();
        boolean result = false;

        // Real multi-touch event? Stop looking for sudden jumps
        if (me.getPointerCount() > 1) {
            mDisableDisambiguation = true;
        }
        if (mDisableDisambiguation) {
            // If UP, reset the multi-touch flag
            if (action == MotionEvent.ACTION_UP) mDisableDisambiguation = false;
            return false;
        }

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            // Reset the "session"
            mDroppingEvents = false;
            mDisableDisambiguation = false;
            break;
        case MotionEvent.ACTION_MOVE:
            // Check if this is a big jump
            final int deltaX = mLastX - x;
            final int deltaY = mLastY - y;
            final int distanceSquare = deltaX * deltaX + deltaY * deltaY;
            
            // Check the distance and also if the move is not entirely within the bottom row.
            // If it's only in the bottom row, it might be an intentional slide gesture
            // for language switching.
            if (distanceSquare > mJumpThresholdSquare
                    && (mLastY < mLastRowY || y < mLastRowY)) {
                // If we're not yet dropping events, start dropping and send an UP event
                if (!mDroppingEvents) {
                    mDroppingEvents = true;
                    // Send an up event
                    final MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                            MotionEvent.ACTION_UP,
                            mLastX, mLastY, me.getMetaState());
                    super.onTouchEvent(translated);
                    translated.recycle();
                }
                result = true;
            } else if (mDroppingEvents) {
                // If moves are small and we're already dropping events, continue dropping
                result = true;
            }
            break;
        case MotionEvent.ACTION_UP:
            if (mDroppingEvents) {
                // Send a down event first, as we dropped a bunch of sudden jumps and assume that
                // the user is releasing the touch on the second key.
                final MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        MotionEvent.ACTION_DOWN,
                        x, y, me.getMetaState());
                super.onTouchEvent(translated);
                translated.recycle();
                mDroppingEvents = false;
                // Let the up event get processed as well, result = false
            }
            break;
        }
        // Track the previous coordinate
        mLastX = x;
        mLastY = y;
        return result;
    }

    /**
     * Handles touch events for the keyboard view.
     * Includes handling for sudden jumps, extension keyboard, and language switching gestures.
     *
     * @param me the motion event
     * @return true if the event was handled
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent me) {
        final LatinKeyboard keyboard = (LatinKeyboard) getKeyboard();
        if (LatinIME.sKeyboardSettings.showTouchPos || DEBUG_LINE) {
            mLastX = (int) me.getX();
            mLastY = (int) me.getY();
            invalidate();
        }

        // If an extension keyboard is visible or this is an extension keyboard, don't look
        // for sudden jumps. Otherwise, if there was a sudden jump, return without processing the
        // actual motion event.
        if (!mExtensionVisible && !mIsExtensionType
                && handleSuddenJump(me)) return true;
        // Reset any bounding box controls in the keyboard
        if (me.getAction() == MotionEvent.ACTION_DOWN) {
            keyboard.keyReleased();
        }

        if (me.getAction() == MotionEvent.ACTION_UP) {
            final int languageDirection = keyboard.getLanguageChangeDirection();
            if (languageDirection != 0) {
                getOnKeyboardActionListener().onKey(
                        languageDirection == 1 ? KEYCODE_NEXT_LANGUAGE : KEYCODE_PREV_LANGUAGE,
                        new int[0], mLastX, mLastY);
                me.setAction(MotionEvent.ACTION_CANCEL);
                keyboard.keyReleased();
                return super.onTouchEvent(me);
            }
        }

        // If we don't have an extension keyboard, don't go any further.
        if (keyboard.getExtension() == null) {
            return super.onTouchEvent(me);
        }
        // If the motion event is above the keyboard and it's not an UP event coming
        // even before the first MOVE event into the extension area
        if (me.getY() < 0 && (mExtensionVisible || me.getAction() != MotionEvent.ACTION_UP)) {
            if (mExtensionVisible) {
                int action = me.getAction();
                if (mFirstEvent) {
                    action = MotionEvent.ACTION_DOWN;
                }
                mFirstEvent = false;
                final MotionEvent translated = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                        action,
                        me.getX(), me.getY() + mExtension.getHeight(), me.getMetaState());
                if (me.getActionIndex() > 0) {
                    return true;  // Ignore second touches to avoid "pointerIndex out of range"
                }
                final boolean result = mExtension.onTouchEvent(translated);
                translated.recycle();
                if (me.getAction() == MotionEvent.ACTION_UP
                        || me.getAction() == MotionEvent.ACTION_CANCEL) {
                    closeExtension();
                }
                return result;
            } else {
                if (swipeUp()) {
                    return true;
                } else if (openExtension()) {
                    final MotionEvent cancel = MotionEvent.obtain(me.getDownTime(), me.getEventTime(),
                            MotionEvent.ACTION_CANCEL, me.getX() - 100, me.getY() - 100, 0);
                    super.onTouchEvent(cancel);
                    cancel.recycle();
                    if (mExtension.getHeight() > 0) {
                        final MotionEvent translated = MotionEvent.obtain(me.getEventTime(),
                                me.getEventTime(),
                                MotionEvent.ACTION_DOWN,
                                me.getX(), me.getY() + mExtension.getHeight(),
                                me.getMetaState());
                        mExtension.onTouchEvent(translated);
                        translated.recycle();
                    } else {
                        mFirstEvent = true;
                    }
                    // Stop processing multi-touch errors
                    mDisableDisambiguation = true;
                }
                return true;
            }
        } else if (mExtensionVisible) {
            closeExtension();
            // Send a down event into the main keyboard first
            final MotionEvent down = MotionEvent.obtain(me.getEventTime(), me.getEventTime(),
                    MotionEvent.ACTION_DOWN,
                    me.getX(), me.getY(), me.getMetaState());
            super.onTouchEvent(down, true);
            down.recycle();
            // Send the actual event
            return super.onTouchEvent(me);
        } else {
            return super.onTouchEvent(me);
        }
    }

    /**
     * Sets whether this keyboard view is an extension of another keyboard.
     *
     * @param isExtensionType true if this is an extension keyboard
     */
    private void setExtensionType(boolean isExtensionType) {
        mIsExtensionType = isExtensionType;
    }

    /**
     * Opens the extension keyboard popup (5th row).
     *
     * @return true if the extension was opened successfully
     */
    private boolean openExtension() {
        // If the current keyboard is not visible, or if the mini keyboard is active, don't show the popup
        if (!isShown() || popupKeyboardIsShowing()) {
            return false;
        }
        PointerTracker.clearSlideKeys();
        if (((LatinKeyboard) getKeyboard()).getExtension() == null) {
            return false;
        }
        makePopupWindow();
        mExtensionVisible = true;
        return true;
    }

    /**
     * Creates and shows the extension keyboard popup window.
     */
    private void makePopupWindow() {
        dismissPopupKeyboard();
        if (mExtensionPopup == null) {
            final int[] windowLocation = new int[2];
            mExtensionPopup = new PopupWindow(getContext());
            mExtensionPopup.setBackgroundDrawable(null);
            
            final LayoutInflater li = (LayoutInflater) getContext().getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            mExtension = (LatinKeyboardView) li.inflate(mExtensionLayoutResId == 0 ?
                    R.layout.input_trans : mExtensionLayoutResId, null);
            
            final Keyboard keyboard = mExtensionKeyboard;
            mExtension.setKeyboard(keyboard);
            mExtension.setExtensionType(true);
            mExtension.setPadding(0, 0, 0, 0);
            mExtension.setOnKeyboardActionListener(
                    new ExtensionKeyboardListener(getOnKeyboardActionListener()));
            mExtension.setPopupParent(this);
            mExtension.setPopupOffset(0, -windowLocation[1]);
            
            mExtensionPopup.setContentView(mExtension);
            mExtensionPopup.setWidth(getWidth());
            mExtensionPopup.setHeight(keyboard.getHeight());
            mExtensionPopup.setAnimationStyle(-1);
            
            getLocationInWindow(windowLocation);
            // Adjust popup position with empirically determined offset for proper alignment
            final int POPUP_VERTICAL_ADJUSTMENT = 30; // px, accounts for status bar and padding
            mExtension.setPopupOffset(0, -windowLocation[1] - POPUP_VERTICAL_ADJUSTMENT);
            mExtensionPopup.showAtLocation(this, 0, 0, -keyboard.getHeight()
                    + windowLocation[1] + this.getPaddingTop());
        } else {
            mExtension.setVisibility(VISIBLE);
        }
        mExtension.setShiftState(getShiftState()); // Propagate shift state
    }

    /**
     * Performs cleanup when the keyboard is closing.
     * Dismisses the extension popup if visible.
     */
    @Override
    public void closing() {
        super.closing();
        if (mExtensionPopup != null && mExtensionPopup.isShowing()) {
            mExtensionPopup.dismiss();
            mExtensionPopup = null;
        }
    }

    /**
     * Closes the extension keyboard popup without dismissing it completely.
     * Hides the extension view and marks it as not visible.
     */
    private void closeExtension() {
        mExtension.closing();
        mExtension.setVisibility(INVISIBLE);
        mExtensionVisible = false;
    }

    /**
     * Listener for the extension keyboard that forwards key events to the target listener
     * but blocks swipe gestures.
     */
    private static class ExtensionKeyboardListener implements OnKeyboardActionListener {
        @NonNull
        private final OnKeyboardActionListener mTarget;
        
        /**
         * Creates a new extension keyboard listener.
         *
         * @param target the target listener to forward events to
         */
        ExtensionKeyboardListener(@NonNull OnKeyboardActionListener target) {
            mTarget = target;
        }
        
        @Override
        public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
            mTarget.onKey(primaryCode, keyCodes, x, y);
        }
        
        @Override
        public void onPress(int primaryCode) {
            mTarget.onPress(primaryCode);
        }
        
        @Override
        public void onRelease(int primaryCode) {
            mTarget.onRelease(primaryCode);
        }
        
        @Override
        public void onText(CharSequence text) {
            mTarget.onText(text);
        }
        
        @Override
        public void onCancel() {
            mTarget.onCancel();
        }
        
        @Override
        public boolean swipeDown() {
            // Don't pass through
            return true;
        }
        
        @Override
        public boolean swipeLeft() {
            // Don't pass through
            return true;
        }
        
        @Override
        public boolean swipeRight() {
            // Don't pass through
            return true;
        }
        
        @Override
        public boolean swipeUp() {
            // Don't pass through
            return true;
        }
    }

    /****************************  INSTRUMENTATION  *******************************/

    /** Debug flag for auto-play mode (testing) */
    static final boolean DEBUG_AUTO_PLAY = false;
    
    /** Debug flag for showing touch position crosshairs */
    static final boolean DEBUG_LINE = false;
    
    private static final int MSG_TOUCH_DOWN = 1;
    private static final int MSG_TOUCH_UP = 2;

    @Nullable
    Handler mHandler2;

    @Nullable
    private String mStringToPlay;
    private int mStringIndex;
    private boolean mDownDelivered;
    
    @NonNull
    private final Key[] mAsciiKeys = new Key[256];
    private boolean mPlaying;
    private int mLastX;
    private int mLastY;
    
    @Nullable
    private Paint mPaint;

    /**
     * Sets up the keyboard for local use, initializing auto-play if enabled.
     *
     * @param k the keyboard to set up
     */
    private void setKeyboardLocal(@NonNull Keyboard k) {
        if (DEBUG_AUTO_PLAY) {
            findKeys();
            if (mHandler2 == null) {
                mHandler2 = new Handler() {
                    @Override
                    public void handleMessage(@NonNull Message msg) {
                        removeMessages(MSG_TOUCH_DOWN);
                        removeMessages(MSG_TOUCH_UP);
                        if (!mPlaying) {
                            return;
                        }

                        switch (msg.what) {
                            case MSG_TOUCH_DOWN:
                                if (mStringIndex >= mStringToPlay.length()) {
                                    mPlaying = false;
                                    return;
                                }
                                char c = mStringToPlay.charAt(mStringIndex);
                                while (c > 255 || mAsciiKeys[c] == null) {
                                    mStringIndex++;
                                    if (mStringIndex >= mStringToPlay.length()) {
                                        mPlaying = false;
                                        return;
                                    }
                                    c = mStringToPlay.charAt(mStringIndex);
                                }
                                final int x = mAsciiKeys[c].x + 10;
                                final int y = mAsciiKeys[c].y + 26;
                                final MotionEvent me = MotionEvent.obtain(SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_DOWN, x, y, 0);
                                LatinKeyboardView.this.dispatchTouchEvent(me);
                                me.recycle();
                                sendEmptyMessageDelayed(MSG_TOUCH_UP, 500); // Deliver up in 500ms
                                mDownDelivered = true;
                                break;
                                
                            case MSG_TOUCH_UP:
                                final char cUp = mStringToPlay.charAt(mStringIndex);
                                final int x2 = mAsciiKeys[cUp].x + 10;
                                final int y2 = mAsciiKeys[cUp].y + 26;
                                mStringIndex++;

                                final MotionEvent me2 = MotionEvent.obtain(SystemClock.uptimeMillis(),
                                        SystemClock.uptimeMillis(),
                                        MotionEvent.ACTION_UP, x2, y2, 0);
                                LatinKeyboardView.this.dispatchTouchEvent(me2);
                                me2.recycle();
                                sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 500); // Deliver down in 500ms
                                mDownDelivered = false;
                                break;
                        }
                    }
                };

            }
        }
    }

    /**
     * Finds and caches ASCII keys (0-255) from the current keyboard.
     * Used for auto-play mode.
     */
    private void findKeys() {
        final List<Key> keys = getKeyboard().getKeys();
        // Get the keys on this keyboard
        for (int i = 0; i < keys.size(); i++) {
            final int code = keys.get(i).codes[0];
            if (code >= 0 && code <= 255) {
                mAsciiKeys[code] = keys.get(i);
            }
        }
    }

    /**
     * Starts auto-play mode for testing (DEBUG_AUTO_PLAY must be true).
     * Simulates typing the given string.
     *
     * @param s the string to auto-play
     */
    public void startPlaying(@Nullable String s) {
        if (DEBUG_AUTO_PLAY) {
            if (s == null) {
                return;
            }
            mStringToPlay = s.toLowerCase();
            mPlaying = true;
            mDownDelivered = false;
            mStringIndex = 0;
            mHandler2.sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 10);
        }
    }

    /**
     * Draws the keyboard view.
     * Includes GC retry logic for low memory situations and debug overlays.
     *
     * @param c the canvas to draw on
     */
    @Override
    public void draw(@NonNull Canvas c) {
        LatinIMEUtil.GCUtils.getInstance().reset();
        boolean tryGC = true;
        for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
            try {
                super.draw(c);
                tryGC = false;
            } catch (OutOfMemoryError e) {
                tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait("LatinKeyboardView", e);
            }
        }
        
        // Auto-play mode (testing)
        if (DEBUG_AUTO_PLAY) {
            if (mPlaying) {
                mHandler2.removeMessages(MSG_TOUCH_DOWN);
                mHandler2.removeMessages(MSG_TOUCH_UP);
                if (mDownDelivered) {
                    mHandler2.sendEmptyMessageDelayed(MSG_TOUCH_UP, 20);
                } else {
                    mHandler2.sendEmptyMessageDelayed(MSG_TOUCH_DOWN, 20);
                }
            }
        }
        
        // Draw touch position crosshairs (debug)
        if (LatinIME.sKeyboardSettings.showTouchPos || DEBUG_LINE) {
            if (mPaint == null) {
                mPaint = new Paint();
                mPaint.setColor(0x80FFFFFF);
                mPaint.setAntiAlias(false);
            }
            c.drawLine(mLastX, 0, mLastX, getHeight(), mPaint);
            c.drawLine(0, mLastY, getWidth(), mLastY, mPaint);
        }
    }
}
