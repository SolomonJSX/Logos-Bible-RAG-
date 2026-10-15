package com.biblerag.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class LogosColors(
    val isDark: Boolean,
    // Чтение Писания
    val pageBg: Color,
    val pageBgDarker: Color,
    val inkDark: Color,          // Основной текст стихов и книг
    val inkGold: Color,          // Второстепенный золотой текст, подзаголовки
    val accentGold: Color,       // Яркий золотой акцент, рамки
    val deepLeather: Color,      // Кожаная обложка, шапки
    val dropCapColor: Color,     // Буквицы и номера стихов
    val dividerColor: Color,     // Разделители
    // Чат Писаря
    val chatBg: Color,           // Фон списка сообщений
    val assistantBubble: Color,  // Облачко ответа Писаря
    val assistantBubbleBorder: Color,
    val userBubble: Color,       // Облачко вопроса пользователя
    val userText: Color,
    val parchmentCard: Color,    // Карточки свитков цитат
    val inputContainerBg: Color, // Фон поля ввода
    val inputTextColor: Color,   // Текст ввода
    val placeholderColor: Color  // Плейсхолдер
)

val LightLogosColors = LogosColors(
    isDark = false,
    pageBg = Color(0xFFF9F5EC),
    pageBgDarker = Color(0xFFF1E9DA),
    inkDark = Color(0xFF2C2219),
    inkGold = Color(0xFF9E782F),
    accentGold = Color(0xFFC5A059),
    deepLeather = Color(0xFF1B1109),
    dropCapColor = Color(0xFF8B1E0F),
    dividerColor = Color(0xFF9E782F).copy(alpha = 0.4f),
    chatBg = Color(0xFFF6F1E6),
    assistantBubble = Color(0xFFFAF6EE),
    assistantBubbleBorder = Color(0xFFC5A059).copy(alpha = 0.45f),
    userBubble = Color(0xFF2B1C12),
    userText = Color(0xFFF6EFE6),
    parchmentCard = Color(0xFFF1E9DA),
    inputContainerBg = Color(0xFFF6F1E6),
    inputTextColor = Color(0xFF2C2219),
    placeholderColor = Color(0xFF8F7A65)
)

val DarkLogosColors = LogosColors(
    isDark = true,
    pageBg = Color(0xFF16110B),           // Глубокий ночной папирус / темная кожа
    pageBgDarker = Color(0xFF1F1810),
    inkDark = Color(0xFFE8DFC8),          // Мягкая антикварная слоновая кость
    inkGold = Color(0xFFD4AA5C),          // Четкое сияющее сусальное золото
    accentGold = Color(0xFFC5A059),
    deepLeather = Color(0xFF130D08),
    dropCapColor = Color(0xFFE27866),     // Теплая коралловая киноварь (отлично читается на темном)
    dividerColor = Color(0xFFC5A059).copy(alpha = 0.35f),
    chatBg = Color(0xFF140E0A),           // Уютный ночной переплет
    assistantBubble = Color(0xFF211811),  // Ночной свиток Писаря
    assistantBubbleBorder = Color(0xFFC5A059).copy(alpha = 0.35f),
    userBubble = Color(0xFF2E2017),       // Тёмно-коричневая кожа
    userText = Color(0xFFF6EFE6),
    parchmentCard = Color(0xFF261B12),    // Винтажная карточка цитаты
    inputContainerBg = Color(0xFF1C130D),
    inputTextColor = Color(0xFFE8DFC8),
    placeholderColor = Color(0xFF9E8C78)
)

val LocalLogosColors = staticCompositionLocalOf { LightLogosColors }

@Composable
fun LogosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkLogosColors else LightLogosColors
    CompositionLocalProvider(LocalLogosColors provides colors) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (darkTheme) {
                androidx.compose.material3.darkColorScheme(
                    background = colors.pageBg,
                    surface = colors.deepLeather,
                    primary = colors.accentGold
                )
            } else {
                androidx.compose.material3.lightColorScheme(
                    background = colors.pageBg,
                    surface = colors.deepLeather,
                    primary = colors.accentGold
                )
            },
            content = content
        )
    }
}
