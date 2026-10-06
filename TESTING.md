# Проверки — 2026-10-06

Среда: Ubuntu VM, JDK 25 для Gradle, Minecraft Java 1.21.11,
Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Mesa OpenGL, LWJGL 3.3.3.

## Выполнено

- `./gradlew build`: успешно; компиляция Java `--release 21`, remap JAR, упаковка JLayer.
- 9 JUnit-тестов:
  repeat-off, wrap repeat-all, repeat-one, manual-next, empty queue,
  singleton shuffle/loop, цикл трёх режимов, shuffle без повтора текущего.
- Настоящий запуск Minecraft `runClient -PpreviewUi`:
  custom OpenGL экран открывается, скругления/шрифт/поля/кнопки рисуются;
  реальный поиск `lofi` возвращает список треков, настройки и переключение вкладок проверены.
- Java network smoke:
  автоматическое обнаружение client_id, поиск 25 публичных треков;
  resolve URL совпадает по ID; 80 MP3-кадров декодированы из сети (184320 PCM-сэмплов);
  поток закрыт. Аудиофайлы на диск не создавались.
- Real SoundCloud MP3 HLS: 80 кадров декодированы из сети, поток закрыт.
- Java lifecycle smoke, generated sine MP3 + ALSA null sink:
  pause/resume, естественное окончание и автопереход на второй трек,
  repeat-one, удаление текущего, очистка очереди/закрытие ресурсов — успешно.

`tools/NetworkSmoke.java` и `tools/LifecycleSmoke.java` содержат эти ручные проверки.
Для них нужен classpath собранных client-классов, Gson и JLayer;
для LifecycleSmoke также ffmpeg и рабочий Java Sound output.
Тестовый MP3 генерируется в памяти и подаётся через локальный HTTP.

## Ограничения проверки

В VM нет физической звуковой карты: audible sound на Windows не проверен.
OpenAL Minecraft сообщает недоступность устройства в VM; это не ошибка GUI мода.
Общий дополнительный расход ОЗУ на Windows не измерен.
Не проверены все внешние треки, все регионы, ресурс-паки, Sodium/Iris, другие ОС и GPU.
Не гарантируется стабильность неофициального SoundCloud web API.

PNG в архиве — реальный GUI screenshot, не дизайн-макет.
