package com.bugenzhao.mnga.ui.screens.plus

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.model.UnlockStatus
import com.bugenzhao.mnga.ui.screens.misc.fmtL
import com.bugenzhao.mnga.util.Constants
import com.bugenzhao.mnga.util.L
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

/** A paywall product card, standing in for the store's product metadata. */
private data class PlusProduct(
    val id: String,
    val name: String,
    val price: String,
    val description: String,
)

private fun productsFor(status: UnlockStatus): List<PlusProduct> = when (status) {
    UnlockStatus.Lite -> listOf(
        PlusProduct(
            Constants.Plus.trialID,
            "Plus Trial",
            "Free",
            "14-day free trial",
        ),
        PlusProduct(
            Constants.Plus.unlockID,
            "MNGA Plus",
            "-",
            "Lifetime access to every MNGA Plus feature.",
        ),
    )
    is UnlockStatus.Trial -> listOf(
        PlusProduct(
            Constants.Plus.unlockID,
            "MNGA Plus",
            "-",
            "Lifetime access to every MNGA Plus feature.",
        ),
    )
    UnlockStatus.Paid -> emptyList()
}

/**
 * The Plus status the UI should render: the debug override when one is set,
 * otherwise the cached store status. Mirrors [PlusModel.status] so that every
 * screen agrees on what the user is entitled to.
 */
@Composable
fun rememberPlusStatus(): UnlockStatus {
    val override by App.plus.debugOverride.collectAsState()
    val cached by App.plus.cachedStatus.collectAsState()
    return override ?: cached
}

private fun plusFeatureIcon(feature: PlusFeature): ImageVector = when (feature) {
    PlusFeature.CUSTOM_APPEARANCE -> Icons.Filled.Palette
    PlusFeature.MULTI_ACCOUNT -> Icons.Filled.Group
    PlusFeature.TOPIC_HISTORY -> Icons.Filled.History
    PlusFeature.MULTI_FAVORITE -> Icons.Filled.Bookmark
    PlusFeature.AUTHOR_ONLY -> Icons.Filled.Person
    PlusFeature.JUMP -> Icons.Filled.SwapVert
    PlusFeature.RESUME_PROGRESS -> Icons.Filled.RestartAlt
    PlusFeature.BLOCK_CONTENTS -> Icons.Outlined.FrontHand
    PlusFeature.SYNC_FORUMS -> Icons.Filled.Cloud
    PlusFeature.ANONYMOUS -> Icons.Outlined.TheaterComedy
    PlusFeature.NEW_TOPIC -> Icons.Filled.PostAdd
    PlusFeature.HOT_TOPIC -> Icons.Filled.LocalFireDepartment
    PlusFeature.SHORT_MESSAGE -> Icons.AutoMirrored.Filled.Chat
}

