package com.andrey.mapapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andrey.mapapp.data.local.entities.ExpeditionEntity

@Composable
fun ExpeditionDrawerContent(
    expeditions: List<ExpeditionEntity>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteClick: (ExpeditionEntity) -> Unit,
    onDetailsClick: (ExpeditionEntity) -> Unit,
    onClearAllDataClick: () -> Unit
) {
    val arialStyle = TextStyle(fontFamily = FontFamily.SansSerif)
    // dialog for deleting all data
    var showDeleteWarningDialog by remember { mutableStateOf(false) }

    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
            Text(
                "Мои экспедиции",
                //style = MaterialTheme.typography.titleLarge,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.SansSerif),
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider()

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(expeditions) { exp ->
                    NavigationDrawerItem(
                        label = { Text(exp.name, style = arialStyle, fontSize = 16.sp) },
                        selected = exp.id == activeId,
                        onClick = { onSelect(exp.id) },
                        shape = RoundedCornerShape(8.dp),
                        badge = {
                            IconButton(onClick = { onDetailsClick(exp) }) {
                                Icon(Icons.Default.List, contentDescription = "Пробы", tint = Color.Gray)
                            }

                            IconButton(onClick = { onDeleteClick(exp) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF6B6B))
                            }
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF90EE90), //button
                    contentColor = Color.Black                  // text
                ),

                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Новая экспедиция", style = arialStyle, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onSettingsClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF90EE90), //button
                    contentColor = Color.Black                  // text
                ),

                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Text("Настройки", style = arialStyle, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // clearing data button
            Button(
                onClick = { onClearAllDataClick() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B6B), // red
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Стереть все данные", style = arialStyle, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

        }
    }
}