package com.maksimowiczm.foodyou.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: WearRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = WearRepository(applicationContext)

        // Mientras la app está a la vista: sincroniza y escucha los avisos en vivo; si se corta,
        // reintenta con espera creciente.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var wait = 2_000L
                while (true) {
                    repository.sync()
                    runCatching { repository.listen() }
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(60_000)
                }
            }
        }

        setContent { MaterialTheme { AppScaffold { WearApp(repository) } } }
    }
}

@Composable
private fun WearApp(repository: WearRepository) {
    val state by repository.state.collectAsState()
    val problem by repository.problem.collectAsState()
    if (state.paired) {
        TodayScreen(repository, state, problem)
    } else {
        PairScreen(repository, problem)
    }
}

@Composable
private fun TodayScreen(repository: WearRepository, state: WearState, problem: WearProblem?) {
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val today = remember { LocalDate.now().toEpochDay() }
    val view = remember(state.docs) { state.dayView(today) }
    var confirmUnpair by remember { mutableStateOf(false) }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item {
                ListHeader {
                    Text(
                        "${view.kcalEaten} / ${view.kcalPlanned} kcal",
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (problem == WearProblem.NoConnection) {
                item {
                    Text(
                        "Sin conexión" + if (state.pending.isNotEmpty()) " · ${state.pending.size} por enviar" else "",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (view.meals.isEmpty()) {
                item {
                    Text(
                        "Nada apuntado hoy",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            view.meals.forEach { meal ->
                item { ListHeader { Text(meal.name) } }
                meal.entries.forEach { row ->
                    item(key = "${row.kind}:${row.id}") {
                        CheckboxButton(
                            checked = row.eaten,
                            onCheckedChange = {
                                repository.toggle(row)
                                scope.launch { repository.sync() }
                            },
                            label = { Text(row.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = row.kcal?.let { { Text("$it kcal") } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                TextButton(
                    onClick = {
                        if (confirmUnpair) scope.launch { repository.unpair() } else confirmUnpair = true
                    },
                ) {
                    Text(if (confirmUnpair) "¿Seguro? Toca otra vez" else "Desemparejar")
                }
            }
            state.account?.let { account ->
                item {
                    Text(
                        account,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PairScreen(repository: WearRepository, problem: WearProblem?) {
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(code) {
        if (code.length == 6 && !busy) {
            busy = true
            val ok = repository.pair(code)
            busy = false
            if (!ok) code = ""
        }
    }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            item {
                Text(
                    "Móvil → Ajustes → Sincronización → Emparejar un reloj",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    if (busy) "Emparejando…" else code.padEnd(6, '·'),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
            when (problem) {
                WearProblem.WrongCode -> item { Text("Código incorrecto o caducado", style = MaterialTheme.typography.labelSmall) }
                WearProblem.NoConnection -> item { Text("Sin conexión", style = MaterialTheme.typography.labelSmall) }
                WearProblem.Unpaired -> item { Text("Se retiró el acceso; empareja de nuevo", style = MaterialTheme.typography.labelSmall) }
                null -> {}
            }
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "")).forEach { keys ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        keys.forEach { key ->
                            if (key.isEmpty()) {
                                androidx.compose.foundation.layout.Spacer(Modifier.size(44.dp))
                            } else {
                                TextButton(
                                    onClick = {
                                        if (busy) return@TextButton
                                        code = if (key == "⌫") code.dropLast(1) else (code + key).take(6)
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Text(key, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
