package com.ricardo.txtreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ricardo.txtreader.navigation.PasteReaderApp
import com.ricardo.txtreader.tts.PasteReaderViewModel
import com.ricardo.txtreader.ui.theme.TxtReaderTheme

class MainActivity : ComponentActivity() {
    private val pasteReaderViewModel: PasteReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TxtReaderTheme {
                PasteReaderApp(pasteReaderViewModel)
            }
        }
    }
}