/**
 * The paywall sheet, ported from `Views/PlusView.swift`. Presents the status
 * header, product cards, restore/redeem actions and the full feature list.
 * Purchases are routed through [PlusModel.billing] when wired; without a
 * billing bridge the buttons report that purchases are unavailable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paywall = App.plus

    val trusted by paywall.isStatusTrusted.collectAsState()
    val status = rememberPlusStatus()

    var purchasingId by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun purchasesUnavailable() {
        Toast.makeText(context, L.str(context, "Purchases unavailable"), Toast.LENGTH_SHORT).show()
    }

    fun refreshStatus() {
        scope.launch {
            val billing = paywall.billing
            if (billing == null) {
                paywall.updateStatus(paywall.cachedStatus.value)
                return@launch
            }
            val fetched = runCatching { billing.fetchStatus() }.getOrNull()
            if (fetched != null) paywall.updateStatus(fetched)
            else paywall.updateStatus(paywall.cachedStatus.value)
        }
    }

    fun purchase(product: PlusProduct) {
        if (purchasingId != null || restoring) return
        val billing = paywall.billing
        if (billing == null) {
            purchasesUnavailable()
            return
        }
        purchasingId = product.id
        errorMessage = null
        scope.launch {
            val ok = runCatching { billing.purchase(product.id) }.getOrDefault(false)
            if (ok) {
                val fetched = runCatching { billing.fetchStatus() }.getOrNull()
                if (fetched != null) paywall.updateStatus(fetched)
            } else {
                errorMessage = L.str(context, "We couldn't verify your purchase. Please try again.")
            }
            purchasingId = null
        }
    }

    fun restore() {
        if (restoring || purchasingId != null) return
        val billing = paywall.billing
        if (billing == null) {
            purchasesUnavailable()
            return
        }
        restoring = true
        errorMessage = null
        scope.launch {
            val ok = runCatching { billing.restore() }.getOrDefault(false)
            if (!ok) {
                errorMessage = L.str(context, "We couldn't verify your purchase. Please try again.")
            } else {
                refreshStatus()
            }
            restoring = false
        }
    }

    // Force a trusted status on appear, like `paywall.updateStatus()`.
    LaunchedEffect(Unit) { refreshStatus() }

    val inProgress = purchasingId != null || restoring

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (!trusted) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Header(inProgress = inProgress)

                StatusCard(status = status)

                if (!status.isPaid) {
                    ProductSection(
                        status = status,
                        purchasingId = purchasingId,
                        onPurchase = { purchase(it) },
                        restoring = restoring,
                        onRestore = { restore() },
                        onRedeem = {
                            if (paywall.billing == null) purchasesUnavailable() else restore()
                        },
                    )
                }

                errorMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                FeatureList()
            }
        }
    }
}

@Composable
private fun Header(inProgress: Boolean) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                L.str(context, "Unlock Plus"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                L.str(context, "Plus Explanation"),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (inProgress) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun StatusCard(status: UnlockStatus) {
    val context = LocalContext.current
    val card = Modifier.fillMaxWidth()
    when (status) {
        UnlockStatus.Paid -> {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                        )
                        Text(
                            L.str(context, "Plus Unlocked"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        L.str(context, "Plus Thanks"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        is UnlockStatus.Trial -> {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (status.trialValid == true) {
                        Text(
                            L.str(context, "You're enjoying a Plus trial"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val date = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                            .format(status.expiration)
                        Text(
                            fmtL(context, "Trial ends on %@", date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            L.str(context, "Your Plus trial has expired"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        UnlockStatus.Lite -> {}
    }
}

@Composable
private fun ProductSection(
    status: UnlockStatus,
    purchasingId: String?,
    onPurchase: (PlusProduct) -> Unit,
    restoring: Boolean,
    onRestore: () -> Unit,
    onRedeem: () -> Unit,
) {
    val context = LocalContext.current
    val products = productsFor(status)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (products.isEmpty()) {
            Text(
                L.str(context, "Products unavailable. Please try again later."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            products.forEachIndexed { index, product ->
                ProductCard(
                    product = product,
                    isPreferred = index == 0,
                    isPurchasing = purchasingId == product.id,
                    onClick = { onPurchase(product) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            TextButton(onClick = onRestore, enabled = !restoring) {
                if (restoring) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(L.str(context, "Restore Purchases"))
            }
            TextButton(onClick = onRedeem) {
                Text(L.str(context, "Redeem Code"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(
    product: PlusProduct,
    isPreferred: Boolean,
    isPurchasing: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border =
            if (isPreferred) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.5.dp, Color.Transparent)
            },
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = !isPurchasing,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (isPurchasing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    product.price,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                product.description,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isPreferred) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "All Plus Features": every feature plus the future-features note. */
@Composable
private fun FeatureList() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            L.str(context, "All Plus Features"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        PlusFeature.entries.forEach { feature ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    plusFeatureIcon(feature),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        L.str(context, feature.label),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        L.str(context, feature.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    L.str(context, "More Features in the Future"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    L.str(context, "Plus More Feature"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
