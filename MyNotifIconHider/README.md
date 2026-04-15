# MyNotifIconHider

LSPosed модуль для скрытия иконок уведомлений в статус-баре.

## Что делает

- Показывает список активных уведомлений в приложении
- Позволяет включить/выключить скрытие иконки для каждого приложения
- Хукает SystemUI и не отображает иконку уведомления для заблокированных пакетов

## Требования

- Android 8.1+
- LSPosed (или EdXposed)
- Разрешение для NotificationListenerService

## Сборка APK через GitHub Actions

1. Залей этот репозиторий на GitHub
2. Перейди во вкладку **Actions**
3. Запусти workflow **Build APK** (кнопка "Run workflow")
4. После завершения скачай APK из раздела **Artifacts**

## Установка

1. Установи APK на устройство
2. В LSPosed → Модули → включи **NotifIconHider**
3. Поставь галочку на **System UI** в области применения модуля
4. Перезагрузи устройство
5. Открой приложение, выдай разрешение на доступ к уведомлениям
6. Переключай уведомления, которые хочешь скрыть

## Структура

```
app/src/main/
├── java/com/example/notifhider/
│   ├── MainActivity.java   — UI приложения
│   ├── HookInit.java       — Xposed хук для SystemUI
│   └── MySettings.java     — Хранение настроек
├── res/layout/activity_main.xml
├── assets/xposed_init      — Точка входа модуля
└── AndroidManifest.xml
```
