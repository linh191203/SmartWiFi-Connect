package com.example.smartwificonnect.feature.scanimage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private data class PickerImageItem(
    val id: Int,
    val gradient: List<Color>,
)

private data class PickerBottomTab(
    val label: String,
    val icon: ImageVector,
)

private val PickerBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF7F9FC)
private val PickerSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color(0xFFF8F9FC)
private val PickerTitle: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF222630)
private val PickerMuted: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF707788)
private val PickerBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF5A63F5)

@Composable
fun ImagePickerScreen(
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    val todayItems = listOf(
        PickerImageItem(1, listOf(Color(0xFFDFD4EA), Color(0xFFB9C6E3))),
        PickerImageItem(2, listOf(Color(0xFFA7C38B), Color(0xFF658C4D))),
        PickerImageItem(3, listOf(Color(0xFF775636), Color(0xFF2E2A26))),
        PickerImageItem(4, listOf(Color(0xFFA7C58A), Color(0xFF61834A))),
        PickerImageItem(5, listOf(Color(0xFFD0C1B0), Color(0xFF6D5843))),
        PickerImageItem(6, listOf(Color(0xFFD1CEC8), Color(0xFF908B83))),
        PickerImageItem(7, listOf(Color(0xFFBD7B4F), Color(0xFF3E2E22))),
        PickerImageItem(8, listOf(Color(0xFF9A8B7C), Color(0xFF3A2F32))),
        PickerImageItem(9, listOf(Color(0xFFABA9AA), Color(0xFF5D6266))),
        PickerImageItem(10, listOf(Color(0xFFC1D8A2), Color(0xFF577743))),
        PickerImageItem(11, listOf(Color(0xFFC4B49E), Color(0xFF5B4D3D))),
        PickerImageItem(12, listOf(Color(0xFF9EA6AE), Color(0xFF434A52))),
    )
    val yesterdayItems = listOf(
        PickerImageItem(13, listOf(Color(0xFFE9CCA2), Color(0xFFD39F62))),
        PickerImageItem(14, listOf(Color(0xFFDACFAF), Color(0xFF9E8A69))),
        PickerImageItem(15, listOf(Color(0xFF96D0F3), Color(0xFFE4CFAD))),
    )

    var selectedImageId by remember { mutableStateOf<Int?>(null) }

    val canContinue = selectedImageId != null

    Scaffold(
        containerColor = PickerBackground,
        topBar = {
            PickerTopBar(
                onBackClick = onBackClick,
                canContinue = canContinue,
                onContinueClick = {
                    if (canContinue) {
                        onContinueClick()
                    }
                },
            )
        },
        bottomBar = { PickerBottomBar() },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 26.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PickerFilterChips() }

            item {
                SectionHeader(
                    title = "Hôm nay",
                )
            }
            item {
                ImageGrid(
                    items = todayItems,
                    selectedImageId = selectedImageId,
                    onSelectSingleImage = { selectedImageId = it },
                )
            }

            item {
                SectionHeader(title = "Hôm qua")
            }
            item {
                ImageGrid(
                    items = yesterdayItems,
                    selectedImageId = selectedImageId,
                    onSelectSingleImage = { selectedImageId = it },
                )
            }

            item { Spacer(modifier = Modifier.height(160.dp)) }
        }
    }
}

@Composable
private fun PickerTopBar(
    onBackClick: () -> Unit,
    canContinue: Boolean,
    onContinueClick: () -> Unit,
) {
    Surface(color = PickerBackground, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = PickerBrand,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onBackClick),
                )
                Text(
                    text = "Chọn ảnh",
                    color = PickerTitle,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Surface(
                onClick = onContinueClick,
                shape = RoundedCornerShape(999.dp),
                color = if (canContinue) PickerBrand else Color(0xFFC8CDDA),
            ) {
                Text(
                    text = "Tiếp tục",
                    color = if (canContinue) Color.White else PickerMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun PickerFilterChips() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PickerBrand,
            ) {
                Text(
                    text = "Tất\ncả\nảnh",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        items(listOf("Chụp\nmàn\nhình", "Instagram", "Zalo")) { label ->
            Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFE1E4EA)) {
                Text(
                    text = label,
                    color = Color(0xFF5F6470),
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color(0xFF292E38),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        if (action != null) {
            Text(
                text = action,
                color = PickerBrand,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun ImageGrid(
    items: List<PickerImageItem>,
    selectedImageId: Int?,
    onSelectSingleImage: (Int) -> Unit,
) {
    val rows = items.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    ImageCell(
                        modifier = Modifier.weight(1f),
                        item = item,
                        selected = selectedImageId == item.id,
                        onClick = { onSelectSingleImage(item.id) },
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ImageCell(
    modifier: Modifier,
    item: PickerImageItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Brush.linearGradient(item.gradient))
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x20000000)),
            )
            Surface(
                modifier = Modifier
                    .fillMaxSize(),
                shape = shape,
                color = Color.Transparent,
                border = BorderStroke(5.dp, PickerBrand),
            ) {}
        }
        Surface(
            modifier = Modifier
                .padding(8.dp)
                .size(22.dp)
                .align(Alignment.TopEnd),
            shape = CircleShape,
            color = if (selected) PickerBrand else Color(0x33000000),
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.92f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerBottomBar() {
    val tabs = listOf(
        PickerBottomTab("Thư viện", Icons.Outlined.PhotoLibrary),
        PickerBottomTab("Album", Icons.Outlined.Image),
        PickerBottomTab("Gần đây", Icons.Outlined.Schedule),
    )

    Surface(
        color = PickerSurface,
        border = BorderStroke(1.dp, Color(0xFFE8EBF2)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == 0
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Color(0xFFE2E6FF) else Color.Transparent)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) PickerBrand else Color(0xFF9AA1B0),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = if (selected) PickerBrand else Color(0xFF9AA1B0),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ImagePickerScreenPreview() {
    SmartWifiAppTheme {
        ImagePickerScreen(
            onBackClick = {},
            onContinueClick = {},
        )
    }
}
