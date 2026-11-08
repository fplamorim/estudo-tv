package com.fernandoamorim.estudotv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtualizadorTest {
    @Test fun comparaVersoes() {
        assertTrue(Atualizador.maisNova("0.1.1", "0.1.0"))
        assertTrue(Atualizador.maisNova("1.0.0", "0.9.9"))
        assertTrue(Atualizador.maisNova("1.2.10", "1.2.9"))
        assertFalse(Atualizador.maisNova("1.2.9", "1.2.10"))
        assertFalse(Atualizador.maisNova("0.1.0", "0.1.0"))
        assertFalse(Atualizador.maisNova("0.0.9", "0.1.0"))
    }
}
