package com.myscript.iink.demo.inksample.util

val iinkExportConfig = """{
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
      "text": true,
      "shape": false
    },
    "pen": {
      "gestures": []
    }
  }
}
"""