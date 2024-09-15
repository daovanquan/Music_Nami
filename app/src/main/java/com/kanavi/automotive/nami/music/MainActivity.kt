package com.kanavi.automotive.nami.music

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.kanavi.automotive.nami.music.service.MusicService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val intentService = Intent(this, MusicService::class.java)
        startForegroundService(intentService)
    }
}