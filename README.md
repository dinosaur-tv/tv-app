# Dino TV

Домашний экран для Android TV. Интерфейс живёт на `https://home.dym-dino.ru/tv/` — это приложение только держит страницу открытой, не гасит экран и ставит иконку в Leanback.

Тема, фон, режим и заметка настраиваются с телефона. После установки откройте экран, введите шестизначный код в Mini App — телевизор запомнит дом.

## Запуск

1. Откройте этот каталог в Android Studio.
2. Выберите JDK 17.
3. Запустите конфигурацию `app` на Android TV или эмуляторе API 26+.

Для консоли на Windows:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

## Quality checks

Коммит проверяет только пробелы и YAML. Тесты гоняет CI.

```powershell
.\gradlew.bat :app:testDebugUnitTest
pre-commit install
```
