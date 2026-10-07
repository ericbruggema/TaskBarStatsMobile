package com.ericbruggema.taskbarstats

import android.app.Application

/** Laadt thema en lettertype zodra het proces start, ook als alleen de service of een widget draait (bijv. na een herstart). */
class TaskBarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Themes.load(this)
        Fonts.load(this)
    }
}
