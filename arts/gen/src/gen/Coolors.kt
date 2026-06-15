package gen

import dev.oblac.gart.color.Palette

/**
 * Ten coolors.co palettes driving the "go wild" batch.
 *
 * Four were hand-picked by the user from coolors.co (the ink/navy + gold/ember +
 * cream family); six are perennial top-trending coolors sets chosen to add range
 * (pastels, ocean, neon, teal-rose). To pin a different set, swap the hexes below.
 */
object Coolors {
    // ── user-grabbed from coolors.co ─────────────────────────────────────────
    // "Ink & Ember"  — Ink Black / Dark Slate Grey / Wheat / Burnt Orange / Midnight Violet
    val inkEmber = Palette(0xFF04151F, 0xFF183A37, 0xFFEFD6AC, 0xFFC44900, 0xFF432534)

    // "Patriot Gold" — Deep Space Blue / Flag Red / Princeton Orange / Sunflower Gold / Vanilla Custard
    val patriotGold = Palette(0xFF003049, 0xFFD62828, 0xFFF77F00, 0xFFFCBF49, 0xFFEAE2B7)

    // "Navy & Gold"  — Ink Black / Prussian Blue / Oxford Navy / School-Bus Yellow / Gold
    val navyGold = Palette(0xFF000814, 0xFF001D3D, 0xFF003566, 0xFFFFC300, 0xFFFFD60A)

    // "Molten"       — Molten Lava / Flag Red / Papaya Whip / Deep Space Blue / Steel Blue
    val molten = Palette(0xFF780000, 0xFFC1121F, 0xFFFDF0D5, 0xFF003049, 0xFF669BBC)

    // ── trending coolors picks (mine) ────────────────────────────────────────
    val sunset = Palette(0xFF264653, 0xFF2A9D8F, 0xFFE9C46A, 0xFFF4A261, 0xFFE76F51)
    val pastelDream = Palette(0xFFCDB4DB, 0xFFFFC8DD, 0xFFFFAFCC, 0xFFBDE0FE, 0xFFA2D2FF)
    val seaBlue = Palette(0xFF03045E, 0xFF0077B6, 0xFF00B4D8, 0xFF90E0EF, 0xFFCAF0F8)
    val tealRose = Palette(0xFF006D77, 0xFF83C5BE, 0xFFEDF6F9, 0xFFFFDDD2, 0xFFE29578)
    val electricGrape = Palette(
        0xFF7209B7, 0xFF560BAD, 0xFF480CA8, 0xFF3A0CA3,
        0xFF3F37C9, 0xFF4361EE, 0xFF4895EF, 0xFF4CC9F0
    )
    val retroPop = Palette(0xFFFFBE0B, 0xFFFB5607, 0xFFFF006E, 0xFF8338EC, 0xFF3A86FF)

    val all = listOf(
        inkEmber, patriotGold, navyGold, molten, sunset,
        pastelDream, seaBlue, tealRose, electricGrape, retroPop
    )
}
