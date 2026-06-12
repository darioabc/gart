package gen

import dev.oblac.gart.Gart
import dev.oblac.gart.color.CssColors
import dev.oblac.gart.gfx.drawCircle
import dev.oblac.gart.gfx.fillOfRed

/**
 * Headless PNG template for the `generate-art` skill.
 *
 * Key points for headless rendering:
 *  - Do NOT call gart.window()/showImage() — that opens a Swing window and
 *    throws in headless environments.
 *  - gart.saveImage(g) writes "<name>.png" to the current working directory.
 *
 * Render with:
 *   .claude/skills/generate-art/render.sh arts:gen gen.TemplateKt
 */
fun main() {
    val gart = Gart.of("template", 1024, 1024)
    val g = gart.gartvas()
    val c = g.canvas
    val d = gart.d

    c.clear(CssColors.navy)
    c.drawCircle(d.center, 200f, fillOfRed())

    gart.saveImage(g) // -> template.png in the current directory
}
