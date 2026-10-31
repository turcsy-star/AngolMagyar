package hu.angolmagyar.fordito.core

/**
 * :core modul – tiszta / újrafelhasználható logika (TextPolish, TM, Whisper bridge).
 * Az app modul függ: implementation(project(":core")).
 * A tiszta Kotlin logika (szöveg, VAD, SRT, ellenőrzés, online fordító) ide költözött; a Room-os adatréteg az :app-ban maradt.
 */
object CoreInfo {
    const val MODULE = "core"
    const val API_LEVEL = 1
}
