# LinkScanner: app Android

Prototipo que se registra como navegador predeterminado para revisar cada link antes de abrirlo. Usa Kotlin y requiere Android 10 o superior.

## Cómo probarla

1. Levantá el backend en tu máquina (ver `../backend/README.md`).
2. Abrí esta carpeta (`android/`) en Android Studio y dejá que sincronice el proyecto.
3. Corré la app en un emulador. Ya apunta al backend local, en `http://10.0.2.2:8080`.
4. Tocá **Elegir como predeterminado** y tocá un link desde otra app.

En un teléfono físico conectado por USB:

```bash
adb reverse tcp:8080 tcp:8080
```

Y agregá `linkscanner.backendUrl=http://localhost:8080` a `~/.gradle/gradle.properties`.

Por línea de comandos, con el SDK indicado en `local.properties` o en `ANDROID_HOME`:

```bash
./gradlew assembleDebug
```

## Cómo funciona

- `ScanActivity` recibe el link, lo manda al backend y muestra el veredicto. El usuario decide si abrirlo: la app nunca lo abre sola.
- Si el backend no responde, lo informa y deja elegir entre reintentar o abrirlo igual.
- **Revisar a fondo** aparece solo con veredicto `UNKNOWN` y manda `deepScan: true`.
- Para abrir el link busca los navegadores instalados sin contarse a sí misma, porque si no el link volvería a ella. Si hay uno solo lo usa directamente, y si hay varios muestra una lista.

## Qué no intercepta

Solo recibe los links que Android le entrega al navegador predeterminado. Quedan afuera:

- Links de dominios verificados que abren directamente su app (Android App Links).
- Links dentro de un WebView de otra app.
- La navegación dentro de otro navegador.
- Custom Tabs de apps que eligen un navegador específico.

## Publicar una versión

La versión release apunta al backend publicado y solo acepta HTTPS. Se firma con una clave que no va al repo, configurada en `android/keystore.properties`:

```properties
storeFile=/ruta/absoluta/a/la/clave.jks
storePassword=...
keyAlias=linkscanner
keyPassword=...
```

```bash
./gradlew assembleRelease   # genera app/build/outputs/apk/release/app-release.apk
```

Antes de cada versión, subí `versionCode` y `versionName` en `app/build.gradle.kts`. **Guardá una copia de la clave y sus contraseñas fuera de esta compu:** sin ellas no se pueden publicar actualizaciones para quienes ya instalaron la app.

## Pendiente

- La lista aparece en cada link si hay más de un navegador: conviene permitir elegir uno y recordarlo.
