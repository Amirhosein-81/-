package com.example.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.game.ActionState
import com.example.game.GroundItem
import com.example.game.ItemType
import com.example.game.WeaponType
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * GameView contains the interactive game screen, custom Joystick controller,
 * action cabinet buttons, in-game stats overlays, and parallax gaming engine.
 */
@Composable
fun GameView(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val player = viewModel.player ?: return
    val enemies = viewModel.enemies
    val particles = viewModel.particles
    val items = viewModel.groundItems
    val screenShake = viewModel.screenShakeAmount

    // Screen shaking offsets
    val shakeOffset = remember(screenShake) {
        if (screenShake > 0) {
            val angle = Math.random() * 2.0 * Math.PI
            Offset(
                (cos(angle) * screenShake).toFloat() * 1.5f,
                (sin(angle) * screenShake).toFloat() * 1.5f
            )
        } else {
            Offset.Zero
        }
    }

    // Load background resources
    val bgPainter = painterResource(id = R.drawable.img_game_background)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14)) // Deep cyber void
    ) {
        // --- 1. GAME DISPLAY BLOCK (Pseudo-CRT Window) ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)
        ) {
            // Screen Window (Uses Box Constraints for adaptive sizing)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(3.dp, Color(0xFF1E2638), RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                val screenWidth = constraints.maxWidth.toFloat()
                val screenHeight = constraints.maxHeight.toFloat()

                // Calculate horizontal parallax scroll
                // Game uses absolute stage width (3000f). Map cameraX scroll position.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = shakeOffset.x.dp, y = shakeOffset.y.dp)
                ) {
                    // Parallax Background layer 1 (Far background scrolling slowly)
                    Image(
                        painter = bgPainter,
                        contentDescription = "Far Background",
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1800.dp)
                            .offset(x = (-viewModel.cameraX * 0.35f).dp), // Parallax slowdown factor
                        alignment = Alignment.TopStart
                    )

                    // Layer 2: Main Playable Street Canvas (Handles characters and foreground layers)
                    ArenaCanvas(
                        viewModel = viewModel,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Wave Clear Flashing Indicator
                    if (viewModel.showGoIndicator) {
                        AnimatedPulsingGo(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 24.dp)
                        )
                    }

                    // Boss Approach warning
                    if (viewModel.currentWave == viewModel.totalWaves && enemies.any { it.name.contains("BOSS") }) {
                        val boss = enemies.first { it.name.contains("BOSS") }
                        if (boss.hp > 230) {
                            BossWarningBanner()
                        }
                    }

                    // Static CRT Scanlines effects over the game window
                    CrtMonitorOverlay()

                    // Visual Game state HUD (HP bar, scores, combo indicators)
                    GameHud(viewModel, modifier = Modifier.padding(12.dp))
                }
            }

            // --- 2. RETRO ARCADE CABINET CONTROLLER DECK ---
            ArcadeDeck(
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF151922),
                                Color(0xFF0F1116),
                                Color(0xFF050608)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF2C354A))
            )
        }
    }
}

/**
 * Game Canvas layer.
 * Manages spatial projections of 2.5D floor depth (y values)
 * and elevation (z values). Sorts characters in Z-order array by their Y coordinates
 * to handle correct front/back visual overlap!
 */
