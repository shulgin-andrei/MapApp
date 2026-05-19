package com.andrey.mapapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.andrey.mapapp.data.local.entities.ExpeditionEntity

@Composable
fun ExpeditionDrawerContent(
    expeditions: List<ExpeditionEntity>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onAddClick: () -> Unit,
    onDeleteClick: (ExpeditionEntity) -> Unit,
    onDetailsClick: (ExpeditionEntity) -> Unit
) {
    val arialStyle = TextStyle(fontFamily = FontFamily.SansSerif)
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
                        label = { Text(exp.name) },
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

            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF90EE90), //button
                    contentColor = Color.Black                  // text
                ),

                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Новая экспедиция", style = arialStyle)
            }
        }
    }
}