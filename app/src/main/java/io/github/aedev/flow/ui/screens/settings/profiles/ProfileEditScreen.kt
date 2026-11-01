package io.github.aedev.flow.ui.screens.settings.profiles

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.ProfileAvatar
import io.github.aedev.flow.data.profile.SlicePart
import io.github.aedev.flow.data.profile.displayName
import io.github.aedev.flow.ui.components.profiles.profilePhotoRequest
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.components.shared.LocalDpadNavigation

private val PreviewSize = 88.dp
private val ButtonSpacing = 8.dp
private const val NEW_PROFILE_COLOR = "blue"
private const val MIN_PASSWORD_LENGTH = 6

/**
 * A new profile when [profileId] is null, otherwise that profile: its name, colour and picture,
 * and for a new one whether it starts fresh or copies parts of the running profile.
 * A new profile requires a password; editing an existing one only asks for it on demand.
 */
@Composable
internal fun ProfileEditScreen(
    profileId: String?,
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val existing = profileId?.let { id -> state?.profiles?.firstOrNull { it.id == id } }
    val isNew = profileId == null

    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var name by rememberSaveable { mutableStateOf("") }
    var colorKey by rememberSaveable { mutableStateOf(NEW_PROFILE_COLOR) }
    var kind by rememberSaveable { mutableStateOf(ProfileAvatar.INITIAL) }
    var emoji by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var copying by rememberSaveable { mutableStateOf(false) }
    var copySettings by rememberSaveable { mutableStateOf(true) }
    var copySubscriptions by rememberSaveable { mutableStateOf(true) }
    var copyRecommendations by rememberSaveable { mutableStateOf(true) }

    // حالة كلمة المرور — تُمسح عند مغادرة الشاشة
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var changePassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existing, state) {
        if (loaded) return@LaunchedEffect
        if (existing != null) {
            name = existing.name
            colorKey = existing.colorKey
            kind = existing.avatar.kind
            emoji = existing.avatar.emoji
            loaded = true
        } else if (state != null) {
            onBack?.invoke()
        }
    }
    ProfilesMessageEffect(message, snackbarHostState, viewModel::consumeMessage)

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                photo = uri.toString()
                kind = ProfileAvatar.PHOTO
            }
        }
    val canPickPhoto = !LocalDpadNavigation.current || ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
    val pendingPhoto = photo?.let(Uri::parse)
    val others = state?.profiles.orEmpty().filterNot { it.id == profileId }
    val issue =
        nameIssue(name, others.map { it.displayName(context) }).takeUnless {
            it == NameIssue.BLANK &&
                existing?.id == state?.defaultId &&
                !isNew
        }
    val avatar = ProfileAvatar(kind = kind, emoji = emoji, photoVersion = existing?.avatar?.photoVersion ?: 0L)
    val preview = Profile(id = profileId.orEmpty(), name = name, colorKey = colorKey, avatar = avatar)
    val previewLabel = if (isNew || name.isNotBlank()) name else existing?.displayName(context).orEmpty()
    val copy =
        if (!copying) {
            emptySet()
        } else {
            setOfNotNull(
                SlicePart.SETTINGS.takeIf { copySettings },
                SlicePart.SUBSCRIPTIONS.takeIf { copySubscriptions },
                SlicePart.RECOMMENDATIONS.takeIf { copyRecommendations },
            )
        }

    // التحقق من كلمة المرور — في المنطق، وليس في الواجهة فقط
    val passwordRequired = isNew || changePassword
    val passwordTooShort = passwordRequired && password.length < MIN_PASSWORD_LENGTH
    val passwordMismatch = passwordRequired && password != confirmPassword
    val passwordIssue = passwordRequired && (password.isBlank() || passwordTooShort || passwordMismatch)

    val draft = ProfileDraft(
        name = name.trim(),
        colorKey = colorKey,
        avatar = avatar,
        photo = pendingPhoto,
        copy = copy,
        password = if (passwordRequired) password.toCharArray() else null,
        changePassword = !isNew && changePassword,
    )
    val canSave = loaded && issue == null && !busy && !passwordIssue
    val runningName =
        state
            ?.profiles
            ?.firstOrNull { it.id == viewModel.runningId }
            ?.displayName(context)
            .orEmpty()

    SettingsPage(
        title = stringResource(if (isNew) R.string.profiles_new_title else R.string.profiles_edit_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
    ) {
        item("profile_edit.preview") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProfilePreview(preview, previewLabel, pendingPhoto.takeIf { kind == ProfileAvatar.PHOTO }, PreviewSize)
            }
        }
        item("profile_edit.name") {
            ProfileNameField(name = name, onNameChange = { name = it }, taken = issue == NameIssue.TAKEN)
        }

        // ---- كلمة المرور ----
        if (isNew) {
            header("profile_edit.password", R.string.profiles_password)
            PasswordFields(
                password = password,
                confirmPassword = confirmPassword,
                showPassword = showPassword,
                onPasswordChange = { password = it },
                onConfirmChange = { confirmPassword = it },
                onToggleVisibility = { showPassword = !showPassword },
                mismatch = passwordMismatch,
                tooShort = passwordTooShort && password.isNotEmpty(),
            )
        } else {
            header("profile_edit.security", R.string.profiles_security)
            item("profile_edit.change_password_switch") {
                ChangePasswordRow(
                    checked = changePassword,
                    onCheckedChange = {
                        changePassword = it
                        if (!it) {
                            password = ""
                            confirmPassword = ""
                            showPassword = false
                        }
                    },
                )
            }
            if (changePassword) {
                PasswordFields(
                    password = password,
                    confirmPassword = confirmPassword,
                    showPassword = showPassword,
                    onPasswordChange = { password = it },
                    onConfirmChange = { confirmPassword = it },
                    onToggleVisibility = { showPassword = !showPassword },
                    mismatch = passwordMismatch,
                    tooShort = passwordTooShort && password.isNotEmpty(),
                )
            }
        }

        header("profile_edit.color", R.string.profiles_color)
        item("profile_edit.colors") { ProfileColorPicker(selected = colorKey, onSelect = { colorKey = it }) }
        header("profile_edit.picture", R.string.profiles_picture)
        item("profile_edit.pictures") {
            ProfilePicturePicker(
                kind = kind,
                emoji = emoji,
                photo =
                    pendingPhoto ?: existing?.takeIf { it.avatar.kind == ProfileAvatar.PHOTO }?.let {
                        profilePhotoRequest(context, it)
                    },
                canPickPhoto = canPickPhoto,
                onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onInitial = { kind = ProfileAvatar.INITIAL },
                onEmoji = {
                    kind = ProfileAvatar.EMOJI
                    emoji = it
                },
            )
        }
        if (isNew) {
            header("profile_edit.start", R.string.profiles_start_with)
            item("profile_edit.start_choice") {
                FlowConnectedToggleGroup(
                    options =
                        listOf(
                            FlowToggleOption(false, stringResource(R.string.profiles_start_fresh)),
                            FlowToggleOption(true, stringResource(R.string.profiles_copy_from, runningName)),
                        ),
                    selected = copying,
                    onSelected = { copying = it },
                )
            }
            if (copying) {
                group(key = "profile_edit.copy") {
                    row("profile_edit.copy.settings") { shape ->
                        CopyRow(
                            R.string.profiles_copy_settings,
                            R.string.profiles_copy_settings_summary,
                            copySettings,
                            { copySettings = it },
                            shape,
                        )
                    }
                    row("profile_edit.copy.subscriptions") { shape ->
                        CopyRow(
                            R.string.profiles_copy_subscriptions,
                            R.string.profiles_copy_subscriptions_summary,
                            copySubscriptions,
                            { copySubscriptions = it },
                            shape,
                        )
                    }
                    row("profile_edit.copy.recommendations") { shape ->
                        CopyRow(
                            R.string.profiles_copy_recommendations,
                            R.string.profiles_copy_recommendations_summary,
                            copyRecommendations,
                            { copyRecommendations = it },
                            shape,
                        )
                    }
                }
            }
        }
        item("profile_edit.actions") {
            if (isNew) {
                Column(verticalArrangement = Arrangement.spacedBy(ButtonSpacing)) {
                    Button(
                        onClick = { viewModel.create(draft, andSwitch = true) { onBack?.invoke() } },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.profiles_create_and_switch)) }
                    FilledTonalButton(
                        onClick = { viewModel.create(draft, andSwitch = false) { onBack?.invoke() } },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.profiles_create)) }
                }
            } else {
                Button(
                    onClick = { existing?.let { viewModel.save(it, draft) { onBack?.invoke() } } },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.profiles_save)) }
            }
        }
    }
}

@Composable
private fun PasswordFields(
    password: String,
    confirmPassword: String,
    showPassword: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    mismatch: Boolean,
    tooShort: Boolean,
) {
    val transformation =
        if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

    item("profile_edit.password_field") {
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.profiles_password_label)) },
            visualTransformation = transformation,
            singleLine = true,
            isError = tooShort,
            supportingText = if (tooShort) {
                { Text(stringResource(R.string.profiles_password_too_short)) }
            } else null,
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (showPassword) R.string.hide_password else R.string.show_password
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item("profile_edit.confirm_password_field") {
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmChange,
            label = { Text(stringResource(R.string.profiles_confirm_password_label)) },
            visualTransformation = transformation,
            singleLine = true,
            isError = mismatch,
            supportingText = if (mismatch) {
                { Text(stringResource(R.string.profiles_password_mismatch)) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChangePasswordRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FlowSwitchRow(
        title = stringResource(R.string.profiles_change_password),
        supportingText = stringResource(R.string.profiles_change_password_summary),
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun CopyRow(
    title: Int,
    summary: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
) {
    FlowSwitchRow(
        title = stringResource(title),
        supportingText = stringResource(summary),
        checked = checked,
        onCheckedChange = onCheckedChange,
        shape = shape,
    )
}