@Composable
fun ArenaCanvas(
    viewModel: GameViewModel,
    screenWidth: Float,
    screenHeight: Float,
    modifier: Modifier = Modifier
) {
    val player = viewModel.player ?: return
    val enemies = viewModel.enemies
    val items = viewModel.groundItems
    val particles = viewModel.particles
    val cameraX = viewModel.cameraX

    // Dynamic horizontal scale factors to map game coordinates (0 -> 3000) to actual canvas screen widths
    val scaleFactor = screenWidth / 800f 
    // Street road depth starts around 30% down the screen, depth height occupies 45% of bottom height
    val streetTopY = screenHeight * 0.50f
    val streetBottomY = screenHeight * 0.95f
    val streetPlayableHeight = streetBottomY - streetTopY

    // Map a game coordinate to screen offsets
    fun getScreenOffset(gameX: Float, gameY: Float, gameZ: Float): Offset {
        val screenX = (gameX - cameraX) * scaleFactor
        // Map 120-220 game depth to the playable street vertical space
        val normalizedY = (gameY - 120f) / 100f // 0.0 -> 1.0
        val screenY = streetTopY + (normalizedY * streetPlayableHeight) - (gameZ * scaleFactor)
        return Offset(screenX, screenY)
    }

    // Sort order for correct Depth rendering
    val renderables = remember(player.y, enemies.map { it.y }) {
        val list = mutableListOf<Combatant>()
        list.add(player)
        list.addAll(enemies)
        list.sortBy { it.y } // Sort from back to front (low Y is back, high Y is front)
        list
    }

    Canvas(modifier = modifier) {
        val canvasWidth = this.size.width
        val canvasHeight = this.size.height

        // 1. Draw Ground Street Sidewalk decorations (Sega retro decals!)
        val streetTopPixel = streetTopY
        val streetBottomPixel = streetBottomY
        
        // Sidewalk line
        drawLine(
            color = Color(0xFF4C5B7F),
            start = Offset(0f, streetTopPixel),
            end = Offset(canvasWidth, streetTopPixel),
            strokeWidth = 3f
        )
        // Sidewalk perspective lines (cyber grid look!)
        for (i in 0..12) {
            val step = canvasWidth / 12 * i
            drawLine(
                color = Color(0xFF1B233A),
                start = Offset(step, streetTopPixel),
                end = Offset(step - 150f, streetBottomPixel),
                strokeWidth = 1.5f
            )
        }

        // 2. Draw Ground items (Barrels, Weapons, Healing Roast chicken)
        for (item in items) {
            val feet = getScreenOffset(item.x, item.y, 0f)
            
            // Draw item shadow
            drawOval(
                color = Color(0x60000000),
                topLeft = Offset(feet.x - 25f * scaleFactor, feet.y - 8f * scaleFactor),
                size = Size(50f * scaleFactor, 16f * scaleFactor)
            )

            if (item.isBreakableBarrel) {
                // Draw Retro styled wooden barrel container
                val bWidth = 36f * scaleFactor
                val bHeight = 50f * scaleFactor
                val top = Offset(feet.x - bWidth / 2, feet.y - bHeight)

                // Barrel base container
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFF8B5A2B), Color(0xFF5C3A21))),
                    topLeft = top,
                    size = Size(bWidth, bHeight)
                )
                // Metal bands around the barrel
                drawLine(Color(0xFFADB8C4), Offset(top.x, top.y + bHeight * 0.25f), Offset(top.x + bWidth, top.y + bHeight * 0.25f), strokeWidth = 3.5f)
                drawLine(Color(0xFFADB8C4), Offset(top.x, top.y + bHeight * 0.75f), Offset(top.x + bWidth, top.y + bHeight * 0.75f), strokeWidth = 3.5f)
                
                // Wooden planks lines
                for (pIdx in 1..3) {
                    val pX = top.x + (bWidth / 4) * pIdx
                    drawLine(Color(0xFF422A1D), Offset(pX, top.y), Offset(pX, top.y + bHeight), strokeWidth = 1.5f)
                }
            } else {
                // Render spawned pickup loot
                val size = 22f * scaleFactor
                val center = Offset(feet.x, feet.y - size / 2)
                when (item.itemType) {
                    ItemType.ROAST_CHICKEN -> {
                        // Drawing delicious roasted poultry
                        drawOval(Color(0xFFFFF0DB), Offset(center.x - 14f, center.y - 7f), Size(28f, 14f)) // Platter plate
                        drawOval(Brush.radialGradient(listOf(Color(0xFFFFB366), Color(0xFFCC6600))), Offset(center.x - 10f, center.y - 12f), Size(20f, 15f)) // Meat roll
                        drawCircle(Color(0xFFFFCC99), radius = 6f, center = Offset(center.x + 8f, center.y - 6f)) // Drumstick bone tip
                    }
                    ItemType.APPLE -> {
                        // Glossy red retro apple
                        drawCircle(Color.Red, radius = 9f, center = center)
                        drawLine(Color(0xFF33CC33), center, Offset(center.x + 4f, center.y - 12f), strokeWidth = 3f) // leaf
                    }
                    ItemType.PIPE_WEAPON -> {
                        // Shiny chrome pipe
                        drawLine(
                            color = Color(0xFFD1D5DB),
                            start = Offset(center.x - 20f, center.y + 10f),
                            end = Offset(center.x + 20f, center.y - 10f),
                            strokeWidth = 5f
                        )
                        drawLine(Color.White, Offset(center.x - 12f, center.y + 6f), Offset(center.x + 12f, center.y - 6f), strokeWidth = 1.5f)
                    }
                    ItemType.DAGGER_WEAPON -> {
                        // Metallic glowing tactical knife
                        val daggerPath = Path().apply {
                            moveTo(center.x - 14f, center.y + 4f)
                            lineTo(center.x + 16f, center.y - 10f)
                            lineTo(center.x + 10f, center.y - 6f)
                            moveTo(center.x - 8f, center.y + 4f)
                            lineTo(center.x - 17f, center.y + 11f) // Black handle
                        }
                        drawPath(daggerPath, Color(0xFFEF4444), style = Stroke(width = 3.5f))
                        drawLine(Color.White, Offset(center.x - 10f, center.y + 2f), Offset(center.x + 15f, center.y - 9f), strokeWidth = 2f)
                    }
                }
            }
        }

        // 3. Draw sorted Characters (depth indices)
        for (char in renderables) {
            val feet = getScreenOffset(char.x, char.y, 0f)
            val body = getScreenOffset(char.x, char.y, char.z)

            // Dynamic shadow size shrinking as character jumps higher
            val shadowShrink = (1.0f - (char.z / 180f)).coerceAtLeast(0.3f)
            drawOval(
                color = Color(0x6A000000),
                topLeft = Offset(feet.x - (28f * scaleFactor * shadowShrink), feet.y - (8f * scaleFactor * shadowShrink)),
                size = Size(56f * scaleFactor * shadowShrink, 16f * scaleFactor * shadowShrink)
            )

            // Draw character model
            drawCharacterSprite(
                char = char,
                headOffset = body,
                scaleFactor = scaleFactor
            )
        }

        // 4. Render floating active impact particles, labels, and damage popups
        for (p in particles) {
            val pos = getScreenOffset(p.x, p.y, 0f)
            
            // Render text
            val textPaint = android.graphics.Paint().apply {
                color = if (p.isDamage) android.graphics.Color.RED else android.graphics.Color.YELLOW
                textSize = (14f + p.lifespan * 0.4f) * scaleFactor
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
                if (!p.isDamage) {
                    setShadowLayer(5.0f * scaleFactor, 0f, 0f, android.graphics.Color.MAGENTA)
                }
            }
            
            drawContext.canvas.nativeCanvas.drawText(
                p.text,
                pos.x,
                pos.y + p.vy * 5f,
                textPaint
            )
        }
    }
}

