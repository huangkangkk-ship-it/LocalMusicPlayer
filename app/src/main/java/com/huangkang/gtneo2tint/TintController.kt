package com.huangkang.gtneo2tint

/** Runtime Tint control shared by the Camera2/OpenGL rendering path. */
object TintController {
    @Volatile
    var value: Int = 0
        set(v) { field = v.coerceIn(-100, 100) }

    /** Value expected by the GLSL shader: -1.0 .. +1.0. */
    fun normalized(): Float = value / 100f
}
