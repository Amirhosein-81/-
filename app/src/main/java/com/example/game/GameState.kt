package com.example.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

enum class ScreenState {
    MENU,
    CHARACTER_SELECT,
    CHARACTER_PROFILES,
    GAMEPLAY,
    GAME_OVER,
    VICTORY
}

enum class ActionState {
    IDLE,
    WALKING,
    PUNCHING_1,
    PUNCHING_2,
    KICKING,
    SPECIAL,
    JUMPING,
    JUMP_KICKING,
    HIT_STUN,
    FALLING,
    KNOCKED_OUT
}

enum class WeaponType {
    NONE,
    PIPE,
    DAGGER
}

data class CharacterTemplate(
    val name: String,
    val description: String,
    val power: Int, // Out of 5
    val speed: Int,
    val jump: Int,
    val specialMoveName: String,
    val bio: String,
    val primaryColorHex: String,
    val accentColorHex: String
)

data class Particle(
    val text: String, // "HIT!", "BAM!", "15", "CRITICAL!", "KO!"
    var x: Float,
    var y: Float,
    val isDamage: Boolean = false,
    var lifespan: Int = 18, // ticks
    val vx: Float = (Math.random() * 4 - 2).toFloat(),
    val vy: Float = (Math.random() * -3 - 2).toFloat(),
    val id: Long = Math.random().hashCode().toLong()
)

data class GroundItem(
    var x: Float,
    var y: Float,
    val itemType: ItemType,
    var isBreakableBarrel: Boolean = false,
    var health: Int = 1, // Barrel takes 1 hit to break
    val id: Long = Math.random().hashCode().toLong()
)

enum class ItemType {
    ROAST_CHICKEN, // Full health
    APPLE,         // Partial health
    PIPE_WEAPON,   // Pickup weapon
    DAGGER_WEAPON  // Pickup weapon
}

class Combatant(
    val name: String,
    var isPlayer: Boolean,
    maxHp: Int,
    var speed: Float,
    var template: CharacterTemplate? = null,
    val id: Long = Math.random().hashCode().toLong()
) {
    var x by mutableStateOf(100f)
    var y by mutableStateOf(160f) // Represents depth coordinate (120f to 220f)
    var z by mutableStateOf(0f)   // Height coordinate during jumps (0f is floor)

    var hp by mutableStateOf(maxHp)
    val maxHealth = maxHp

    var actionState by mutableStateOf(ActionState.IDLE)
    var isFacingRight by mutableStateOf(true)

    // Physics
    var vx by mutableStateOf(0f)
    var vy by mutableStateOf(0f)
    var vz by mutableStateOf(0f)

    var stateTicksLeft by mutableStateOf(0)
    var currentComboIndex by mutableStateOf(0) // Punch 1 -> 2 -> Kick Finish
    var weaponEquipped by mutableStateOf(WeaponType.NONE)

    val isVulnerable: Boolean
        get() = actionState != ActionState.SPECIAL && actionState != ActionState.FALLING && actionState != ActionState.KNOCKED_OUT

    fun heal(amount: Int) {
        hp = (hp + amount).coerceAtMost(maxHealth)
    }

    fun takeDamage(amount: Int): Boolean {
        if (!isVulnerable) return false
        hp = (hp - amount).coerceAtMost(maxHealth)
        if (hp <= 0) {
            hp = 0
            actionState = ActionState.FALLING
            vz = 8f  // fly up slightly on final blow
            stateTicksLeft = 24
            ArcadeSynth.playKO()
            return true
        } else {
            actionState = ActionState.HIT_STUN
            stateTicksLeft = 10
            vz = 0f
            ArcadeSynth.playPunch()
            return false
        }
    }
}

