package com.example.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * MenuScreen handles splash page overlays, responsive logo frames, character roster selections,
 * fighter stats, bios, high score rankings, and game over screens.
 */
@Composable
fun MenuScreen(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    when (viewModel.currentScreen) {
        ScreenState.MENU -> SplashMainMenu(viewModel, modifier)
        ScreenState.CHARACTER_SELECT -> CharacterSelectionView(viewModel, modifier)
        ScreenState.CHARACTER_PROFILES -> BioProfilesView(viewModel, modifier)
        ScreenState.GAME_OVER -> RetroGameOverView(viewModel, modifier)
        ScreenState.VICTORY -> RetroVictoryView(viewModel, modifier)
        else -> {}
    }
}

/**
 * Main Arcade menu showing generated cover art, pulsing interactive insert coin labels,
 * and high score leaderboards.
 */
@Composable
fun SplashMainMenu(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04060A))
    ) {
        // Ambient backdrop cover image
        Image(
            painter = painterResource(id = R.drawable.img_streets_hero),
            contentDescription = "Streets cover art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.45f
        )

        // Dark ambient shading
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xBB000000),
                            Color(0xFF04060A)
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Retro Crown Game Logo header
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "BARE KNUCKLE",
                    color = Color.Yellow,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.scale(1.1f)
                )
                Text(
                    text = "شورش در شهر سگا",
                    color = Color(0xFFFF1A4B),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "STREETS OF RAGE REMIX",
                    color = Color.Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Flashing INSERT COIN chiptune label
            item {
                Text(
                    text = "PRESS ANY ACTION TO RUN",
                    color = Color(0xFF00FFCC).copy(alpha = alpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Core Menu option listing cards
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option 1: Start Combat
                    Button(
                        onClick = {
                            ArcadeSynth.playCoin()
                            viewModel.currentScreen = ScreenState.CHARACTER_SELECT
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1A4B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("menu_play_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("1P PLAY ACTION", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    // Option 2: AI Advisor terminal query
                    Button(
                        onClick = {
                            ArcadeSynth.playCoin()
                            // Navigate to Gemini AI tactical screen
                            viewModel.currentScreen = ScreenState.CHARACTER_PROFILES // profiles house advisor
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .border(1.5.dp, Color.Cyan, RoundedCornerShape(8.dp))
                            .testTag("menu_advisor_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "AI", tint = Color.Cyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("AI TACTICAL ADVISOR", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // High Score Hall of Fame registry
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .background(Color(0xE00D1117), RoundedCornerShape(8.dp))
                        .border(1.5.dp, Color(0xFFFFCC00).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Trophy", tint = Color(0xFFFFCC00))
                        Text(
                            text = "ARCADE HIGH SCORE REGISTRY",
                            color = Color(0xFFFFCC00),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    viewModel.highScores.forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${idx + 1}. ${entry.first}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format("%06d PTS", entry.second),
                                color = Color.Cyan,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Character Selection flow.
 * Showcases customizable Materials 3 bar charts graphing fighter specs, special move details
 * andconfirm button.
 */
@Composable
fun CharacterSelectionView(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val templates = viewModel.characters
    val activeIdx = viewModel.selectedCharacterIndex
    val activeFighter = templates[activeIdx]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04060A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT 1P CHARACTER",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "PLAYER SELECT",
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Roster carousel selectors Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                templates.forEachIndexed { idx, char ->
                    val isSelected = idx == activeIdx
                    val frameColor = if (isSelected) Color(0xFF00FFCC) else Color(0xFF1E2638)
                    val backGrad = if (isSelected) {
                        Brush.verticalGradient(listOf(Color(0xFF133A44), Color(0xFF0A1F26)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0xFF0C101A), Color(0xFF04060A)))
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(backGrad)
                            .border(2.5.dp, frameColor, RoundedCornerShape(8.dp))
                            .clickable {
                                ArcadeSynth.playJump()
                                viewModel.selectedCharacterIndex = idx
                            }
                            .padding(4.dp)
                            .testTag("char_select_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = char.name.take(2).uppercase(),
                                color = if (isSelected) Color(0xFF00FFCC) else Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                            Text(
                                text = char.name.substringAfter(" "),
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Fighter Details card containing specs (Power, Speed, Jump) and bio description!
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B101A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Title / Bio Section
                        Text(
                            text = activeFighter.name.uppercase(),
                            color = Color(activeFighter.primaryColorHex.toLong(16)),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = activeFighter.description,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeFighter.bio,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    item {
                        // Fighter Stats graphs
                        Divider(color = Color(0xFF1E2638))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "FIGHTER CHARACTERISTICS:",
                            color = Color.Yellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Stat Row 1: Power
                    item {
                        StatBarItem(label = "STRIKING POWER", statValue = activeFighter.power, maxVal = 5, barColor = Color.Red)
                    }
                    // Stat Row 2: Speed
                    item {
                        StatBarItem(label = "AGILITY SPEED", statValue = activeFighter.speed, maxVal = 5, barColor = Color.Cyan)
                    }
                    // Stat Row 3: Jump
                    item {
                        StatBarItem(label = "VERTICAL JUMP", statValue = activeFighter.jump, maxVal = 5, barColor = Color.Green)
                    }

                    // Specialty move highlight
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1105), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFFFF9900), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "SPECIAL ULTRA TECHNIQUE:",
                                    color = Color(0xFFFF9900),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = activeFighter.specialMoveName.uppercase(),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Navigation confirm controllers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cancel Back
                OutlinedButton(
                    onClick = {
                        ArcadeSynth.playJump()
                        viewModel.currentScreen = ScreenState.MENU
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.5.dp, Color(0xFF1E2638)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("MENU")
                }

                // Confirm Action Launch Game Level!
                Button(
                    onClick = {
                        ArcadeSynth.playCoin()
                        viewModel.startNewGame(activeFighter)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1.8f)
                        .height(50.dp)
                        .testTag("confirm_char_button")
                ) {
                    Text(
                        text = "CONFIRM FIGHTER (1P)",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Custom Material 3 styled stat metrics bar graph
 */
@Composable
fun StatBarItem(label: String, statValue: Int, maxVal: Int, barColor: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "$statValue/$maxVal", color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 1..maxVal) {
                val filled = i <= statValue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(if (filled) barColor else Color(0xFF131722), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/**
 * Bio and profile view framing the AI Strategies terminal console.
 * Integrated to act simultaneously as a retro biography browser and strategy advisory panel!
 */
@Composable
fun BioProfilesView(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    AiTerminal(
        onBack = {
            ArcadeSynth.playJump()
            viewModel.currentScreen = ScreenState.MENU
        },
        modifier = modifier
    )
}

/**
 * Retro Arcade Game Over Screen.
 */
@Composable
fun RetroGameOverView(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "GAME OVER",
                color = Color.Red,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "YOUR FINAL SCORE: ${viewModel.playerScore} PTS",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = {
                    ArcadeSynth.playCoin()
                    viewModel.currentScreen = ScreenState.MENU
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9900)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(180.dp)
                    .height(48.dp)
                    .testTag("game_over_retry_button")
            ) {
                Text(
                    text = "CONTINUE RETRY",
                    color = Color.Black,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * Retro victory screen.
 */
@Composable
fun RetroVictoryView(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF042217)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Victory Medal",
                tint = Color.Yellow,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "VICTORY!!",
                color = Color.Yellow,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "CONGRATULATIONS FIGHTER!\nYOU RESTORED ORDER TO THE CITY STREETS.",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Text(
                text = "CHAMP PIECE SCORE: ${viewModel.playerScore} PTS",
                color = Color(0xFF00FFCC),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = {
                    ArcadeSynth.playCoin()
                    viewModel.currentScreen = ScreenState.MENU
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .width(180.dp)
                    .height(48.dp)
                    .testTag("victory_continue_button")
            ) {
                Text(
                    text = "START SYSTEM OVER",
                    color = Color.Black,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
