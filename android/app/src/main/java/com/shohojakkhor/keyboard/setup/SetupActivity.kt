package com.shohojakkhor.keyboard.setup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohojakkhor.keyboard.R
import com.shohojakkhor.keyboard.ime.state.PersistentSelectionMemory
import com.shohojakkhor.keyboard.ime.state.PersonalDictionaryStore
import com.shohojakkhor.keyboard.ime.state.SuggestionPreferences

/**
 * Two-step onboarding for the IME:
 *
 *  1. Open Android input-method settings so the user can enable
 *     "Shohojakkhor Keyboard".
 *  2. Open the Android input-method picker so the user can select
 *     "Shohojakkhor Keyboard" as active.
 *
 * The screen is intentionally minimal per the elderly-usability guidelines:
 * two large buttons, short Bengali sentences, no hidden gestures.
 */
class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShohojakkhorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SetupScreen(
                        onEnableClick = ::openInputMethodSettings,
                        onPickClick = ::openInputMethodPicker,
                        onVoiceClick = ::openVoiceScreen,
                    )
                }
            }
        }
    }

    private fun openInputMethodSettings() {
        startActivity(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun openInputMethodPicker() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    private fun openVoiceScreen() {
        startActivity(
            Intent(this, com.shohojakkhor.keyboard.voice.VoiceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun ShohojakkhorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF0B5D61),
            onPrimary = Color.White,
            secondary = Color(0xFFE7A21A),
            background = Color(0xFFFFFAF1),
            surface = Color(0xFFFFFAF1),
            surfaceVariant = Color(0xFFF1EDE3),
            onBackground = Color(0xFF17383A),
            onSurface = Color(0xFF17383A),
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}

@Composable
internal fun SetupScreen(
    onEnableClick: () -> Unit,
    onPickClick: () -> Unit,
    onVoiceClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.shohojakkhor_logo),
            contentDescription = stringResource(R.string.brand_logo_description),
            modifier = Modifier.size(132.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_subtitle),
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(32.dp))

        StepCard(
            titleRes = R.string.setup_step1_title,
            bodyRes = R.string.setup_step1_body,
            buttonLabelRes = R.string.setup_step1_button,
            onClick = onEnableClick,
        )
        Spacer(Modifier.height(24.dp))
        StepCard(
            titleRes = R.string.setup_step2_title,
            bodyRes = R.string.setup_step2_body,
            buttonLabelRes = R.string.setup_step2_button,
            onClick = onPickClick,
        )
        Spacer(Modifier.height(24.dp))
        StepCard(
            titleRes = R.string.setup_voice_title,
            bodyRes = R.string.setup_voice_body,
            buttonLabelRes = R.string.setup_voice_button,
            onClick = onVoiceClick,
        )
        Spacer(Modifier.height(24.dp))
        SuggestionSettingsCard()
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.setup_footer_note),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun SuggestionSettingsCard() {
    val context = LocalContext.current
    val preferences = remember { SuggestionPreferences(context) }
    val personalDictionary = remember { PersonalDictionaryStore(context) }
    var noisyEnabled by remember { mutableStateOf(preferences.noisySuggestionsEnabled) }
    var emojiEnabled by remember { mutableStateOf(preferences.emojiSuggestionsEnabled) }
    var standardBangla by remember { mutableStateOf(preferences.preferStandardBangla) }
    var latin by remember { mutableStateOf("") }
    var bengali by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(personalDictionary.entries()) }
    var confirmClear by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.suggestion_settings_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.suggestion_settings_body),
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            SettingSwitch(
                label = stringResource(R.string.suggestion_noisy_label),
                checked = noisyEnabled,
            ) {
                noisyEnabled = it
                preferences.noisySuggestionsEnabled = it
            }
            SettingSwitch(
                label = stringResource(R.string.suggestion_standard_label),
                checked = standardBangla,
            ) {
                standardBangla = it
                preferences.preferStandardBangla = it
            }
            SettingSwitch(
                label = stringResource(R.string.suggestion_emoji_label),
                checked = emojiEnabled,
            ) {
                emojiEnabled = it
                preferences.emojiSuggestionsEnabled = it
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.personal_dictionary_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = latin,
                onValueChange = { latin = it },
                label = { Text(stringResource(R.string.personal_dictionary_latin)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = bengali,
                onValueChange = { bengali = it },
                label = { Text(stringResource(R.string.personal_dictionary_bengali)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = {
                    personalDictionary.add(latin, bengali)
                    entries = personalDictionary.entries()
                    latin = ""
                    bengali = ""
                },
                enabled = latin.isNotBlank() && bengali.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.personal_dictionary_add), fontSize = 18.sp)
            }
            entries.take(MAX_VISIBLE_PERSONAL_ENTRIES).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${entry.latin} → ${entry.bengali}", fontSize = 17.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        personalDictionary.remove(entry.latin, entry.bengali)
                        entries = personalDictionary.entries()
                    }) { Text(stringResource(R.string.personal_dictionary_delete)) }
                }
            }
            TextButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.suggestion_forget_learned), fontSize = 18.sp)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.suggestion_forget_title)) },
            text = { Text(stringResource(R.string.suggestion_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    PersistentSelectionMemory.clearAll(context)
                    confirmClear = false
                }) { Text(stringResource(R.string.suggestion_forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.suggestion_forget_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val MAX_VISIBLE_PERSONAL_ENTRIES = 20

@Composable
private fun StepCard(
    titleRes: Int,
    bodyRes: Int,
    buttonLabelRes: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(titleRes),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(bodyRes),
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) {
                Text(
                    text = stringResource(buttonLabelRes),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun SetupScreenPreview() {
    ShohojakkhorTheme {
        SetupScreen(onEnableClick = {}, onPickClick = {}, onVoiceClick = {})
    }
}
