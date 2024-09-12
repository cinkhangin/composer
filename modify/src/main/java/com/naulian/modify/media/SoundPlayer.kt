package com.naulian.modify.media

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes

fun Context.playSound(@RawRes resId: Int) {
    SoundPlayer.play()
}

fun releaseSoundPlayer() {
    SoundPlayer.release()
}

object SoundPlayer {
    private var _player: MediaPlayer? = null

    fun create(context: Context, @RawRes resId: Int): SoundPlayer {
        release()
        _player = MediaPlayer.create(context, resId)
        return this
    }

    fun play() {
        _player?.start()
    }

    fun release() {
        _player?.release()
    }
}
