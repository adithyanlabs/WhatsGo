package com.whatsgo.app.ui.home

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val PillShape = RoundedCornerShape(50)

@Composable
fun ChatTopTabs(selected: TopTab, onSelect: (TopTab) -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(selected) {
        listState.animateScrollToItem(
            index = selected.ordinal,
            scrollOffset = -48
        )
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TopTab.entries, key = { it.name }) { tab ->
            val isSelected = tab == selected

            val transition = updateTransition(
                targetState = isSelected,
                label = "tab"
            )

            val backgroundColor by transition.animateColor(
                transitionSpec = { tween(200, easing = FastOutSlowInEasing) },
                label = "bg"
            ) { sel ->
                if (sel) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            }

            val contentColor by transition.animateColor(
                transitionSpec = { tween(200, easing = FastOutSlowInEasing) },
                label = "content"
            ) { sel ->
                if (sel) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .drawBehind { drawRect(backgroundColor) }
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(tab) }
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = tab.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
