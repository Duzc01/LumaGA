package com.bugenzhao.mnga.ui.screens.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.util.L

/** 金属货币色（不随主题切换，保持金属质感）。 */
private val Gold = Color(0xFFC9A227)
private val Silver = Color(0xFF9AA3AB)
private val Copper = Color(0xFFB87333)
private val NCoinBlue = Color(0xFF4E7CD9)

/**
 * 签到页：连续/累计签到天数 + 金币/银币/铜币/N币 + 签到动作。
 * 签名视觉是"砖墙"——连续天数渲染成一排金属砖，呼应 NGA 的签到墙。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockInScreen(navigator: Navigator? = null) {
    val context = LocalContext.current
    val stats by App.currentUser.clockInStats.collectAsState()
    val clockedIn by App.currentUser.todayClockedIn.collectAsState()

    // 打开页面即刷新状态与统计（签到接口幂等：已签只查统计）。
    LaunchedEffect(Unit) {
        App.currentUser.refreshTodayClockIn()
        App.currentUser.clockInOnce()
    }

    BackHandler(enabled = navigator != null && navigator.size > 1) { navigator?.pop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "Clock In")) },
                navigationIcon = {
                    IconButton(onClick = { navigator?.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))

            // 连续签到天数：大数字 + 砖墙。
            Text(
                L.str(context, "Clock Streak"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            val continued = stats?.continuedDays ?: 0
            Text(
                continued.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = if (clockedIn) Gold else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                L.str(context, "Clock Streak Days"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            // 砖墙：已签的金砖 + 今日描边砖（未签时）+ 剩余灰砖。
            ClockBrickWall(continued = continued, clockedIn = clockedIn)
            Spacer(Modifier.height(14.dp))
            Text(
                L.str(context, "Clock Total Days", stats?.totalDays ?: 0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            // 货币四宫格。
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    CoinRow(
                        coins = listOf(
                            CoinBadge(Gold, L.str(context, "Clock Gold"), stats?.gold ?: 0),
                            CoinBadge(Silver, L.str(context, "Clock Silver"), stats?.silver ?: 0),
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    CoinRow(
                        coins = listOf(
                            CoinBadge(Copper, L.str(context, "Clock Copper"), stats?.copper ?: 0),
                            CoinBadge(NCoinBlue, L.str(context, "Clock N Coin"), stats?.nCoins ?: 0),
                        ),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // 签到主按钮。
            if (clockedIn) {
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(L.str(context, "Clocked In"))
                }
            } else {
                Button(
                    onClick = { App.currentUser.clockInOnce() },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(L.str(context, "Clock In"), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 一枚货币徽章：金属色圆点 + 名称 + 数值。 */
private data class CoinBadge(val color: Color, val label: String, val value: Int)

@Composable
private fun CoinRow(coins: List<CoinBadge>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        coins.forEach { coin ->
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(coin.color, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        coin.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        coin.value.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}

/** 砖墙：14 格——前 [continued] 格金砖，未签时今日格金色描边，其余灰砖。 */
@Composable
private fun ClockBrickWall(continued: Int, clockedIn: Boolean) {
    val goldBrush = Brush.linearGradient(
        listOf(Gold.copy(alpha = 0.85f), Gold.copy(alpha = 1f)),
    )
    val grey = MaterialTheme.colorScheme.outlineVariant
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(14) { i ->
            val brick = Modifier
                .size(width = 22.dp, height = 30.dp)
                .then(
                    when {
                        // 今日未签：金色描边砖，等待点亮。
                        !clockedIn && i == continued -> Modifier.border(
                            1.5.dp, Gold, RoundedCornerShape(5.dp),
                        )
                        i < continued -> Modifier.background(goldBrush, RoundedCornerShape(5.dp))
                        else -> Modifier.border(1.dp, grey, RoundedCornerShape(5.dp))
                    },
                )
            Box(brick, contentAlignment = Alignment.Center) {
                if (!clockedIn && i == continued) {
                    Text(
                        "今",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
