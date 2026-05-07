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
import java.util.Arrays;

import static org.dark.keyboard.Keyboard.Key;

/**
 * Key detector that uses proximity correction to determine which key was pressed.
 * <p>
 * Uses distance-based algorithm to find nearby keys and returns the closest one.
 * </p>
 */
class ProximityKeyDetector extends KeyDetector {

    private static final int MAX_NEARBY_KEYS = 12;

    /** Working array for storing distances to nearby keys */
    @NonNull
    private final int[] mDistances = new int[MAX_NEARBY_KEYS];

    @Override
    protected int getMaxNearbyKeys() {
        return MAX_NEARBY_KEYS;
    }

    @Override
    @NonNull
    public int getKeyIndexAndNearbyCodes(int x, int y, @Nullable int[] allKeys) {
        final Key[] keys = getKeys();
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);
        int primaryIndex = LatinKeyboardBaseView.NOT_A_KEY;
        int closestKey = LatinKeyboardBaseView.NOT_A_KEY;
        int closestKeyDist = mProximityThresholdSquare + 1;
        int[] distances = mDistances;
        Arrays.fill(distances, Integer.MAX_VALUE);
        
        int[] nearestKeyIndices = mKeyboard != null ? mKeyboard.getNearestKeys(touchX, touchY) : new int[0];
        final int keyCount = nearestKeyIndices.length;
        
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(touchX, touchY);
            
            if (isInside) {
                primaryIndex = nearestKeyIndices[i];
            }

            final int keyCode = key.codes != null && key.codes.length > 0 ? key.codes[0] : 0;
            
            if (((mProximityCorrectOn
                    && (dist = key.squaredDistanceFrom(touchX, touchY)) < mProximityThresholdSquare)
                    || isInside)
                    && keyCode > 32) {
                // Find insertion point
                final int nCodes = key.codes != null ? key.codes.length : 0;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }

                if (allKeys == null) continue;

                for (int j = 0; j < distances.length; j++) {
                    if (distances[j] > dist) {
                        // Make space for nCodes codes
                        System.arraycopy(distances, j, distances, j + nCodes,
                                distances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                allKeys.length - j - nCodes);
                        if (key.codes != null) {
                            System.arraycopy(key.codes, 0, allKeys, j, nCodes);
                        }
                        Arrays.fill(distances, j, j + nCodes, dist);
                        break;
                    }
                }
            }
        }
        
        if (primaryIndex == LatinKeyboardBaseView.NOT_A_KEY) {
            primaryIndex = closestKey;
        }
        
        return primaryIndex;
    }
}
