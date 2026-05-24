package com.andrey.mapapp.ui.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrey.mapapp.data.local.AppSettings

class SettingsActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = AppSettings(this)
        val arialStyle = TextStyle(fontFamily = FontFamily.Companion.SansSerif)

        setContent {
            val periods = listOf(1, 3, 6, 8, 12, 18, 24)
            var selectedPeriod by remember { mutableStateOf(settings.getWindPeriod()) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Настройки") },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.ArrowBack, "Назад")
                            }
                        }
                    )
                }
            ) { padding ->
                // labels for first setting
                Column(modifier = Modifier.Companion.padding(padding).padding(16.dp)) {
                    Text(
                        text = "Расчёт ветра",
                        style = arialStyle,
                        fontSize = 20.sp,
                        color = Color.Companion.Black
                    )
                    Spacer(modifier = Modifier.Companion.height(18.dp))
                    Text(
                        text = "Период анализа ветра",
                        style = arialStyle,
                        color = Color.Companion.Black,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.Companion.height(8.dp))
                    Text(
                        text = "За какой период собирать данные для розы ветров:",
                        style = arialStyle,
                        fontSize = 16.sp,
                        color = Color.Companion.Gray
                    )
                    Spacer(modifier = Modifier.Companion.height(16.dp))

                    // 1 for dropdown menu ================================================
                    var isExpanded by remember { mutableStateOf(false) }

                    // text format
                    fun getPeriodText(months: Int) = when (months) {
                        1 -> "1 месяц"
                        in 2..4, 24 -> "$months месяца"
                        else -> "$months месяцев"
                    }


                    // список выпадающий Material 3)
                    ExposedDropdownMenuBox(
                        expanded = isExpanded,
                        onExpandedChange = { isExpanded = it }
                    ) {
                        // Поле ввода, которое отображает текущий выбор.
                        // readOnly = true превращает его в обычную кнопку-кликер.
                        TextField(
                            value = getPeriodText(selectedPeriod),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Companion.SansSerif,
                                color = Color.Companion.Black,
                                fontSize = 18.sp
                            ),
                            modifier = Modifier.Companion
                                .fillMaxWidth()
                                .menuAnchor() // КРИТИЧНО ДЛЯ MATERIAL 3: привязывает меню к полю
                        )

                        // menu by itself
                        ExposedDropdownMenu(
                            expanded = isExpanded,
                            onDismissRequest = { isExpanded = false }
                        ) {
                            periods.forEach { months ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = getPeriodText(months),
                                            style = arialStyle,
                                            fontSize = 18.sp
                                        )
                                    },
                                    onClick = {
                                        selectedPeriod = months
                                        settings.saveWindPeriod(months) // saving in SharedPreferences
                                        isExpanded = false // closing menu
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    // ===========================================================================
                    Spacer(modifier = Modifier.Companion.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.Companion.height(16.dp))

                    // ===========================================================================

                    // wind display mode switch ===================================================
                    Text(
                        text = "Визуализация ветра",
                        style = arialStyle,
                        fontSize = 20.sp,
                        color = Color.Companion.Black
                    )
                    Spacer(modifier = Modifier.Companion.height(8.dp))

                    // switch state
                    var showFullRose by remember { mutableStateOf(settings.isFullRoseEnabled()) }

                    Row(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .toggleable( // делает кликабельной всю строку, а не только сам переключатель
                                value = showFullRose,
                                onValueChange = { newValue ->
                                    showFullRose = newValue
                                    settings.setRoseDisplayMode(newValue) // saving
                                }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Companion.CenterVertically
                    ) {
                        Column(modifier = Modifier.Companion.weight(1f)) {
                            Text(
                                text = "Полная роза ветров",
                                fontSize = 18.sp,
                                color = Color.Companion.Black
                            )
                            Spacer(modifier = Modifier.Companion.height(8.dp))
                            Text(
                                text = if (showFullRose) "Отображается полная роза частот" else "Отображается только вектор главного выноса (стрелка)",
                                fontSize = 16.sp,
                                color = Color.Companion.Gray
                            )
                        }

                        // switch
                        Switch(
                            checked = showFullRose,
                            onCheckedChange = { newValue ->
                                showFullRose = newValue
                                settings.setRoseDisplayMode(newValue)
                            }
                        )
                    }
                    // ===========================================================================
                    Spacer(modifier = Modifier.Companion.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.Companion.height(16.dp))

                    // compass display mode switch ===============================================
                    // either to north, either to device internal thing

//                    Text(
//                        text = "Работа компаса",
//                        style = arialStyle,
//                        fontSize = 20.sp,
//                        color = Color.Black
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    // switch state
//                    var compassDeviceMode by remember { mutableStateOf(settings.isCompassDeviceModeEnabled()) }
//
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .toggleable(
//                                value = compassDeviceMode,
//                                onValueChange = { newValue ->
//                                    compassDeviceMode = newValue
//                                    settings.saveCompassMode(newValue) // saving
//                                }
//                            )
//                            .padding(vertical = 10.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column(modifier = Modifier.weight(1f)) {
//                            Text(
//                                text = "Компас устройства (Режим навигации)",
//                                fontSize = 18.sp,
//                                color = Color.Black
//                            )
//                            Spacer(modifier = Modifier.height(8.dp))
//                            Text(
//                                text = if (compassDeviceMode) "Стрелка вращается за твоим телом (датчик телефона)" else "Стрелка статично указывает на Север карты",
//                                fontSize = 16.sp,
//                                color = Color.Gray
//                            )
//                        }
//
//                        // switch
//                        Switch(
//                            checked = compassDeviceMode,
//                            onCheckedChange = { newValue ->
//                                compassDeviceMode = newValue
//                                settings.saveCompassMode(newValue)
//                            }
//                        )
//                    }

                }

            }
        }
    }
}