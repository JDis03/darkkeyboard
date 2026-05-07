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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

import static org.dark.keyboard.Keyboard.Key;

/**
 * Base class for detecting which key was pressed on a keyboard.
 * <p>
 * Handles coordinate correction, proximity correction, and key lookup.
 * </p>
 */
abstract class KeyDetector {

    @Nullable
    protected Keyboard mKeyboard;

    @Nullable
    private Key[] mKeys;

    protected int mCorrectionX;

    protected int mCorrectionY;

    protected boolean mProximityCorrectOn;

    protected int mProximityThresholdSquare;

    /**
     * Sets the keyboard to detect keys on.
     *
     * @param keyboard The keyboard layout
     * @param correctionX X-coordinate correction offset
     * @param correctionY Y-coordinate correction offset
     * @return Array of all keys in the keyboard
     * @throws NullPointerException if keyboard is null
     */
    @NonNull
    public Key[] setKeyboard(@Nullable Keyboard keyboard, float correctionX, float correctionY) {
        Log.i("KeyDetector", "KeyDetector correctionX=" + correctionX + " correctionY=" + correctionY);
        if (keyboard == null) {
            throw new NullPointerException("Keyboard cannot be null");
        }
        mCorrectionX = (int) correctionX;
        mCorrectionY = (int) correctionY;
        mKeyboard = keyboard;
        List<Key> keys = mKeyboard.getKeys();
        Key[] array = keys.toArray(new Key[0]);
        mKeys = array;
        return array;
    }

    /**
     * Applies X-coordinate correction to a touch point.
     *
     * @param x The raw x-coordinate
     * @return The corrected x-coordinate
     */
    protected int getTouchX(int x) {
        return x + mCorrectionX;
    }

    /**
     * Applies Y-coordinate correction to a touch point.
     *
     * @param y The raw y-coordinate
     * @return The corrected y-coordinate
     */
    protected int getTouchY(int y) {
        return y + mCorrectionY;
    }

    /**
     * Gets all keys in the keyboard.
     *
     * @return Array of all keys
     * @throws IllegalStateException if keyboard hasn't been set
     */
    @NonNull
    protected Key[] getKeys() {
        if (mKeys == null) {
            throw new IllegalStateException("Keyboard isn't set. Call setKeyboard() first.");
        }
        return mKeys;
    }

    /**
     * Enables or disables proximity correction.
     *
     * @param enabled true to enable proximity correction
     */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectOn = enabled;
    }

    /**
     * Checks if proximity correction is enabled.
     *
     * @return true if proximity correction is enabled
     */
    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectOn;
    }

    /**
     * Sets the proximity threshold for key detection.
     *
     * @param threshold The threshold in pixels (will be squared internally)
     */
    public void setProximityThreshold(int threshold) {
        mProximityThresholdSquare = threshold * threshold;
    }

    /**
     * Allocates an array that can hold all key indices returned by
     * {@link #getKeyIndexAndNearbyCodes(int, int, int[])}.
     * <p>
     * The maximum size is computed by {@link #getMaxNearbyKeys()}.
     * </p>
     *
     * @return Array initialized with {@link LatinKeyboardBaseView#NOT_A_KEY} values
     */
    @NonNull
    public int[] newCodeArray() {
        int[] codes = new int[getMaxNearbyKeys()];
        Arrays.fill(codes, LatinKeyboardBaseView.NOT_A_KEY);
        return codes;
    }

    /**
     * Computes the maximum size of the array needed for nearby key indices.
     *
     * @return Maximum number of nearby keys that can be returned
     */
    abstract protected int getMaxNearbyKeys();

    /**
     * Finds all possible nearby key indices around a touch point and returns
     * the nearest key index.
     * <p>
     * The algorithm depends on:
     * </p>
     * <ul>
     *     <li>Threshold set by {@link #setProximityThreshold(int)}</li>
     *     <li>Mode set by {@link #setProximityCorrectionEnabled(boolean)}</li>
     * </ul>
     *
     * @param x The x-coordinate of the touch point
     * @param y The y-coordinate of the touch point
     * @param allKeys Array to store all nearby key indices
     * @return The nearest key index
     */
    @NonNull
    abstract public int getKeyIndexAndNearbyCodes(int x, int y, @NonNull int[] allKeys);
}
