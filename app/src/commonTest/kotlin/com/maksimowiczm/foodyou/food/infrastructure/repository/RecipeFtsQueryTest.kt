package com.maksimowiczm.foodyou.food.infrastructure.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the assistant types goes straight into an FTS MATCH. A query MATCH rejects is a SQL error
 * in the middle of a chat turn; one it misreads is a search for something else. Both would pass
 * any test that only uses tidy dish names.
 */
class RecipeFtsQueryTest {

    @Test
    fun wordsBecomeQuotedLowerCasePrefixes() {
        // El indice ya pliega tildes y mayusculas por los dos lados: basta con no romperlo.
        assertEquals("\"albóndigas*\" \"caseras*\"", recipeFtsQuery("Albóndigas Caseras"))
    }

    @Test
    fun operatorsAreSearchedAsWordsNotObeyed() {
        // En FTS, AND/OR/NOT/NEAR en mayusculas son operadores. Entre comillas y en minusculas no.
        assertEquals("\"salsa*\" \"and*\" \"queso*\"", recipeFtsQuery("salsa AND queso"))
        assertEquals("\"near*\" \"pan*\"", recipeFtsQuery("NEAR pan"))
    }

    @Test
    fun punctuationCannotReachMatch() {
        // Guiones, comillas, asteriscos y parentesis tienen significado para MATCH.
        assertEquals("\"pan*\" \"con*\" \"tomate*\"", recipeFtsQuery("pan-con \"tomate\""))
        assertEquals("\"bocadillo*\" \"lomo*\"", recipeFtsQuery("bocadillo (lomo)*"))
        assertEquals("\"arroz*\" \"3*\" \"delicias*\"", recipeFtsQuery("arroz 3 delicias"))
    }

    @Test
    fun nothingToSearchIsNullNotAnEmptyMatch() {
        // MATCH '' es un error, no "ningun resultado".
        assertNull(recipeFtsQuery(""))
        assertNull(recipeFtsQuery("   "))
        assertNull(recipeFtsQuery("*** -- \"\""))
    }

    @Test
    fun aParagraphIsCutToADishName() {
        assertEquals(
            "\"a*\" \"b*\" \"c*\" \"d*\" \"e*\" \"f*\"",
            recipeFtsQuery("a b c d e f g h"),
        )
    }
}
