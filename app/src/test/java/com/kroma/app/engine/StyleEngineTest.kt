package com.kroma.app.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM validation of the Kroma color science (no device / no GPU required).
 * These pin the CPU reference to the behaviour described by the specification.
 */
class StyleEngineTest {

    private val eps = 1e-4f

    private fun assertClose(expected: FloatArray, actual: FloatArray, tol: Float = eps) {
        assertEquals("length", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("channel $i", expected[i].toDouble(), actual[i].toDouble(), tol.toDouble())
        }
    }

    @Test
    fun oklab_roundTrip_isIdentity() {
        val samples = listOf(
            floatArrayOf(0.0f, 0.0f, 0.0f),
            floatArrayOf(1.0f, 1.0f, 1.0f),
            floatArrayOf(0.5f, 0.5f, 0.5f),
            floatArrayOf(0.8f, 0.2f, 0.1f),
            floatArrayOf(0.1f, 0.4f, 0.9f),
            floatArrayOf(0.9f, 0.7f, 0.3f),
        )
        for (rgb in samples) {
            val roundTrip = Oklab.oklabToRgb(Oklab.rgbToOklab(rgb))
            assertClose(rgb, roundTrip, tol = 1e-3f)
        }
    }

    @Test
    fun presetTable_has15Ids_andMatchesAnchors() {
        assertEquals(15, Presets.ALL.size)
        assertEquals((0..14).toList(), Presets.ALL.map { it.id })
        // Spec anchors: Native (0,0), Cinema (0,-45), the two B&W at -100.
        assertEquals(0f, Presets.byId(5).anchorX, 0f)
        assertEquals(-45f, Presets.byId(9).anchorY, 0f)
        assertTrue(Presets.byId(13).monochrome)
        assertTrue(Presets.byId(14).monochrome)
    }

    @Test
    fun neutralPresetAtAnchor_isIdentity() {
        // Balance (id 1) has anchor (0,0) and beta 0 → at the anchor it's a no-op.
        val state = StyleState(presetId = 1, padColor = 0f, padTone = 0f, intensity = 1f)
        for (srgb in listOf(
            floatArrayOf(0.85f, 0.68f, 0.56f),
            floatArrayOf(0.45f, 0.66f, 0.90f),
            floatArrayOf(0.50f, 0.50f, 0.50f),
        )) {
            val out = StyleEngine.renderSrgb(srgb, state)
            assertClose(srgb, out, tol = 1e-3f)
        }
    }

    @Test
    fun nativePreset_atOrigin_isExactBypass() {
        val state = StyleState(presetId = Presets.NATIVE_ID, padColor = 0f, padTone = 0f)
        val base = floatArrayOf(0.42f, 0.51f, 0.63f)
        assertArrayEquals(base, StyleEngine.applyStyle(base, state), 0f)
    }

    @Test
    fun zeroIntensity_returnsBaseForEveryPreset() {
        val base = floatArrayOf(0.6f, 0.45f, 0.35f)
        for (preset in Presets.ALL) {
            val out = StyleEngine.applyStyle(
                base,
                StyleState(presetId = preset.id, padColor = 40f, padTone = -25f, intensity = 0f),
            )
            assertClose(base, out)
        }
    }

    @Test
    fun monochromePresets_removeChroma() {
        val state = StyleState(presetId = 14, padColor = 0f, padTone = 0f, intensity = 1f)
        val out = StyleEngine.renderSrgb(floatArrayOf(0.80f, 0.20f, 0.10f), state)
        // a ≈ b ≈ 0 → the three channels collapse to a grey.
        assertEquals(out[0].toDouble(), out[1].toDouble(), 5e-3)
        assertEquals(out[1].toDouble(), out[2].toDouble(), 5e-3)
    }

    @Test
    fun upwardTone_compressesContrast_andLiftsShadows() {
        // gamma = exp(-effTone/50); effTone > 0 → gamma < 1 → flatter curve (lifted shadows,
        // pulled highlights). This matches the spec's S-curve definition.
        val dark = floatArrayOf(0.25f, 0.25f, 0.25f)
        val bright = floatArrayOf(0.75f, 0.75f, 0.75f)
        val neutral = StyleState(presetId = 1)
        val toned = StyleState(presetId = 1, padTone = 60f)

        val darkN = StyleEngine.renderSrgb(dark, neutral)[1]
        val brightN = StyleEngine.renderSrgb(bright, neutral)[1]
        val darkT = StyleEngine.renderSrgb(dark, toned)[1]
        val brightT = StyleEngine.renderSrgb(bright, toned)[1]

        assertTrue("contrast should compress", (brightT - darkT) < (brightN - darkN))
        assertTrue("shadows should lift (darkT=$darkT darkN=$darkN)", darkT > darkN)
    }

    @Test
    fun vividPreset_increasesChroma() {
        val sky = floatArrayOf(0.45f, 0.66f, 0.90f)
        val neutral = StyleEngine.renderSrgb(sky, StyleState(presetId = 1))
        val vivid = StyleEngine.renderSrgb(sky, StyleState(presetId = 6))
        fun spread(c: FloatArray) = c.max() - c.min()
        assertTrue("Vivid should widen channel spread", spread(vivid) > spread(neutral))
    }
}