/**
 * Procedural Vector Drawing for Streets of Rage Sprites!
 * We draw characters in appropriate action stances (Stretching for punch, kicking,
 * double flying jump kicks, falling backwards with stars, and floating specials!)
 */
fun DrawScope.drawCharacterSprite(
    char: Combatant,
    headOffset: Offset,
    scaleFactor: Float
) {
    val faceDir = if (char.isFacingRight) 1f else -1f
    val mainColor = char.template?.primaryColorHex?.let { Color(it.toLong(16)) } ?: Color(0xFFADB5BD)
    val accentColor = char.template?.accentColorHex?.let { Color(it.toLong(16)) } ?: Color.White

    val size = 45f * scaleFactor
    val r = 10f * scaleFactor

    // Animation frames based on state ticks
    val strokeWidth = 4f * scaleFactor

    // Determine current pose parameters based on ActionState
    var torsoOffset = Offset(headOffset.x, headOffset.y - 12f * scaleFactor)
    var armL = Offset(torsoOffset.x - 14f * scaleFactor * faceDir, torsoOffset.y + 4f * scaleFactor)
    var armR = Offset(torsoOffset.x + 14f * scaleFactor * faceDir, torsoOffset.y + 3f * scaleFactor)
    var footL = Offset(headOffset.x - 10f * scaleFactor, headOffset.y + 22f * scaleFactor)
    var footR = Offset(headOffset.x + 10f * scaleFactor, headOffset.y + 22f * scaleFactor)

    if (char.actionState == ActionState.PUNCHING_1 || char.actionState == ActionState.PUNCHING_2) {
        // High impact forward punch! Move arm way outer front
        armR = Offset(torsoOffset.x + 28f * scaleFactor * faceDir, torsoOffset.y - 4f * scaleFactor)
        
        // Add energy strike circles!
        drawCircle(
            color = accentColor.copy(alpha = 0.8f),
            radius = 12f * scaleFactor,
            center = armR
        )
    } else if (char.actionState == ActionState.KICKING) {
        // Grand roundhouse kick finisher! Lift leg, sweep outwards
        footR = Offset(torsoOffset.x + 28f * scaleFactor * faceDir, torsoOffset.y + 12f * scaleFactor)
        
        // Glowing sweeping sparks
        drawCircle(
            color = mainColor.copy(alpha = 0.9f),
            radius = 16f * scaleFactor,
            center = footR,
            style = Stroke(width = 3f * scaleFactor)
        )
    } else if (char.actionState == ActionState.JUMP_KICKING) {
        // Classic air dive-kick pose! Body angled down, leg pushed forward
        torsoOffset = Offset(headOffset.x - 8f * scaleFactor * faceDir, headOffset.y - 6f * scaleFactor)
        footR = Offset(headOffset.x + 30f * scaleFactor * faceDir, headOffset.y + 22f * scaleFactor)
        
        // Flying streak sparks
        drawLine(
            color = Color.Cyan,
            start = torsoOffset,
            end = footR,
            strokeWidth = 6f * scaleFactor
        )
    } else if (char.actionState == ActionState.SPECIAL) {
        // Mega radial ultimate! Glow full screen circle
        drawCircle(
            brush = Brush.radialGradient(listOf(accentColor, Color.Transparent)),
            radius = 45f * scaleFactor,
            center = headOffset,
            alpha = 0.35f
        )
        drawCircle(
            color = mainColor,
            radius = 35f * scaleFactor,
            center = headOffset,
            style = Stroke(width = 2f * scaleFactor)
        )
    } else if (char.actionState == ActionState.FALLING || char.actionState == ActionState.KNOCKED_OUT) {
        // Faceplant / flying tumble rotation
        torsoOffset = Offset(headOffset.x - 15f * scaleFactor * faceDir, headOffset.y + 10f * scaleFactor)
        armR = Offset(torsoOffset.x - 10f * scaleFactor, torsoOffset.y - 12f * scaleFactor)
        footR = Offset(torsoOffset.x + 20f * scaleFactor * faceDir, torsoOffset.y - 8f * scaleFactor)
    }

    // --- DRAW VECTOR FIGURE ---

    // 1. Head
    val headRadius = 7f * scaleFactor
    val headCenter = Offset(headOffset.x, headOffset.y - 24f * scaleFactor)
    drawCircle(
        color = if (char.isPlayer) mainColor else Color(0xFFDF0000), // Punks have angry red skin silhouettes
        radius = headRadius,
        center = headCenter
    )

    // Sega headband / Punk mohawk style!
    if (char.isPlayer) {
        // Yellow headband of Axel/Adam or Blazes brown crown hair
        val bandanaOffset = Offset(headCenter.x - 8f * scaleFactor * faceDir, headCenter.y - 10f * scaleFactor)
        drawLine(
            color = accentColor,
            start = headCenter,
            end = bandanaOffset,
            strokeWidth = 3f * scaleFactor
        )
    } else {
        // Red punk mohawk spikes!
        val mohawkPath = Path().apply {
            moveTo(headCenter.x - 2f * scaleFactor, headCenter.y - headRadius)
            lineTo(headCenter.x, headCenter.y - headRadius - 8f * scaleFactor)
            lineTo(headCenter.x + 2f * scaleFactor, headCenter.y - headRadius)
        }
        drawPath(mohawkPath, Color.Yellow)
    }

    // 2. Torso (Vest jacket outfit)
    drawLine(
        color = if (char.isPlayer) Color.White else Color(0xFF343A40),
        start = headCenter,
        end = torsoOffset,
        strokeWidth = 8f * scaleFactor
    )
    // Colored jacket layer
    drawLine(
        color = mainColor,
        start = Offset(headCenter.x, headCenter.y + 2f * scaleFactor),
        end = torsoOffset,
        strokeWidth = 5f * scaleFactor
    )

    // 3. Hands (Boxing Gloves or punk bare knuckles)
    drawLine(
        color = Color(0xFF1E2638),
        start = torsoOffset,
        end = armL,
        strokeWidth = strokeWidth
    )
    drawCircle(Color.White, radius = 4f * scaleFactor, center = armL)

    drawLine(
        color = Color(0xFF1E2638),
        start = torsoOffset,
        end = armR,
        strokeWidth = strokeWidth
    )
    drawCircle(
        color = if (char.isPlayer) accentColor else Color.Red,
        radius = 5.5f * scaleFactor,
        center = armR
    )

    // If weapon wielded, draw the pipe/knife crossing their arm overlay!
    if (char.isPlayer && char.weaponEquipped != WeaponType.NONE) {
        val wLen = 22f * scaleFactor
        val wColor = if (char.weaponEquipped == WeaponType.PIPE) Color(0xFFCED4DA) else Color(0xFFEF4444)
        drawLine(
            color = wColor,
            start = Offset(armR.x - wLen * 0.4f * faceDir, armR.y + wLen * 0.4f),
            end = Offset(armR.x + wLen * 0.8f * faceDir, armR.y - wLen * 0.8f),
            strokeWidth = 4f * scaleFactor
        )
    }

    // 4. Legs/Feet
    drawLine(
        color = if (char.isPlayer) Color(0xFF4C6EF5) else Color(0xFF868E96), // blue jeans for leader Axel
        start = torsoOffset,
        end = footL,
        strokeWidth = strokeWidth
    )
    drawCircle(
        color = if (char.isPlayer) Color.White else Color.Black,
        radius = 4.5f * scaleFactor,
        center = footL
    )

    drawLine(
        color = if (char.isPlayer) Color(0xFF4C6EF5) else Color(0xFF868E96),
        start = torsoOffset,
        end = footR,
        strokeWidth = strokeWidth
    )
    drawCircle(
        color = if (char.isPlayer) Color.White else Color.Black,
        radius = 4.5f * scaleFactor,
        center = footR
    )
}

