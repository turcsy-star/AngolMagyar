package hu.angolmagyar.fordito.core

/**
 * :core modul – tiszta / újrafelhasználható logika (TextPolish, TM, Whisper bridge).
 * Az app modul függ: implementation(project(":core")).
 * A meglévő core/* osztályok (db, TextPolish, WhisperNativeBridge) fokozatosan ide költöznek.
 */
object CoreInfo {
    const val MODULE = "core"
    const val API_LEVEL = 1
}
