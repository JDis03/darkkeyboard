/*
 * Copyright (C) 2010 Google Inc.
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
import java.util.ArrayList;
import java.util.List;
import org.dark.keyboard.LatinKeyboardBaseView.OnKeyboardActionListener;
import org.dark.keyboard.LatinKeyboardBaseView.UIHandler;
import android.content.res.Resources;
import org.dark.keyboard.Keyboard.Key;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Tracks pointer movements and gestures on the keyboard.
 * <p>
 * Handles touch events, key detection, multi-tap, sliding input, and key repeat.
 * Each PointerTracker instance manages a single pointer (finger) on the keyboard.
 * </p>
 */
public class PointerTracker {
    private static final String TAG = "PointerTracker";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_MOVE = false;

    /**
     * Interface for UI callbacks from the pointer tracker.
     */
    public interface UIProxy {
        void invalidateKey(Key key);
        void showPreview(int keyIndex, PointerTracker tracker);
        boolean hasDistinctMultitouch();
    }

    public final int mPointerId;

    // Timing constants
    private final int mDelayBeforeKeyRepeatStart;
    private final int mMultiTapKeyTimeout;

    // Miscellaneous constants
    private static final int NOT_A_KEY = LatinKeyboardBaseView.NOT_A_KEY;
    private static final int[] KEY_DELETE = { Keyboard.KEYCODE_DELETE };

    private final UIProxy mProxy;
    private final UIHandler mHandler;
    private final KeyDetector mKeyDetector;
    private OnKeyboardActionListener mListener;
    private final KeyboardSwitcher mKeyboardSwitcher;
    private final boolean mHasDistinctMultitouch;

    private Key[] mKeys;
    private int mKeyHysteresisDistanceSquared = -1;

    private final KeyState mKeyState;

    // true if keyboard layout has been changed.
    private boolean mKeyboardLayoutHasBeenChanged;

    // true if event is already translated to a key action (long press or mini-keyboard)
    private boolean mKeyAlreadyProcessed;

    // true if this pointer is repeatable key
    private boolean mIsRepeatableKey;

    // true if this pointer is in sliding key input
    private boolean mIsInSlidingKeyInput;

    // For multi-tap
    private int mLastSentIndex;
    private int mTapCount;
    private long mLastTapTime;
    private boolean mInMultiTap;
    private final StringBuilder mPreviewLabel = new StringBuilder(1);

    // pressed key
    private int mPreviousKey = NOT_A_KEY;

    private static boolean sSlideKeyHack;
    private static final List<Key> sSlideKeys = new ArrayList<>(10);

    /**
     * Tracks the state of a single key pointer.
     */
    private static class KeyState {
        private final KeyDetector mKeyDetector;

        // The position and time at which first down event occurred.
        private int mStartX;
        private int mStartY;
        private long mDownTime;

        // The current key index where this pointer is.
        private int mKeyIndex = NOT_A_KEY;
        // The position where mKeyIndex was recognized for the first time.
        private int mKeyX;
        private int mKeyY;

        // Last pointer position.
        private int mLastX;
        private int mLastY;

        public KeyState(KeyDetector keyDetector) {
            mKeyDetector = keyDetector;
        }

        public int getKeyIndex() {
            return mKeyIndex;
        }

        public int getKeyX() {
            return mKeyX;
        }

        public int getKeyY() {
            return mKeyY;
        }

        public int getStartX() {
            return mStartX;
        }

        public int getStartY() {
            return mStartY;
        }

        public long getDownTime() {
            return mDownTime;
        }

        public int getLastX() {
            return mLastX;
        }

        public int getLastY() {
            return mLastY;
        }

        public int onDownKey(int x, int y, long eventTime) {
            mStartX = x;
            mStartY = y;
            mDownTime = eventTime;
            return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
        }

        private int onMoveKeyInternal(int x, int y) {
            mLastX = x;
            mLastY = y;
            return mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
        }

        public int onMoveKey(int x, int y) {
            return onMoveKeyInternal(x, y);
        }

        public int onMoveToNewKey(int keyIndex, int x, int y) {
            mKeyIndex = keyIndex;
            mKeyX = x;
            mKeyY = y;
            return keyIndex;
        }

        public int onUpKey(int x, int y) {
            return onMoveKeyInternal(x, y);
        }
    }

