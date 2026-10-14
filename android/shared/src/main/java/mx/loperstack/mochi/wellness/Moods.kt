// The diary's five moods, each with Mochi's face for it — shared by the
// phone's Diario and the watch's quick "¿Cómo te sientes?".

package mx.loperstack.mochi.wellness

import mx.loperstack.mochi.mochi.MochiEngine

data class MoodLevel(val value: Int, val label: String, val color: Long, val face: MochiEngine.Eye)

val MOODS = listOf(
    MoodLevel(5, "Genial", 0xFF4ADE80, MochiEngine.Eye.STAR),
    MoodLevel(4, "Bien", 0xFF2DD4BF, MochiEngine.Eye.HAPPY),
    MoodLevel(3, "Normal", 0xFFA5A9B1, MochiEngine.Eye.PILL),
    MoodLevel(2, "Bajón", 0xFF818CF8, MochiEngine.Eye.TIRED),
    MoodLevel(1, "Mal", 0xFFF4505E, MochiEngine.Eye.LINE),
)

fun moodOf(value: Int): MoodLevel? = MOODS.firstOrNull { it.value == value }
