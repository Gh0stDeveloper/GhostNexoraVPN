# 🔑 Ghost Nexora VPN

<div align="center">

![Ghost Nexora VPN](https://img.shields.io/badge/Ghost%20Nexora-VPN%20Manager-00E5FF?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-00E676?style=for-the-badge)

**Gestión profesional de perfiles VPN para Android**
![Ghost Nexora VPN](./ignore/app.png)

*Desarrollado por [Ghost Developer](https://github.com/CHICO-CP)*

</div>

---

## 📱 ¿Qué es Ghost Nexora VPN?

**Ghost Nexora VPN** es una aplicación Android nativa que permite gestionar perfiles de
conexión VPN de forma moderna, centralizada y segura. Diseñada con una experiencia similar
a las VPN comerciales premium, pero con código abierto y total control del usuario.

A diferencia de los gestores convencionales, Ghost Nexora ofrece importación/exportación
de perfiles en JSON, creación manual con soporte para múltiples protocolos, dashboard
reactivo con estados visuales en tiempo real y ejecución persistente en segundo plano
mediante la API oficial `VpnService` de Android.

---

## ✨ Características principales

| Característica | Descripción |
|---|---|
| 🔑 **Gestión de perfiles** | Crear, editar, eliminar y organizar perfiles VPN con etiquetas |
| 📥 **Importar / Exportar** | Soporte completo para JSON con previsualización y merge/replace |
| 🔒 **VPN nativa** | Interfaz TUN real mediante `VpnService` de Android |
| 🫧 **Ventana flotante** | Burbuja de control rápido sobre otras aplicaciones |
| 📊 **Dashboard reactivo** | Estados en tiempo real con animaciones y timer de sesión |
| 📋 **Registro de logs** | Historial completo con filtros por nivel y búsqueda |
| ⚙️ **Ajustes avanzados** | Reconexión automática, gestión de permisos, limpieza de datos |
| 🌙 **Tema oscuro neon** | Material Design 3 con acentos cian/azul/verde |
| 🔔 **Notificación persistente** | Control desde la barra de notificaciones |
| 🚀 **Reconexión al inicio** | Conecta automáticamente al encender el dispositivo |

---

## 🛠️ Stack tecnológico

```
Lenguaje:     Kotlin 2.0
UI:           Jetpack Compose + Material Design 3
Arquitectura: MVVM + Repository Pattern + StateFlow
DI:           Hilt (Dagger)
Base de datos: Room (SQLite)
Preferencias: DataStore (Proto)
Serialización: Gson
VPN:          VpnService (TUN interface)
Overlay:      WindowManager (TYPE_APPLICATION_OVERLAY)
Background:   Foreground Service + START_STICKY
```

---

## 📂 Estructura del proyecto

```
GhostNexoraVPN/
├── app/
│   └── src/main/
│       ├── java/com/ghostnexora/vpn/
│       │   ├── data/
│       │   │   ├── model/          # VpnProfile, LogEntry, ConnectionState
│       │   │   ├── local/          # Room DAOs, AppDatabase, DataStoreManager
│       │   │   └── repository/     # ProfileRepository (SSOT)
│       │   ├── di/                 # AppModule (Hilt)
│       │   ├── navigation/         # Screen, NavGraph, Drawer
│       │   ├── receiver/           # BootReceiver
│       │   ├── service/            # GhostVpnService, FloatingWindowService
│       │   ├── ui/
│       │   │   ├── screens/
│       │   │   │   ├── dashboard/  # Dashboard + ViewModel
│       │   │   │   ├── profiles/   # Lista + Crear/Editar + ViewModels
│       │   │   │   ├── importexport/ # Import + Export + ViewModel
│       │   │   │   ├── logs/       # Logs + ViewModel
│       │   │   │   ├── settings/   # Settings + ViewModel
│       │   │   │   └── about/      # AboutScreen
│       │   │   └── theme/          # Colors, Type, Shape, Dimensions, Components
│       │   ├── util/               # Extensions, PermissionHelper, JsonManager
│       │   └── GhostNexoraApp.kt   # Application class
│       ├── res/
│       │   ├── drawable/           # Íconos vectoriales
│       │   ├── values/             # Colors, Strings, Themes
│       │   └── xml/                # Network config, FileProvider, Backup
│       └── AndroidManifest.xml
├── gradle/
│   └── libs.versions.toml          # Version catalog
├── build.gradle.kts
├── settings.gradle.kts
├── proguard-rules.pro
└── README.md
```
---

## 📋 Permisos requeridos

| Permiso | Motivo |
|---|---|
| `BIND_VPN_SERVICE` | Crear la interfaz TUN de la VPN |
| `INTERNET` | Conexión al servidor VPN |
| `FOREGROUND_SERVICE` | Mantener el servicio activo en background |
| `SYSTEM_ALERT_WINDOW` | Ventana flotante sobre otras apps |
| `RECEIVE_BOOT_COMPLETED` | Reconexión automática al inicio |
| `POST_NOTIFICATIONS` | Notificación persistente de estado (Android 13+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Evitar que el sistema mate el servicio |

---

## 🗺️ Roadmap

```
✅ Fase 1 — UI + Gestión de perfiles + Import/Export + VPN básica + Background
🔄 Fase 2 — Floating Window avanzada + Logs detallados + Favoritos + Reconexión
⏳ Fase 3 — Per-app VPN + Biométrico + Estadísticas de sesión
⏳ Fase 4 — Sincronización en nube + Cifrado avanzado de exportaciones
```

---

## 📄 Formato JSON de perfiles

```json
{
  "appName": "Ghost Nexora VPN",
  "version": "1.0.20",
  "exportedAt": "2026-05-11T00:00:00Z",
  "profiles": [
    {
      "id": "gxn-001",
      "name": "Servidor Principal",
      "host": "vpn.example.com",
      "port": 443,
      "username": "",
      "password": "",
      "method": "ssh",
      "sslEnabled": true,
      "sni": "",
      "proxy": { "host": "", "port": 0, "type": "" },
      "tags": ["fast", "premium"],
      "notes": "Servidor de alta velocidad",
      "enabled": true,
      "lastUsed": "2026-05-11T04:00:00Z"
    }
  ]
}
```

---

## 👨‍💻 Desarrollador

<div align="center">

| | |
|---|---|
| **Nombre** | Ghost Developer |
| **GitHub** | [@CHICO-CP](https://github.com/CHICO-CP) |
| **Telegram** | [@Gh0stDeveloper](https://t.me/Gh0stDeveloper) |
| **Correo** | [ghostnexora@gmail.com](mailto:ghostnexora@gmail.com) |

</div>

---

## 📜 Licencia

```
MIT License

Copyright (c) 2026 Ghost Developer

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

<div align="center">

**Ghost Nexora VPN** — Desarrollado por Ghost Developer

[GitHub](https://github.com/CHICO-CP) · [Telegram](https://t.me/Gh0stDeveloper) · [Contacto](mailto:ghostnexora@gmail.com)

</div>
