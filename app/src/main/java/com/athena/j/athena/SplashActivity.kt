package com.athena.j.athena

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity


/**
 * Displays Athena's splash screen briefly
 * before opening the main login screen.
 */
class SplashActivity : AppCompatActivity() {

    // =============================================================
    // CREATE
    // =============================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_splash
        )

        // Brief delay for a smooth transition.
        Handler(
            Looper.getMainLooper()
        ).postDelayed(
            {

                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    )
                )

                overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
                )

                finish()

            },
            10L
        )
    }
}