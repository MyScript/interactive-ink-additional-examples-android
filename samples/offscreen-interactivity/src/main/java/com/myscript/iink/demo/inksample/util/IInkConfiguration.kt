// Copyright @ MyScript. All rights reserved.

package com.myscript.iink.demo.inksample.util

val iinkConfig = """{
  "export": {
    "jiix": {
      "text": {
        "chars": true,
        "words": true
      },
      "bounding-box": true,
      "strokes": false,
      "glyphs": false,
      "primitives": false,
      "ids": true
    }
  },
  "raw-content": {
    "recognition": {
      "types": ["text"]
    },
    "pen": {
      "gestures": ["scratch-out", "strike-through"]
    }
  }
}
"""