package app.baki

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Digits are the source of truth: "1234" is RM12.34, never a floating-point value. */
fun centsDigits(cents: Long): String = cents.takeIf { it > 0 }?.toString().orEmpty()

fun initialCentsDigits(display: String?): String =
    display?.let { parseMoneyCents(it)?.toString() }.orEmpty()

fun centsFromDigits(digits: String): Long = digits.toLongOrNull() ?: 0L

fun formatCentsDigits(digits: String): String {
    if (digits.isEmpty()) return ""
    val d = digits.takeLast(9)
    val padded = d.padStart(3, '0')
    val whole = padded.dropLast(2).trimStart('0').ifEmpty { "0" }
    return "$whole.${padded.takeLast(2)}"
}

/** Raw field input. A typed decimal/paste is converted; a plain digit stream is cents-first. */
fun normalizeCentsDigits(raw: String, previous: String): String {
    if (raw.isBlank()) return ""
    if (raw.contains('.') || raw.contains(',')) {
        return parseMoneyCents(raw)?.toString() ?: previous
    }
    val digits = raw.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" }
    return digits.take(9)
}

private class CentsOffsetMapping(private val digits: Int, private val formatted: String) : OffsetMapping {
    private val leading = if (digits <= 2) (formatted.length - digits).coerceAtLeast(0) else 0
    private val boundary = (digits - 2).coerceAtLeast(0)

    override fun originalToTransformed(offset: Int): Int = when {
        digits == 0 -> 0
        digits <= 2 -> (leading + offset).coerceIn(0, formatted.length)
        else -> (offset + if (offset > boundary) 1 else 0).coerceIn(0, formatted.length)
    }

    override fun transformedToOriginal(offset: Int): Int = when {
        digits == 0 -> 0
        digits <= 2 -> (offset - leading).coerceIn(0, digits)
        else -> (offset - if (offset > boundary) 1 else 0).coerceIn(0, digits)
    }
}

private object CentsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = formatCentsDigits(digits)
        return TransformedText(AnnotatedString(formatted), CentsOffsetMapping(digits.length, formatted))
    }
}

/**
 * One field for every currency amount. The keyboard is numeric; decimal input is still accepted
 * through paste and converted exactly to cents.
 */
@Composable
fun CentsField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "0.00",
    prefix: String = "RM",
    supportingText: String? = null,
    isError: Boolean = false,
    textStyle: TextStyle? = null,
    shape: Shape? = null,
    colors: TextFieldColors? = null,
    filled: Boolean = false,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val leading: @Composable (() -> Unit)? = if (prefix.isNotEmpty()) {
        {
            Text(
                prefix,
                color = scheme.onSecondaryContainer.copy(alpha = 0.72f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    } else null
    val support: @Composable (() -> Unit)? = supportingText?.let { message ->
        { Text(message, color = if (isError) scheme.error else scheme.onSurfaceVariant) }
    }
    val labelContent: @Composable (() -> Unit)? = label?.let { title -> { Text(title) } }
    val placeholderContent: @Composable (() -> Unit) = {
        Text(
            placeholder,
            color = if (filled) scheme.onSecondaryContainer.copy(alpha = 0.30f) else scheme.onSurfaceVariant.copy(alpha = 0.45f),
            style = textStyle ?: MaterialTheme.typography.bodyLarge,
        )
    }
    val semanticsMod = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier

    if (filled) {
        TextField(
            value = digits,
            onValueChange = { onDigitsChange(normalizeCentsDigits(it, digits)) },
            isError = isError,
            supportingText = support,
            leadingIcon = leading,
            placeholder = placeholderContent,
            label = labelContent,
            singleLine = true,
            enabled = enabled,
            textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = shape ?: MaterialTheme.shapes.medium,
            visualTransformation = CentsVisualTransformation,
            modifier = semanticsMod.fillMaxWidth(),
            colors = colors ?: TextFieldDefaults.colors(),
        )
    } else {
        OutlinedTextField(
            value = digits,
            onValueChange = { onDigitsChange(normalizeCentsDigits(it, digits)) },
            isError = isError,
            supportingText = support,
            leadingIcon = leading,
            placeholder = placeholderContent,
            label = labelContent,
            singleLine = true,
            enabled = enabled,
            textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = shape ?: MaterialTheme.shapes.medium,
            visualTransformation = CentsVisualTransformation,
            modifier = semanticsMod.fillMaxWidth(),
            colors = colors ?: OutlinedTextFieldDefaults.colors(),
        )
    }
}