class GameViewModel : ViewModel() {
    // Nav / Page management
    var currentScreen by mutableStateOf(ScreenState.MENU)
    val characters = listOf(
        CharacterTemplate(
            name = "Axel Stone",
            description = "The Balanced Leader",
            power = 4,
            speed = 3,
            jump = 3,
            specialMoveName = "Dragon Wing",
            bio = "Ex-police officer with fists of fire. Martial artist seeking justice in the dark streets.",
            primaryColorHex = "FF3366FF", // Royal Blue
            accentColorHex = "FFFFFF00"  // Neo Yellow
        ),
        CharacterTemplate(
            name = "Blaze Fielding",
            description = "The Nimble Fighter",
            power = 3,
            speed = 5,
            jump = 4,
            specialMoveName = "Vertical Slash",
            bio = "Judo expert and gorgeous dancer. Blazing speed and acrobatic vertical kick combos.",
            primaryColorHex = "FFFF1A4B", // Hot Crimson
            accentColorHex = "FFFFFFFF"  // Pure White
        ),
        CharacterTemplate(
            name = "Adam Hunter",
            description = "The Heavy Striker",
            power = 5,
            speed = 3,
            jump = 4,
            specialMoveName = "Uppercut Rush",
            bio = "Professional boxer with massive impact. Powerful reaches and extreme jump dropkicks.",
            primaryColorHex = "FF00E676", // Electric Green
            accentColorHex = "FFFF4D00"  // Burn Orange
        ),
        CharacterTemplate(
            name = "Max Thunder",
            description = "The Wrestling Giant",
            power = 5,
            speed = 2,
            jump = 2,
            specialMoveName = "Thunder Slide",
            bio = "Colossal heavyweight champion. Massive grapples and slow but devatasting specials.",
            primaryColorHex = "FFFF9F00", // Amber Glow
            accentColorHex = "FF9D00FF"  // Cyber Purple
        )
    )

    var selectedCharacterIndex by mutableStateOf(0)
    var selectedProfileIndex by mutableStateOf(0)

    // High Scores State
    val highScores = mutableStateListOf(
        Pair("AXEL", 48200),
        Pair("BLAZE", 39900),
        Pair("ADAM", 31400)
    )

    // Active Game Setup
    var player by mutableStateOf<Combatant?>(null)
    val enemies = mutableStateListOf<Combatant>()
    val particles = mutableStateListOf<Particle>()
    val groundItems = mutableStateListOf<GroundItem>()

    // Camera view offset (Parallax)
    var cameraX by mutableStateOf(0f)
    var stageWidth = 3000f
    val stageHeight = 250f // Playable ground street height depth

    // Combat tracking
    var playerScore by mutableStateOf(0)
    var currentWave by mutableStateOf(1)
    val totalWaves = 4
    var waveInTransition by mutableStateOf(false)
    var showGoIndicator by mutableStateOf(false)
    var screenShakeAmount by mutableStateOf(0f)

    // Combo Chain Meter
    var comboCount by mutableStateOf(0)
    var comboTimer by mutableStateOf(0)

    private var gameLoopJob: Job? = null

    init {
        // Play classic retro chiptune melody on launch to create nostalgia!
        ArcadeSynth.playThemeChp()
    }

    fun startNewGame(charTemplate: CharacterTemplate) {
        player = Combatant(
            name = charTemplate.name,
            isPlayer = true,
            maxHp = 100,
            speed = 4.5f,
            template = charTemplate
        ).apply {
            x = 120f
            y = 160f
        }

        enemies.clear()
        particles.clear()
        groundItems.clear()
        cameraX = 0f
        playerScore = 0
        currentWave = 1
        waveInTransition = false
        showGoIndicator = false
        comboCount = 0
        comboTimer = 0

        // Populate introductory level items (Wooden barrels with goodies inside!)
        groundItems.add(GroundItem(x = 420f, y = 140f, itemType = ItemType.APPLE, isBreakableBarrel = true))
        groundItems.add(GroundItem(x = 650f, y = 180f, itemType = ItemType.PIPE_WEAPON, isBreakableBarrel = true))
        groundItems.add(GroundItem(x = 1100f, y = 150f, itemType = ItemType.ROAST_CHICKEN, isBreakableBarrel = true))
        groundItems.add(GroundItem(x = 1750f, y = 160f, itemType = ItemType.DAGGER_WEAPON, isBreakableBarrel = true))

        spawnWave(1)
        currentScreen = ScreenState.GAMEPLAY
        startGameLoop()
    }

