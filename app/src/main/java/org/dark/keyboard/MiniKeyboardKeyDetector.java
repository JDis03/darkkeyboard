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
import org.dark.keyboard.Keyboard.Key;

/**
 * Key detector optimized for mini keyboard popups.
 * <p>
 * Uses squared distance calculations for performance, with special handling
 * for slide gestures from the top edge of the keyboard.
 * </p>
 */
class MiniKeyboardKeyDetector extends KeyDetector {
    private static final int MAX_NEARBY_KEYS = 1;

    private final int mSlideAllowanceSquare;
    private final int mSlideAllowanceSquareTop;

    /**
     * Creates a new MiniKeyboardKeyDetector with specified slide allowance.
     *
     * @param slideAllowance Maximum distance in pixels for slide gesture detection
     */
    public MiniKeyboardKeyDetector(float slideAllowance) {
        super();
        mSlideAllowanceSquare = (int)(slideAllowance * slideAllowance);
        // Top slide allowance is slightly longer (sqrt(2) times) than other edges.
        mSlideAllowanceSquareTop = mSlideAllowanceSquare * 2;
    }

    @Override
    @NonNull
    protected int getMaxNearbyKeys() {
        return MAX_NEARBY_KEYS;
    }

    @Override
    public int getKeyIndexAndNearbyCodes(int x, int y, @Nullable int[] allKeys) {
        final Key[] keys = getKeys();
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);
        int closestKeyIndex = LatinKeyboardBaseView.NOT_A_KEY;
        int closestKeyDist = (y < 0) ? mSlideAllowanceSquareTop : mSlideAllowanceSquare;
        final int keyCount = keys.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[i];
            int dist = key.squaredDistanceFrom(touchX, touchY);
            if (dist < closestKeyDist) {
                closestKeyIndex = i;
                closestKeyDist = dist;
            }
        }
        if (allKeys != null && closestKeyIndex != LatinKeyboardBaseView.NOT_A_KEY) {
            allKeys[0] = keys[closestKeyIndex].getPrimaryCode();
        }
        return closestKeyIndex;
    }
}
