package com.metrolist.music.ios

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController(player: AudioPlayer, session: AccountSession) =
    ComposeUIViewController { MetrolistApp(player, session) }
