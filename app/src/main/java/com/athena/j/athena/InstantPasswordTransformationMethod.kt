package com.athena.j.athena

import android.graphics.Rect
import android.text.method.TransformationMethod
import android.view.View

class InstantPasswordTransformationMethod : TransformationMethod {
    override fun getTransformation(source: CharSequence?, view: View?): CharSequence {
        if (source == null) return ""
        return object : CharSequence {
            override val length: Int
                get() = source.length
            override fun get(index: Int): Char {
                return '•'
            }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
                val length = endIndex - startIndex
                return "•".repeat(length)
            }
            override fun toString(): String {
                return "•".repeat(length)
            }
        }
    }
    override fun onFocusChanged(
        view: View?,
        sourceText: CharSequence?,
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {}
}