    /**
     * Creates a new PointerTracker.
     *
     * @param id The pointer ID
     * @param handler UI handler for timers and callbacks
     * @param keyDetector Key detection algorithm
     * @param proxy UI proxy for invalidation and preview
     * @param res Resources for timing constants
     * @param slideKeyHack Whether sliding key input is enabled
     * @throws NullPointerException if handler, keyDetector, or proxy is null
     */
    public PointerTracker(int id, UIHandler handler, KeyDetector keyDetector, UIProxy proxy,
            Resources res, boolean slideKeyHack) {
        if (proxy == null || handler == null || keyDetector == null) {
            throw new NullPointerException("proxy, handler, and keyDetector must not be null");
        }
        mPointerId = id;
        mProxy = proxy;
        mHandler = handler;
        mKeyDetector = keyDetector;
        mKeyboardSwitcher = KeyboardSwitcher.getInstance();
        mKeyState = new KeyState(keyDetector);
        mHasDistinctMultitouch = proxy.hasDistinctMultitouch();
        mDelayBeforeKeyRepeatStart = res.getInteger(R.integer.config_delay_before_key_repeat_start);
        mMultiTapKeyTimeout = res.getInteger(R.integer.config_multi_tap_key_timeout);
        sSlideKeyHack = slideKeyHack;
        resetMultiTap();
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mListener = listener;
    }

    /**
     * Sets the keyboard layout and hysteresis distance.
     *
     * @param keys Array of keys in the current keyboard layout
     * @param keyHysteresisDistance Minimum distance in pixels to consider a key change
     * @throws IllegalArgumentException if keys is null or hysteresis is negative
     */
    public void setKeyboard(Key[] keys, float keyHysteresisDistance) {
        if (keys == null || keyHysteresisDistance < 0) {
            throw new IllegalArgumentException("keys must not be null and hysteresis must be >= 0");
        }
        mKeys = keys;
        mKeyHysteresisDistanceSquared = (int)(keyHysteresisDistance * keyHysteresisDistance);
        mKeyboardLayoutHasBeenChanged = true;
    }

    public boolean isInSlidingKeyInput() {
        return mIsInSlidingKeyInput;
    }

    public void setSlidingKeyInputState(boolean state) {
        mIsInSlidingKeyInput = state;
    }

    private boolean isValidKeyIndex(int keyIndex) {
        return keyIndex >= 0 && keyIndex < mKeys.length;
    }

    @Nullable
    public Key getKey(int keyIndex) {
        return isValidKeyIndex(keyIndex) ? mKeys[keyIndex] : null;
    }

    private boolean isModifierInternal(int keyIndex) {
        Key key = getKey(keyIndex);
        if (key == null || key.codes == null) {
            return false;
        }
        int primaryCode = key.codes[0];
        return primaryCode == Keyboard.KEYCODE_SHIFT
                || primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                || primaryCode == LatinKeyboardView.KEYCODE_CTRL_LEFT
                || primaryCode == LatinKeyboardView.KEYCODE_ALT_LEFT
                || primaryCode == LatinKeyboardView.KEYCODE_META_LEFT
                || primaryCode == LatinKeyboardView.KEYCODE_FN;
    }

    public boolean isModifier() {
        return isModifierInternal(mKeyState.getKeyIndex());
    }

