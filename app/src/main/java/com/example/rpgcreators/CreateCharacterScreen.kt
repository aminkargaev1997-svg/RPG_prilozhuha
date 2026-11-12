
package com.example.rpgcreators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCharacterScreen(
    onBack: () -> Unit,
    onCreate: (Character) -> Unit,
    character: Character? = null
) {
    var characterName by remember(character?.id) {
        mutableStateOf(character?.name ?: "")
    }

    var selectedRace by remember(character?.id) {
        mutableStateOf(character?.race ?: "Человек")
    }

    var selectedProfession by remember(character?.id) {
        mutableStateOf(character?.profession ?: "Воин")
    }

    var raceMenuExpanded by remember { mutableStateOf(false) }
    var professionMenuExpanded by remember { mutableStateOf(false) }

    val isEditing = character != null

    val races = listOf(
        "Человек",
        "Эльф",
        "Гном",
        "Орк",
        "Драконорождённый"
    )

    val professions = listOf(
        "Воин",
        "Маг",
        "Вор",
        "Ремесленник"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if (isEditing) {
                "Редактирование персонажа"
            } else {
                "Создание персонажа"
            },
            fontSize = 26.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = characterName,
            onValueChange = { characterName = it },
            label = { Text("Имя персонажа") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = raceMenuExpanded,
            onExpandedChange = {
                raceMenuExpanded = it
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedRace,
                onValueChange = {},
                readOnly = true,
                label = { Text("Раса") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = raceMenuExpanded
                    )
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            DropdownMenu(
                expanded = raceMenuExpanded,
                onDismissRequest = {
                    raceMenuExpanded = false
                }
            ) {
                races.forEach { race ->
                    DropdownMenuItem(
                        text = { Text(race) },
                        onClick = {
                            selectedRace = race
                            raceMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = professionMenuExpanded,
            onExpandedChange = {
                professionMenuExpanded = it
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedProfession,
                onValueChange = {},
                readOnly = true,
                label = { Text("Профессия") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = professionMenuExpanded
                    )
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            DropdownMenu(
                expanded = professionMenuExpanded,
                onDismissRequest = {
                    professionMenuExpanded = false
                }
            ) {
                professions.forEach { profession ->
                    DropdownMenuItem(
                        text = { Text(profession) },
                        onClick = {
                            selectedProfession = profession
                            professionMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val name = characterName.trim()

                if (name.isNotEmpty()) {
                    val updatedCharacter = Character(
                        id = character?.id ?: System.currentTimeMillis(),
                        name = name,
                        race = selectedRace,
                        profession = selectedProfession,
                        level = character?.level ?: 1,
                        experience = character?.experience ?: 0,
                        experienceRequired = character?.experienceRequired ?: experienceForLevel(1),
                        autoExperience = character?.autoExperience ?: true
                    )

                    onCreate(updatedCharacter)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isEditing) {
                    "Сохранить изменения"
                } else {
                    "Создать персонажа"
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Назад")
        }
    }
}
