package com.maksimowiczm.foodyou.assistant.domain

import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolRegistry
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.FunctionCall
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.ToolCall
import com.maksimowiczm.foodyou.common.log.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Whether several tool calls from one response really run side by side - and, just as important,
 * that they never do when one of them writes.
 *
 * Virtual time makes "side by side" measurable: three reads that each wait 100 ms finish at 100 ms
 * when they overlap and at 300 ms when they queue.
 */
class ToolCallRunnerTest {

    /** Counts how many calls are inside it at the same moment. */
    private class Tracker {
        var inside = 0
        var mostAtOnce = 0
    }

    private class SlowTool(
        override val name: String,
        override val runsConcurrently: Boolean,
        override val mutates: Boolean,
        private val tracker: Tracker,
    ) : AssistantTool {
        override val description = "test"
        override val parameters = ToolSchema.obj()

        override suspend fun call(arguments: JsonObject): JsonElement {
            tracker.inside++
            tracker.mostAtOnce = maxOf(tracker.mostAtOnce, tracker.inside)
            delay(100)
            tracker.inside--
            // Devuelve lo que se le pidio, para comprobar que cada resultado va con su llamada.
            return JsonPrimitive("$name:${arguments["q"]?.jsonPrimitive?.content}")
        }
    }

    private class FailingTool : AssistantTool {
        override val name = "broken"
        override val description = "test"
        override val parameters = ToolSchema.obj()
        override val runsConcurrently = true

        override suspend fun call(arguments: JsonObject): JsonElement = error("boom")
    }

    private object SilentLogger : Logger {
        override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun w(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private val tracker = Tracker()
    private val runner =
        ToolCallRunner(
            ToolRegistry(
                listOf(
                    SlowTool("searchFood", runsConcurrently = true, mutates = false, tracker),
                    SlowTool("goals", runsConcurrently = true, mutates = false, tracker),
                    SlowTool("addEntries", runsConcurrently = false, mutates = true, tracker),
                    // Escribe en el borrador aunque no toque el diario: tampoco puede ir en paralelo.
                    SlowTool("padAdd", runsConcurrently = false, mutates = false, tracker),
                    FailingTool(),
                )
            ),
            SilentLogger,
        )

    private fun call(name: String, q: String) =
        ToolCall(id = "id-$q", function = FunctionCall(name, """{"q":"$q"}"""))

    @Test
    fun severalReadsRunSideBySide() = runTest {
        val calls =
            listOf(call("searchFood", "pan"), call("searchFood", "carne"), call("goals", "hoy"))

        val results = runner.runAll(calls)

        assertEquals(3, tracker.mostAtOnce)
        assertEquals(100, testScheduler.currentTime)
        // El orden es el de las llamadas, no el de quien termina antes.
        assertEquals(
            listOf("\"searchFood:pan\"", "\"searchFood:carne\"", "\"goals:hoy\""),
            results,
        )
    }

    @Test
    fun aWriteAmongThemPutsEverythingInOrder() = runTest {
        val calls = listOf(call("searchFood", "pan"), call("addEntries", "pan"))

        assertFalse(runner.canRunTogether(calls))
        runner.runAll(calls)

        assertEquals(1, tracker.mostAtOnce)
        assertEquals(200, testScheduler.currentTime)
    }

    @Test
    fun theDraftToolsAreNeverConcurrentEvenThoughTheyDoNotWriteTheDiary() = runTest {
        val calls = listOf(call("padAdd", "a"), call("padAdd", "b"))

        assertFalse(runner.canRunTogether(calls))
        runner.runAll(calls)

        assertEquals(1, tracker.mostAtOnce)
    }

    @Test
    fun aSingleCallIsNotAGroup() {
        assertFalse(runner.canRunTogether(listOf(call("searchFood", "pan"))))
        assertTrue(runner.canRunTogether(listOf(call("searchFood", "a"), call("goals", "b"))))
    }

    @Test
    fun oneToolFailingDoesNotSinkTheOthers() = runTest {
        // Si una lectura revienta en paralelo, las demas tienen que llegar igual al modelo.
        val results = runner.runAll(listOf(call("searchFood", "pan"), call("broken", "x")))

        assertEquals("\"searchFood:pan\"", results[0])
        assertTrue(results[1].contains("\"ok\":false"), results[1])
        assertTrue(results[1].contains("boom"), results[1])
    }

    @Test
    fun anUnknownToolIsAnErrorTheModelCanRead() = runTest {
        val result = runner.run(call("noExiste", "x"))

        assertTrue(result.contains("noExiste"), result)
    }
}