    public boolean isOnModifierKey(int x, int y) {
        return isModifierInternal(mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null));
    }

    public boolean isSpaceKey(int keyIndex) {
        Key key = getKey(keyIndex);
        return key != null && key.codes != null && key.codes[0] == LatinIME.ASCII_SPACE;
    }

    /**
     * Updates the visual state of keys when the pointer moves.
     *
     * @param keyIndex The new key index under the pointer
     */
    public void updateKey(int keyIndex) {
        if (mKeyAlreadyProcessed) {
            return;
        }
        int oldKeyIndex = mPreviousKey;
        mPreviousKey = keyIndex;
        if (keyIndex != oldKeyIndex) {
            if (isValidKeyIndex(oldKeyIndex)) {
                final boolean inside = (keyIndex == NOT_A_KEY);
                mKeys[oldKeyIndex].onReleased(inside);
                mProxy.invalidateKey(mKeys[oldKeyIndex]);
            }
            if (isValidKeyIndex(keyIndex)) {
                mKeys[keyIndex].onPressed();
                mProxy.invalidateKey(mKeys[keyIndex]);
            }
        }
    }

    public void setAlreadyProcessed() {
        mKeyAlreadyProcessed = true;
    }

    /**
     * Processes a touch event.
     *
     * @param action MotionEvent action (ACTION_DOWN, ACTION_MOVE, etc.)
     * @param x X coordinate of the touch
     * @param y Y coordinate of the touch
     * @param eventTime Time of the event
     */
    public void onTouchEvent(int action, int x, int y, long eventTime) {
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                onMoveEvent(x, y, eventTime);
                break;
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onDownEvent(x, y, eventTime);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                onUpEvent(x, y, eventTime);
                break;
            case MotionEvent.ACTION_CANCEL:
                onCancelEvent(x, y, eventTime);
                break;
        }
    }

    /**
     * Handles ACTION_DOWN events.
     */
    public void onDownEvent(int x, int y, long eventTime) {
        if (DEBUG) {
            debugLog("onDownEvent:", x, y);
        }
        int keyIndex = mKeyState.onDownKey(x, y, eventTime);
        mKeyboardLayoutHasBeenChanged = false;
        mKeyAlreadyProcessed = false;
        mIsRepeatableKey = false;
        mIsInSlidingKeyInput = false;
        checkMultiTap(eventTime, keyIndex);
        if (mListener != null && isValidKeyIndex(keyIndex)) {
            Key key = mKeys[keyIndex];
            if (key.codes != null) {
                mListener.onPress(key.getPrimaryCode());
            }
            if (mKeyboardLayoutHasBeenChanged) {
                mKeyboardLayoutHasBeenChanged = false;
                keyIndex = mKeyState.onDownKey(x, y, eventTime);
            }
        }
        if (isValidKeyIndex(keyIndex) && mKeys[keyIndex].repeatable) {
            repeatKey(keyIndex);
            mHandler.startKeyRepeatTimer(mDelayBeforeKeyRepeatStart, keyIndex, this);
            mIsRepeatableKey = true;
        }
        startLongPressTimer(keyIndex);
        showKeyPreviewAndUpdateKey(keyIndex);
    }

    private static void addSlideKey(Key key) {
        if (!sSlideKeyHack || LatinIME.sKeyboardSettings.sendSlideKeys == 0) {
            return;
        }
        if (key == null) {
            return;
        }
        if (key.modifier) {
            clearSlideKeys();
        } else {
            sSlideKeys.add(key);
        }
    }
    
    /*package*/ static void clearSlideKeys() {
        sSlideKeys.clear();
    }
    
    void sendSlideKeys() {
        if (!sSlideKeyHack) {
            return;
        }
        int slideMode = LatinIME.sKeyboardSettings.sendSlideKeys;
        if ((slideMode & 4) > 0) {
            for (Key key : sSlideKeys) {
                detectAndSendKey(key, key.x, key.y, -1);            
            }
        } else {
            int n = sSlideKeys.size();
            if (n > 0 && (slideMode & 1) > 0) {
                Key key = sSlideKeys.get(0);
                detectAndSendKey(key, key.x, key.y, -1);            
            }
            if (n > 1 && (slideMode & 2) > 0) {
                Key key = sSlideKeys.get(n - 1);
                detectAndSendKey(key, key.x, key.y, -1);            
            }
        }
        clearSlideKeys();
    }
    
    /**
     * Handles ACTION_MOVE events.
     */
    public void onMoveEvent(int x, int y, long eventTime) {
        if (DEBUG_MOVE) {
            debugLog("onMoveEvent:", x, y);
        }
        if (mKeyAlreadyProcessed) {
            return;
        }
        final KeyState keyState = mKeyState;
        int keyIndex = keyState.onMoveKey(x, y);
        final Key oldKey = getKey(keyState.getKeyIndex());
        if (isValidKeyIndex(keyIndex)) {
            boolean isMinorMoveBounce = isMinorMoveBounce(x, y, keyIndex);
            if (DEBUG_MOVE) {
                Log.i(TAG, "isMinorMoveBounce=" + isMinorMoveBounce + " oldKey=" + (oldKey == null ? "null" : oldKey));
            }
            if (oldKey == null) {
                if (mListener != null) {
                    Key key = getKey(keyIndex);
                    if (key.codes != null) {
                        mListener.onPress(key.getPrimaryCode());
                    }
                    if (mKeyboardLayoutHasBeenChanged) {
                        mKeyboardLayoutHasBeenChanged = false;
                        keyIndex = keyState.onMoveKey(x, y);
                    }
                }
                keyState.onMoveToNewKey(keyIndex, x, y);
                startLongPressTimer(keyIndex);
            } else if (!isMinorMoveBounce) {
                mIsInSlidingKeyInput = true;
                if (mListener != null && oldKey.codes != null) {
                    mListener.onRelease(oldKey.getPrimaryCode());
                }
                resetMultiTap();
                if (mListener != null) {
                    Key key = getKey(keyIndex);
                    if (key.codes != null) {
                        mListener.onPress(key.getPrimaryCode());
                    }
                    if (mKeyboardLayoutHasBeenChanged) {
                        mKeyboardLayoutHasBeenChanged = false;
                        keyIndex = keyState.onMoveKey(x, y);
                    }
                    addSlideKey(oldKey);
                }
                keyState.onMoveToNewKey(keyIndex, x, y);
                startLongPressTimer(keyIndex);
            }
        } else {
            if (oldKey != null && !isMinorMoveBounce(x, y, keyIndex)) {
                mIsInSlidingKeyInput = true;
                if (mListener != null && oldKey.codes != null) {
                    mListener.onRelease(oldKey.getPrimaryCode());
                }
                resetMultiTap();
                keyState.onMoveToNewKey(keyIndex, x, y);
                mHandler.cancelLongPressTimer();
            }
        }
        showKeyPreviewAndUpdateKey(keyState.getKeyIndex());
    }

    /**
     * Handles ACTION_UP events.
     */
    public void onUpEvent(int x, int y, long eventTime) {
        if (DEBUG) {
            debugLog("onUpEvent  :", x, y);
        }
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        showKeyPreviewAndUpdateKey(NOT_A_KEY);
        mIsInSlidingKeyInput = false;
        sendSlideKeys();
        if (mKeyAlreadyProcessed) {
            return;
        }
        int keyIndex = mKeyState.onUpKey(x, y);
        if (isMinorMoveBounce(x, y, keyIndex)) {
            keyIndex = mKeyState.getKeyIndex();
            x = mKeyState.getKeyX();
            y = mKeyState.getKeyY();
        }
        if (!mIsRepeatableKey) {
            detectAndSendKey(keyIndex, x, y, eventTime);
        }
        if (isValidKeyIndex(keyIndex)) {
            mProxy.invalidateKey(mKeys[keyIndex]);
        }
    }

    /**
     * Handles ACTION_CANCEL events.
     */
    public void onCancelEvent(int x, int y, long eventTime) {
        if (DEBUG) {
            debugLog("onCancelEvt:", x, y);
        }
        mHandler.cancelKeyTimers();
        mHandler.cancelPopupPreview();
        showKeyPreviewAndUpdateKey(NOT_A_KEY);
        mIsInSlidingKeyInput = false;
        int keyIndex = mKeyState.getKeyIndex();
        if (isValidKeyIndex(keyIndex)) {
            mProxy.invalidateKey(mKeys[keyIndex]);
        }
    }

    /**
     * Repeats the key press for repeatable keys.
     */
    public void repeatKey(int keyIndex) {
        Key key = getKey(keyIndex);
        if (key != null) {
            detectAndSendKey(keyIndex, key.x, key.y, -1);
        }
    }

    public int getLastX() {
        return mKeyState.getLastX();
    }

    public int getLastY() {
        return mKeyState.getLastY();
    }

    public long getDownTime() {
        return mKeyState.getDownTime();
    }

    /* package */ int getStartX() {
        return mKeyState.getStartX();
    }

    /* package */ int getStartY() {
        return mKeyState.getStartY();
    }

    private boolean isMinorMoveBounce(int x, int y, int newKey) {
        if (mKeys == null || mKeyHysteresisDistanceSquared < 0) {
            throw new IllegalStateException("keyboard and/or hysteresis not set");
        }
        int curKey = mKeyState.getKeyIndex();
        if (newKey == curKey) {
            return true;
        } else if (isValidKeyIndex(curKey)) {
            return getSquareDistanceToKeyEdge(x, y, mKeys[curKey]) < mKeyHysteresisDistanceSquared;
        } else {
            return false;
        }
    }

    private static int getSquareDistanceToKeyEdge(int x, int y, Key key) {
        final int left = key.x;
        final int right = key.x + key.width;
        final int top = key.y;
        final int bottom = key.y + key.height;
        final int edgeX = x < left ? left : (x > right ? right : x);
        final int edgeY = y < top ? top : (y > bottom ? bottom : y);
        final int dx = x - edgeX;
        final int dy = y - edgeY;
        return dx * dx + dy * dy;
    }

    private void showKeyPreviewAndUpdateKey(int keyIndex) {
        updateKey(keyIndex);
        if (mHasDistinctMultitouch && isModifier()) {
            mProxy.showPreview(NOT_A_KEY, this);
        } else {
            mProxy.showPreview(keyIndex, this);
        }
    }

    private void startLongPressTimer(int keyIndex) {
        if (mKeyboardSwitcher.isInMomentaryAutoModeSwitchState()) {
            mHandler.startLongPressTimer(LatinIME.sKeyboardSettings.longpressTimeout * 3, keyIndex, this);
        } else {
            mHandler.startLongPressTimer(LatinIME.sKeyboardSettings.longpressTimeout, keyIndex, this);
        }
    }

    private void detectAndSendKey(int index, int x, int y, long eventTime) {
        detectAndSendKey(getKey(index), x, y, eventTime);
        mLastSentIndex = index;
    }
    
    private void detectAndSendKey(Key key, int x, int y, long eventTime) {
        final OnKeyboardActionListener listener = mListener;

        if (key == null) {
            if (listener != null) {
                listener.onCancel();
            }
        } else {
            if (key.text != null) {
                if (listener != null) {
                    listener.onText(key.text);
                    listener.onRelease(0);
                }
            } else {
                if (key.codes == null) {
                    return;
                }
                int code = key.getPrimaryCode();
                int[] codes = mKeyDetector.newCodeArray();
                mKeyDetector.getKeyIndexAndNearbyCodes(x, y, codes);
                if (mInMultiTap) {
                    if (mTapCount != -1) {
                        mListener.onKey(Keyboard.KEYCODE_DELETE, KEY_DELETE, x, y);
                    } else {
                        mTapCount = 0;
                    }
                    code = key.codes[mTapCount];
                }
                if (codes.length >= 2 && codes[0] != code && codes[1] == code) {
                    codes[1] = codes[0];
                    codes[0] = code;
                }
                if (listener != null) {
                    listener.onKey(code, codes, x, y);
                    listener.onRelease(code);
                }
            }
            mLastTapTime = eventTime;
        }
    }

    /**
     * Returns the preview text for a key, handling multi-tap and dead keys.
     *
     * @param key The key to get preview text for
     * @return The preview text
     */
    public CharSequence getPreviewText(Key key) {
        if (mInMultiTap) {
            mPreviewLabel.setLength(0);
            mPreviewLabel.append((char) key.codes[mTapCount < 0 ? 0 : mTapCount]);
            return mPreviewLabel;
        } else {
            if (key.isDeadKey()) {
                return DeadAccentSequence.normalize(" " + key.label);
            } else {
                return key.label;
            }
        }
    }

    private void resetMultiTap() {
        mLastSentIndex = NOT_A_KEY;
        mTapCount = 0;
        mLastTapTime = -1;
        mInMultiTap = false;
    }

    private void checkMultiTap(long eventTime, int keyIndex) {
        Key key = getKey(keyIndex);
        if (key == null || key.codes == null) {
            return;
        }

        final boolean isMultiTap = (eventTime < mLastTapTime + mMultiTapKeyTimeout && keyIndex == mLastSentIndex);
        if (key.codes.length > 1) {
            mInMultiTap = true;
            if (isMultiTap) {
                mTapCount = (mTapCount + 1) % key.codes.length;
                return;
            } else {
                mTapCount = -1;
                return;
            }
        }
        if (!isMultiTap) {
            resetMultiTap();
        }
    }

    private void debugLog(String title, int x, int y) {
        int keyIndex = mKeyDetector.getKeyIndexAndNearbyCodes(x, y, null);
        Key key = getKey(keyIndex);
        final String code;
        if (key == null || key.codes == null) {
            code = "----";
        } else {
            int primaryCode = key.codes[0];
            code = String.format((primaryCode < 0) ? "%4d" : "0x%02x", primaryCode);
        }
        Log.d(TAG, String.format("%s%s[%d] %3d,%3d %3d(%s) %s", title,
                (mKeyAlreadyProcessed ? "-" : " "), mPointerId, x, y, keyIndex, code,
                (isModifier() ? "modifier" : "")));
    }
}