/**
 * Pulse glowing CRT visual effect overlay.
 */
@Composable
fun CrtMonitorOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val height = size.height
        val width = size.width

        // CRT Horizontal scanline grids
        val scanlineSpacing = 6f
        var currentY = 0f
        while (currentY < height) {
            drawLine(
                color = Color(0x1F000000),
                start = Offset(0f, currentY),
                end = Offset(width, currentY),
                strokeWidth = 1.5f
            )
            currentY += scanlineSpacing
        }

        // CRT subtle corner vignette shadows
        val radial = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x60000000)),
            center = Offset(width / 2, height / 2),
            radius = max(width, height) * 0.75f
        )
        drawRect(brush = radial)
    }
}

/**
 * Pulsating Go Indicator guiding player to advance rightwards
 */
@Composable
fun AnimatedPulsingGo(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "go")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "goScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .background(Color(0xE01E020C), RoundedCornerShape(8.dp))
            .border(2.dp, Color(0xFFFF007F), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("go_indicator")
    ) {
        Text(
            text = "GO",
            color = Color(0xFFFF007F),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Arrow right",
            tint = Color(0xFFFF007F),
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Red hazard style warning splash when boss enters active stage arena.
 */
@Composable
fun BossWarningBanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "boss")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bossAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0xD9000000))
            .border(2.dp, Color.Red.copy(alpha = alpha))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Alert icon",
                tint = Color.Red,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "WARNING: MR. X BOSS APPROACHING",
                color = Color.Red,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Alert icon",
                tint = Color.Red,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Dynamic User health overlay & scoring panel (Classic HUD).
 */
