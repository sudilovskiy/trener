package com.example.trener.music

import android.content.Context
import android.os.Environment
import java.io.File

internal object MusicFolderResolver {
    private const val MUSIC_SUBDIRECTORY = "trener"

    fun resolvePlaybackDirectory(context: Context): File {
        val musicRoot = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, Environment.DIRECTORY_MUSIC)
        return File(musicRoot, MUSIC_SUBDIRECTORY)
    }
}