    private fun spawnWave(wave: Int) {
        enemies.clear()
        val pX = player?.x ?: 120f
        
        when (wave) {
            1 -> {
                // Intro wave: 2 normal Thugs ('Galsia' punk)
                enemies.add(Combatant("Galsia A", false, 40, 2.5f).apply { x = pX + 500f; y = 140f })
                enemies.add(Combatant("Galsia B", false, 40, 2.5f).apply { x = pX + 680f; y = 190f })
            }
            2 -> {
                // Wave 2: 3 thugs with intermediate mechanics (Signal kicks)
                enemies.add(Combatant("Galsia C", false, 45, 2.6f).apply { x = pX + 550f; y = 150f })
                enemies.add(Combatant("Signal A", false, 55, 3.2f).apply { x = pX + 700f; y = 180f })
                enemies.add(Combatant("Knife-Thug", false, 35, 2.8f).apply { x = pX + 400f; y = 130f })
            }
            3 -> {
                // Wave 3: Ambush from both sides!
                enemies.add(Combatant("Signal B", false, 60, 3.0f).apply { x = pX - 250f; y = 170f })
                enemies.add(Combatant("Galsia D", false, 50, 2.6f).apply { x = pX + 600f; y = 150f })
                enemies.add(Combatant("Signal C", false, 60, 3.3f).apply { x = pX + 800f; y = 190f })
            }
            4 -> {
                // Final Wave: Mr. X Boss battle!
                val boss = Combatant("MR. X (BOSS)", false, 250, 3.8f).apply {
                    x = pX + 650f
                    y = 165f
                }
                enemies.add(boss)
                // Add two boss bodyguard thugs
                enemies.add(Combatant("Elite Slasher", false, 65, 3.4f).apply { x = pX + 500f; y = 130f })
                enemies.add(Combatant("Elite Bouncer", false, 75, 2.5f).apply { x = pX - 200f; y = 180f })
            }
        }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (currentScreen == ScreenState.GAMEPLAY) {
                delay(30) // ~33 FPS tick loop
                updateGameEngine()
            }
        }
    }

    private fun updateGameEngine() {
        val p = player ?: return

        // Manage screenshake decay
        if (screenShakeAmount > 0) {
            screenShakeAmount = (screenShakeAmount - 0.5f).coerceAtLeast(0f)
        }

        // Manage combo meter decay
        if (comboTimer > 0) {
            comboTimer--
            if (comboTimer == 0) {
                comboCount = 0
            }
        }

        // 1. Update Player State Ticks and Animations
        if (p.stateTicksLeft > 0) {
            p.stateTicksLeft--
            if (p.stateTicksLeft == 0) {
                if (p.actionState == ActionState.KNOCKED_OUT) {
                    currentScreen = ScreenState.GAME_OVER
                } else if (p.actionState == ActionState.FALLING && p.hp <= 0) {
                    p.actionState = ActionState.KNOCKED_OUT
                    p.stateTicksLeft = 40
                } else {
                    p.actionState = ActionState.IDLE
                }
            }
        }

        // Player Jump Physics
        if (p.actionState == ActionState.JUMPING || p.actionState == ActionState.JUMP_KICKING) {
            p.z += p.vz
            p.vz -= 0.65f // gravity pull
            if (p.z <= 0) {
                p.z = 0f
                p.vz = 0f
                if (p.actionState == ActionState.JUMP_KICKING) {
                    p.actionState = ActionState.IDLE
                } else if (p.actionState == ActionState.JUMPING) {
                    p.actionState = ActionState.IDLE
                }
            }
            // Move horizontally while in mid-air
            p.x += p.vx
            p.y = (p.y + p.vy).coerceIn(120f, 220f)
        } else if (p.actionState == ActionState.WALKING) {
            p.x += p.vx
            p.y = (p.y + p.vy).coerceIn(120f, 220f)
        } else if (p.actionState == ActionState.FALLING) {
            p.z += p.vz
            p.vz -= 0.6f
            if (p.z <= 0) {
                p.z = 0f
                p.vz = 0f
                if (p.hp <= 0) {
                    p.actionState = ActionState.KNOCKED_OUT
                    p.stateTicksLeft = 50
                } else if (p.stateTicksLeft == 0) {
                    p.actionState = ActionState.IDLE
                }
            }
            p.x += if (p.isFacingRight) -3f else 3f
        }

        // Constrain player within screen boundaries or level limit
        p.x = p.x.coerceIn(cameraX, cameraX + 800f)

        // Camera Autoscroll Parallax following player
        val targetCamX = p.x - 300f
        if (targetCamX > cameraX && !showGoIndicator) {
            val potentialCamX = (cameraX + (targetCamX - cameraX) * 0.15f)
            // Cap camera if we have enemies alive in the wave!
            if (enemies.isEmpty() || currentWave == totalWaves) {
                cameraX = potentialCamX.coerceIn(0f, stageWidth - 850f)
            } else {
                // Block screen scrolling beyond the active wave arena boundary
                val blockLimit = currentWave * 650f
                if (potentialCamX < blockLimit) {
                    cameraX = potentialCamX
                }
            }
        }

        // 2. Clear out completely empty waves and progress!
        if (enemies.isEmpty() && !waveInTransition) {
            if (currentWave < totalWaves) {
                showGoIndicator = true
                // If player walks past the right edge area of active stage boundary, trigger next wave!
                val threshold = currentWave * 650f + 250f
                if (p.x >= threshold) {
                    waveInTransition = true
                    viewModelScope.launch {
                        showGoIndicator = false
                        particles.add(Particle("STAGE CLEAR wave $currentWave!", p.x, p.y - 80f))
                        delay(1200)
                        currentWave++
                        spawnWave(currentWave)
                        waveInTransition = false
                    }
                }
            } else {
                // Victory Condition! Game Completed
                currentScreen = ScreenState.VICTORY
                // Record Score in high scores
                val idx = highScores.indexOfFirst { it.first == p.name.uppercase() }
                if (idx != -1) {
                    if (playerScore > highScores[idx].second) {
                        highScores[idx] = Pair(p.name.uppercase(), playerScore)
                    }
                } else {
                    highScores.add(Pair(p.name.uppercase(), playerScore))
                }
            }
        }

        // 3. Update Thugs AI and combat states
        for (e in ArrayList(enemies)) {
            if (e.hp <= 0 && e.actionState == ActionState.KNOCKED_OUT) {
                enemies.remove(e)
                playerScore += if (e.name.contains("MR. X")) 10000 else 500
                particles.add(Particle("+${if (e.name.contains("MR. X")) 10000 else 500}", e.x, e.y - 50f))
                continue
            }

            // Update Enemy status ticks
            if (e.stateTicksLeft > 0) {
                e.stateTicksLeft--
                if (e.stateTicksLeft == 0) {
                    if (e.actionState == ActionState.FALLING && e.hp <= 0) {
                        e.actionState = ActionState.KNOCKED_OUT
                        e.stateTicksLeft = 1
                    } else {
                        e.actionState = ActionState.IDLE
                    }
                }
            }

            // Handle falling physics for enemies
            if (e.actionState == ActionState.FALLING) {
                e.z += e.vz
                e.vz -= 0.65f
                if (e.z <= 0) {
                    e.z = 0f
                    e.vz = 0f
                    if (e.hp <= 0) {
                        e.actionState = ActionState.KNOCKED_OUT
                        e.stateTicksLeft = 1
                    } else if (e.stateTicksLeft == 0) {
                        e.actionState = ActionState.IDLE
                    }
                }
                e.x += if (e.isFacingRight) -4f else 4f
                e.x = e.x.coerceIn(0f, stageWidth)
                continue
            }

            if (e.actionState == ActionState.HIT_STUN) {
                continue // Cant move during stun
            }

            // Pathfinding AI towards Player
            val dx = p.x - e.x
            val dy = p.y - e.y
            val distance = abs(dx)

            e.isFacingRight = dx > 0

            // If close enough in depth and distance, unleash thug strike!
            if (distance < seventyDpToPx() && abs(dy) < twentyFiveDpToPx() && p.isVulnerable) {
                // Strike AI!
                e.actionState = ActionState.PUNCHING_1
                e.stateTicksLeft = 12
                // Roll probability of hitting player
                if (Math.random() < 0.65) {
                    // Check if player didn't jump high!
                    if (p.z < 25f) {
                        viewModelScope.launch {
                            delay(180) // visual wind up delay
                            val isFinalKO = p.takeDamage(10)
                            particles.add(Particle("-10", p.x, p.y - 60f, isDamage = true))
                            screenShakeAmount = 5f
                            comboCount = 0 // Break combo
                            if (isFinalKO) {
                                screenShakeAmount = 15f
                            }
                        }
                    }
                }
            } else {
                // Simulating thug movement path
                e.actionState = ActionState.WALKING
                val moveSpeed = e.speed
                e.x += if (dx > 0) moveSpeed else -moveSpeed
                e.y += if (dy > 0) (moveSpeed * 0.4f) else (-moveSpeed * 0.4f)
                e.y = e.y.coerceIn(120f, 220f)
            }
        }

        // 4. Particle Tick animation
        for (part in ArrayList(particles)) {
            part.lifespan--
            part.x += part.vx
            part.y += part.vy
            if (part.lifespan <= 0) {
                particles.remove(part)
            }
        }
    }

    // Dynamic dp to px estimation for simulation calculations
    private fun seventyDpToPx() = 120f
    private fun twentyFiveDpToPx() = 40f

    // Action Methods triggered by hardware/UI Buttons
    fun movePlayer(dirX: Float, dirY: Float) {
        val p = player ?: return
        if (p.actionState == ActionState.HIT_STUN || p.actionState == ActionState.FALLING || p.actionState == ActionState.KNOCKED_OUT) return

        if (dirX != 0f || dirY != 0f) {
            p.actionState = if (p.z > 0) p.actionState else ActionState.WALKING
            p.vx = dirX * p.speed
            p.vy = dirY * (p.speed * 0.5f)
            if (dirX != 0f) {
                p.isFacingRight = dirX > 0
            }
        } else {
            p.vx = 0f
            p.vy = 0f
            if (p.actionState == ActionState.WALKING) {
                p.actionState = ActionState.IDLE
            }
        }
    }

    fun triggerPlayerPunch() {
        val p = player ?: return
        if (p.actionState == ActionState.HIT_STUN || p.actionState == ActionState.FALLING || p.actionState == ActionState.KNOCKED_OUT) return

        // If in-air, execute flying double kick!
        if (p.z > 15f) {
            p.actionState = ActionState.JUMP_KICKING
            p.stateTicksLeft = 14
            ArcadeSynth.playPunch()
            performPlayerAttack(damage = 22, isKick = true, range = 130f)
            return
        }

        // Standard grounding combos!
        val nextState: ActionState
        val isHeavyFinish: Boolean
        val damageApplied: Int

        when (p.currentComboIndex) {
            0 -> {
                nextState = ActionState.PUNCHING_1
                isHeavyFinish = false
                damageApplied = 12
                p.currentComboIndex = 1
            }
            1 -> {
                nextState = ActionState.PUNCHING_2
                isHeavyFinish = false
                damageApplied = 15
                p.currentComboIndex = 2
            }
            else -> {
                nextState = ActionState.KICKING
                isHeavyFinish = true
                damageApplied = 28
                p.currentComboIndex = 0 // Reset
            }
        }

        p.actionState = nextState
        p.stateTicksLeft = 10
        ArcadeSynth.playPunch()
        performPlayerAttack(damageApplied, isKick = isHeavyFinish, isFinish = isHeavyFinish)
    }

    private fun performPlayerAttack(damage: Int, isKick: Boolean, range: Float = 110f, isFinish: Boolean = false) {
        val p = player ?: return
        var hitAny = false

        // Check weapon boosts
        val finalDamage = when (p.weaponEquipped) {
            WeaponType.PIPE -> damage + 14
            WeaponType.DAGGER -> damage + 22
            else -> damage
        }
        val finalRange = if (p.weaponEquipped != WeaponType.NONE) range + 40f else range

        // 1. Hit dynamic level barrels or boxes
        for (item in ArrayList(groundItems)) {
            val dist = abs(p.x - item.x)
            val vDepth = abs(p.y - item.y)
            if (dist < finalRange && vDepth < 35f && p.z < 35f) {
                if (item.isBreakableBarrel) {
                    item.health--
                    if (item.health <= 0) {
                        groundItems.remove(item)
                        // Shatter barrel and spawn the actual pickup item on the floor!
                        groundItems.add(GroundItem(x = item.x, y = item.y, itemType = item.itemType, isBreakableBarrel = false))
                        particles.add(Particle("CRASH!", item.x, item.y - 40f))
                        screenShakeAmount = 4f
                        ArcadeSynth.playWeaponHit()
                    }
                    hitAny = true
                }
            }
        }

        // 2. Hit actual thug enemies
        for (e in enemies) {
            // Must align horizontally AND depth Y coordinate, and height Z coordinate must match
            val checkX = if (p.isFacingRight) (e.x - p.x) else (p.x - e.x)
            val depthDiff = abs(p.y - e.y)
            val heightDiff = abs(p.z - e.z)

            // Attack forward check (only hits in facing directions!)
            if (checkX in 0f..finalRange && depthDiff < 30f && heightDiff < 40f) {
                // Connect hit!
                hitAny = true
                val wasWeapon = p.weaponEquipped != WeaponType.NONE
                
                if (wasWeapon) {
                    ArcadeSynth.playWeaponHit()
                } else {
                    ArcadeSynth.playPunch()
                }

                // Apply direct physical properties
                val isThugFalls = isFinish || isKick || wasWeapon
                if (isThugFalls) {
                    e.actionState = ActionState.FALLING
                    e.vz = if (wasWeapon) 12f else 7f // flying air roll!
                    e.stateTicksLeft = 20
                    e.hp = max(0, e.hp - finalDamage)
                    screenShakeAmount = if (wasWeapon) 11f else 8f
                    particles.add(Particle("BAM!", e.x, e.y - 65f))
                    particles.add(Particle("-$finalDamage", e.x - 20f, e.y - 85f, isDamage = true))
                } else {
                    e.takeDamage(finalDamage)
                    particles.add(Particle("HIT!", e.x, e.y - 50f))
                    particles.add(Particle("-$finalDamage", e.x - 15f, e.y - 70f, isDamage = true))
                }

                // Tick up cumulative combo multipliers
                comboCount++
                comboTimer = 60 // 2 seconds threshold
                playerScore += 100 * comboCount
            }
        }

        if (hitAny) {
            screenShakeAmount = if (screenShakeAmount == 0f) 2f else screenShakeAmount
        }
    }

    fun triggerPlayerJump() {
        val p = player ?: return
        if (p.actionState == ActionState.HIT_STUN || p.actionState == ActionState.FALLING || p.actionState == ActionState.KNOCKED_OUT) return

        if (p.z == 0f) {
            p.actionState = ActionState.JUMPING
            p.vz = 8.5f + (p.template?.jump?.toFloat() ?: 3f) * 0.4f // Stat jump scaler
            ArcadeSynth.playJump()
        }
    }

    /**
     * Wipes out surrounding thugs inside a shockwave. Costs player 12HP on usage.
     */
    fun triggerPlayerSpecial() {
        val p = player ?: return
        if (p.actionState == ActionState.HIT_STUN || p.actionState == ActionState.FALLING || p.actionState == ActionState.KNOCKED_OUT) return
        
        // Cost 12 HP penalty
        if (p.hp > 15) {
            p.hp -= 12
        } else {
            p.hp = 1 // scale to minimum 1 to prevent self gameover
        }

        p.actionState = ActionState.SPECIAL
        p.stateTicksLeft = 18
        screenShakeAmount = 18f
        ArcadeSynth.playSpecial()

        particles.add(Particle("${p.template?.specialMoveName?.uppercase()}!", p.x, p.y - 80f))

        // Blast radius hits every thug on screen within broad proximity!
        for (e in enemies) {
            val dist = abs(p.x - e.x)
            val dY = abs(p.y - e.y)
            if (dist < 180f && dY < 55f) {
                e.actionState = ActionState.FALLING
                e.vz = 10f
                e.stateTicksLeft = 22
                e.hp = max(0, e.hp - 35) // Heavy force
                particles.add(Particle("-35", e.x, e.y - 65f, isDamage = true))
                playerScore += 300
            }
        }
    }

    /**
     * Contextual pickup/throw action button.
     * Picks up knives, pipes or heals with dropped items on the floor.
     */
    fun triggerActionPickup() {
        val p = player ?: return
        if (p.actionState == ActionState.HIT_STUN || p.actionState == ActionState.FALLING || p.actionState == ActionState.KNOCKED_OUT) return

        // Search closest items in proximity
        val closestItem = groundItems.firstOrNull { item ->
            !item.isBreakableBarrel && abs(p.x - item.x) < 55f && abs(p.y - item.y) < 25f
        }

        if (closestItem != null) {
            groundItems.remove(closestItem)
            when (closestItem.itemType) {
                ItemType.ROAST_CHICKEN -> {
                    p.heal(100)
                    particles.add(Particle("ROAST CHICKEN! HP FULL", p.x, p.y - 70f))
                    ArcadeSynth.playCoin()
                }
                ItemType.APPLE -> {
                    p.heal(40)
                    particles.add(Particle("APPLE HEALTH +40", p.x, p.y - 70f))
                    ArcadeSynth.playCoin()
                }
                ItemType.PIPE_WEAPON -> {
                    p.weaponEquipped = WeaponType.PIPE
                    particles.add(Particle("STEEL PIPE EQUIP!", p.x, p.y - 70f))
                    ArcadeSynth.playCoin()
                }
                ItemType.DAGGER_WEAPON -> {
                    p.weaponEquipped = WeaponType.DAGGER
                    particles.add(Particle("MILITARY DAGGER EQUIP!", p.x, p.y - 70f))
                    ArcadeSynth.playCoin()
                }
            }
        }
    }
}
