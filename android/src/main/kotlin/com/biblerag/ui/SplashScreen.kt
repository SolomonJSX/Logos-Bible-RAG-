package com.biblerag.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biblerag.R
import kotlinx.coroutines.delay

/**
 * Экран заставки (Splash Screen) приложения Logos.
 * Отображает винтажную гравюру Богородицы с младенцем Христом и надписью «Logos».
 */
@Composable
fun SplashScreen(
    statusText: String = "Инициализация Писания...",
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        // Показываем заставку 2 секунды перед плавным переходом к чату
        delay(2200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1610)) // Тёплый тон под старинный пергамент
            .clickable { onSplashFinished() } // Быстрый пропуск по нажатию
    ) {
        // Полноэкранная иллюстрация Logos
        Image(
            painter = painterResource(id = R.drawable.logos_splash),
            contentDescription = "Logos",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Мягкий полупрозрачный градиент внизу для читаемости текста
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xBB18120B),
                            Color(0xEE140F09)
                        )
                    )
                )
        )

        // Нижняя панель с прогрессом и библейским эпиграфом
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "«В начале было Слово, и Слово было у Бога»",
                color = Color(0xFFE8D7BE),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFC9A265) // Золотистый оттенок
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = statusText,
                    color = Color(0xFFD2C1A9),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
