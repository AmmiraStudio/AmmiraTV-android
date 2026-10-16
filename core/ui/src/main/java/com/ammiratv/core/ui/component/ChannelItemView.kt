package com.ammiratv.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import coil.compose.AsyncImage
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.ChannelEpgInfo
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurfaceVariant
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

@Composable
fun ChannelItemView(
    channel: Channel,
    /** TV-style number to show; null (unnumbered channel) shows nothing. */
    channelNumber: Int?,
    epgInfo: ChannelEpgInfo? = null,
    isSelected: Boolean = false,
    onChannelClick: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(12.dp)
    var rowFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .dpadFocusable(
                shape = cardShape,
                focusedScale = 1.02f,
                onFocusChange = { rowFocused = it },
                onClick = { onChannelClick(channel) }
            )
            .background(
                color = if (isSelected) DarkSurfaceVariant else DarkCard,
                shape = cardShape
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Channel Number
            Text(
                text = channelNumber?.toString().orEmpty(),
                color = if (isSelected) AmmiraTeal else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                // An unnumbered channel does not reserve an empty column on a narrow screen
                modifier = Modifier.width(
                    if (channelNumber == null &&
                        com.ammiratv.core.ui.device.LocalWidthClass.current == com.ammiratv.core.ui.device.WidthClass.COMPACT
                    ) 0.dp else 48.dp
                )
            )

            // Logo or fallback icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF26262C)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.cleanName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = AmmiraTeal,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Channel Title & EPG Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                ScrollingText(text = channel.cleanName, focused = rowFocused, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

                val currentProg = epgInfo?.currentProgram
                if (currentProg != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    ScrollingText(text = currentProg.title, focused = rowFocused, color = Color(0xFFDADADF), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { epgInfo.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AmmiraTeal,
                        trackColor = Color(0xFF2A2A30)
                    )
                    val nextProg = epgInfo.nextProgram
                    if (nextProg != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        ScrollingText(text = "A seguire: ${nextProg.title}", focused = rowFocused, color = TextSecondary, fontSize = 11.sp)
                    }
                } else if (!channel.groupTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    ScrollingText(text = channel.groupTitle ?: "", focused = rowFocused, color = TextSecondary, fontSize = 12.sp)
                }
            }

            // Favorite Icon Button
            IconButton(
                onClick = { onFavoriteToggle(channel) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (channel.isFavorite) Color(0xFFFF5252) else Color(0xFF707070),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

