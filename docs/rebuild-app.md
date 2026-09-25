# Пересборка приложения с готовым движком

Готовый движок Chromium/Cefrium со встроенными программными AC3/EAC3 хранится
отдельно в [GitHub Release](https://github.com/ARST113/lampa-chromium-android/releases/tag/cefrium-0.9.2-chromium-152-ac3-arm64-r8).
Скачиваемый файл `cefrium-sdk-arm64-ac3.aar` — библиотека Android с нативным
движком, Java API и ресурсами. Это не устанавливаемый APK. APK — приложение
Lampa, которое включает эту библиотеку. Версия рантайма: Cefrium 0.9.2,
Chromium 152.0.7977.82, Android 10+, ARM64.

Release не использует 14-дневный срок хранения Actions. Файлы остаются доступны,
пока релиз или репозиторий не будут удалены. SHA256 закреплён в
`dependencies/runtime.json` и проверяется при каждой сборке.

## Через GitHub

1. Открой **Actions → Build APK with saved AC3/EAC3 runtime**.
2. Нажми **Run workflow**, выбери `main` и запусти.
3. После завершения скачай `lampa-app-arm64-…` из **Artifacts**.
   Внутри находится `lampa-software-ac3-arm64.apk`.

Этот процесс загружает готовый AAR, собирает интерфейс Lampa и приложение Gradle.
Chromium и FFmpeg повторно не компилируются. Изменения приложения в `main`
также запускают этот workflow автоматически. APK подписывается отладочным
ключом для тестирования.

## На настроенном Linux-исполнителе

Нужны JDK 25, Android SDK (API/build-tools 37), Node/npm, FFmpeg и Python 3.11+,
как на текущем VPS. Из корня репозитория:

```bash
bash scripts/build-app.sh
```

Результат: `artifacts/lampa-software-ac3-arm64.apk`. Загруженный рантайм
кэшируется в `.cache/runtime/`. Можно менять Android-оболочку и ресурсы
приложения без изменения движка. Фронтенд Lampa закреплён на ревизии в
`scripts/build-frontend.sh`; его обновление требует изменения этого pin.

Полная сборка **Build Cefrium with built-in AC3/EAC3** нужна при изменении
самого Chromium/FFmpeg или добавлении другой архитектуры. Сохранённый рантайм
ARM64 не подходит для эмулятора x86_64. Успешная компиляция не подтверждает
воспроизведение кодеков на устройстве: такую проверку выполняют отдельно.
