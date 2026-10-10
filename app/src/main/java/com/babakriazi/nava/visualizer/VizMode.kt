package com.babakriazi.nava.visualizer

enum class VizMode(val label: String) {
    BARS("Spectrum Bars"),
    CIRCULAR("Circular Spectrum"),
    WAVEFORM("Waveform"),
    STARBURST("Starburst"),
    PARTICLES("Particles"),
    TUNNEL("Tunnel");

    companion object {
        fun fromOrdinalSafe(i: Int): VizMode =
            entries.getOrElse(i.coerceIn(0, entries.lastIndex)) { BARS }
    }
}
