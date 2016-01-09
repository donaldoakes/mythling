/**
 * Copyright 2015 Donald Oakes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oakesville.mythling.media;

import java.io.Serializable;

public class Cut implements Serializable {

    public int start; // seconds
    public int end; // seconds

    public Cut(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public boolean equals(Cut other) {
        return other != null && other.start == this.start && other.end == this.end;
    }
}