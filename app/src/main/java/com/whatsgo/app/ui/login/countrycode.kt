package com.whatsgo.app.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Country(val name: String, val code: String)

private val countries = listOf(
    Country("India", "+91"),
    Country("United States", "+1"),
    Country("Canada", "+1"),
    Country("United Kingdom", "+44"),
    Country("United Arab Emirates", "+971"),
    Country("Saudi Arabia", "+966"),
    Country("Qatar", "+974"),
    Country("Kuwait", "+965"),
    Country("Oman", "+968"),
    Country("Bahrain", "+973"),
    Country("Australia", "+61"),
    Country("Pakistan", "+92"),
    Country("Bangladesh", "+880"),
    Country("Sri Lanka", "+94"),
    Country("Nepal", "+977"),
    Country("Singapore", "+65"),
    Country("Malaysia", "+60"),
    Country("Indonesia", "+62"),
    Country("Germany", "+49"),
    Country("France", "+33")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryCodePicker(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }

    Surface(
        onClick = { showSheet = true },
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(selected, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick country code")
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var query by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }

        val digits = query.filter { it.isDigit() }
        val numericQuery = query.isNotEmpty() && query.all { it.isDigit() || it == '+' }

        val filtered = remember(query) {
            when {
                query.isBlank() -> countries
                numericQuery -> countries.filter { it.code.removePrefix("+").startsWith(digits) }
                else -> countries.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
        }
        val showCustom = numericQuery && digits.isNotEmpty() && digits.length <= 4 &&
                filtered.none { it.code == "+$digits" }

        fun pick(code: String) {
            onSelected(code)
            showSheet = false
        }

        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(20) },
                    placeholder = { Text("Search country or type code") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (showCustom) pick("+$digits")
                        else filtered.firstOrNull()?.let { pick(it.code) }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                Spacer(Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    if (showCustom) {
                        item {
                            ListItem(
                                headlineContent = { Text("Use +$digits", fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Custom country code") },
                                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                                modifier = Modifier.clickable { pick("+$digits") }
                            )
                        }
                    }
                    items(filtered) { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            trailingContent = {
                                Text(
                                    c.code,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.code == selected)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable { pick(c.code) }
                        )
                    }
                    if (filtered.isEmpty() && !showCustom) {
                        item {
                            Text(
                                "No match",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}