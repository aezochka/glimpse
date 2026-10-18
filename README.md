# Glimpse

Быстрая галерея для Android в стиле Material 3 Expressive.

## Что умеет
- Сетка фото/видео с группировкой по дням, фильтром «все/фото/видео», вкладка альбомов
- Авто-обновление при появлении новых снимков (ContentObserver)
- Просмотрщик: свайп-пейджер, пинч-зум, двойной тап, воспроизведение видео (media3/ExoPlayer)
- Редактор: поворот, зеркало, кроп с 4 ручками и пресетами соотношений, фильтры, яркость/контраст/насыщенность; сохраняет копию в Pictures/Glimpse
- Пикер: любое приложение с «прикрепить файл» может выбрать медиа через Glimpse (GET_CONTENT / PICK, одиночный и множественный выбор)
- M3E: MaterialExpressiveTheme, MotionScheme.expressive, M3E floating toolbar в редакторе, фильтр медиа из общего M3E-порта, морф форм при нажатии

## Сборка
```
./gradlew :app:assembleRelease
```
CI (GitHub Actions) собирает debug+release APK при каждом пуше в main.

Kotlin + Jetpack Compose, minSdk 26.