@Composable
fun GameHud(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val player = viewModel.player ?: return
    val pTemplate = player.template ?: return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // First Row: Character profile picture, health gauge, scores and lives
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Part: Player Profile container
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Styled Avatar box representing Active leader
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.dp, Color(0xFFFFCC00), RoundedCornerShape(6.dp))
                            .background(Color(0xFF0D1222)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pTemplate.name.take(2).uppercase(),
                            color = Color(0xFFFFCC00),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                    }

                    // Health Progression Gauge bar combo
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = pTemplate.name.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            // Lifes Counter
                            Text(
                                text = "x3",
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Sega Orange/Red segmented lifebar
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .height(12.dp)
                                .background(Color(0xFF2E1911))
                                .border(1.dp, Color.White)
                        ) {
                            val hpRatio = player.hp.toFloat() / player.maxHealth
                            val lifebarColor = if (player.hp > 40) Color(0xFFFFCC00) else Color.Red
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(hpRatio)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                lifebarColor,
                                                lifebarColor.copy(alpha = 0.7f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                // Right Part: Master Arcade score registry
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "1P SCORE",
                        color = Color(0xFFFF1A4B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%06d", viewModel.playerScore),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("score_label")
                    )
                    // High score template
                    Text(
                        text = "STAGE ${viewModel.currentWave}/${viewModel.totalWaves}",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- Multi-Hit Combo banner popups ---
            if (viewModel.comboCount > 1) {
                Box(
                    modifier = Modifier
                        .padding(start = 55.dp)
                        .scale(1.05f)
                        .background(Color(0xE60D0418), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFFFCC00), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${viewModel.comboCount} HIT COMBO! EXCELLENT",
                        color = Color(0xFFFFCC00),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Weapon equip indicator
            if (player.weaponEquipped != WeaponType.NONE) {
                Box(
                    modifier = Modifier
                        .padding(start = 55.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "WEAPON: ${player.weaponEquipped.name}",
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // --- 2. BOSS DYNAMIC HP REGISTRY ---
            if (viewModel.enemies.any { it.name.contains("MR. X") }) {
                val boss = viewModel.enemies.first { it.name.contains("MR. X") }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(Color(0xC0000000), RoundedCornerShape(6.dp))
                        .border(1.5.dp, Color.Red, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = boss.name,
                            color = Color.Red,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "HP: ${boss.hp}/${boss.maxHealth}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(Color(0xFF260408))
                            .border(1.dp, Color.Red)
                    ) {
                        val bossHpRatio = boss.hp.toFloat() / boss.maxHealth
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bossHpRatio)
                                .background(Brush.horizontalGradient(listOf(Color.Red, Color(0xFFFF5C5C))))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tactile Retro Arcade Controller Board (Deck).
 * Places D-pad Joystick controller on the left side,
 * Action cabinet PUNCH, JUMP, SPECIAL keys on the right deck,
 * and contextual instruction tags in between.
 */
@Composable
fun ArcadeDeck(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- LEFT DECK: JOSTICK KNOB PANEL ---
        Box(
            modifier = Modifier
                .size(140.dp)
                .drawBehind {
                    // Outer structural metal socket ring
                    drawCircle(Color(0xFF252C3D), radius = 68.dp.toPx(), style = Stroke(width = 4.dp.toPx()))
                    drawCircle(Color(0xFF141822), radius = 64.dp.toPx())
                    // Inner metallic glowing circle
                    drawCircle(Color(0xFF0E1118), radius = 45.dp.toPx())
                },
            contentAlignment = Alignment.Center
        ) {
            // Analog joystick knob dragging handle
            var joystickOffset by remember { mutableStateOf(Offset.Zero) }
            val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 45.dp.toPx() }

            Box(
                modifier = Modifier
                    .offset(joystickOffset.x.dp, joystickOffset.y.dp)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF1A4B), // Hot retro cherry ball
                                Color(0xFFB30022),
                                Color(0xFF4D0008)
                            )
                        )
                    )
                    .border(1.5.dp, Color.White, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val raw = joystickOffset + Offset(dragAmount.x / 2.5f, dragAmount.y / 2.5f)
                                val distance = sqrt(raw.x * raw.x + raw.y * raw.y)
                                
                                if (distance <= 45f) {
                                    joystickOffset = raw
                                } else {
                                    val angle = atan2(raw.y, raw.x)
                                    joystickOffset = Offset(cos(angle) * 45f, sin(angle) * 45f)
                                }

                                // Convey vector direction values to game velocity loop
                                val normX = joystickOffset.x / 45f
                                val normY = joystickOffset.y / 45f
                                viewModel.movePlayer(normX, normY)
                            },
                            onDragEnd = {
                                joystickOffset = Offset.Zero
                                viewModel.movePlayer(0f, 0f) // stop moving
                            }
                        )
                    }
                    .testTag("joystick_controller")
            )
        }

        // --- MIDDLE PANEL: Weapon Action & Game guide details ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(90.dp)
        ) {
            // Tactile yellow WEAPON/USE Action button
            Button(
                onClick = { viewModel.triggerActionPickup() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9900)),
                shape = CutCornerShape(5.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(width = 80.dp, height = 36.dp)
                    .border(1.5.dp, Color.White, CutCornerShape(5.dp))
                    .testTag("action_pickup_button")
            ) {
                Text(
                    text = "PICK/USE",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Info, contentDescription = "D-pad label", tint = Color.Gray, modifier = Modifier.size(12.dp))
                Text("DRAG STICK TO MOVE", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }

        // --- RIGHT DECK: GIANT MECHANICAL BUTTONS (PUNCH, JUMP, SPECIAL) ---
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Button A: PUNCH (Glowing Pink Red)
            ArcadeButton(
                label = "PUNCH",
                btnCode = "A",
                colorGlow = Color(0xFFFF1A4B),
                onClick = { viewModel.triggerPlayerPunch() },
                modifier = Modifier.testTag("punch_button")
            )

            // 2. Button B: JUMP (Glowing Aqua Blue)
            ArcadeButton(
                label = "JUMP",
                btnCode = "B",
                colorGlow = Color(0xFF00FFCC),
                onClick = { viewModel.triggerPlayerJump() },
                modifier = Modifier.testTag("jump_button")
            )

            // 3. Button C: SPECIAL (Glowing Radioactive Green)
            ArcadeButton(
                label = "SPECIAL",
                btnCode = "C",
                colorGlow = Color(0xFFCCFF00),
                onClick = { viewModel.triggerPlayerSpecial() },
                modifier = Modifier.testTag("special_button")
            )
        }
    }
}

/**
 * Stylized mechanical push-down arcade cabinet button with glowing neon backlighting.
 */
@Composable
fun ArcadeButton(
    label: String,
    btnCode: String,
    colorGlow: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .clickable {
                    onClick()
                }
                .background(
                    if (isPressed) colorGlow.copy(alpha = 0.5f) else Color(0xFF0F121C)
                )
                .border(2.5.dp, colorGlow, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Mechanical inner ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colorGlow.copy(alpha = 0.8f),
                                colorGlow.copy(alpha = 0.3f),
                                Color.Black
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = btnCode,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}
