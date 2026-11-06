# LinkScanner

App gratuita para Android que revisa cada link antes de abrirlo y te avisa si parece phishing.

Se configura como tu navegador predeterminado. Cuando tocás un link, muestra un veredicto y los motivos, y vos decidís si abrirlo en tu navegador de siempre.

| Veredicto | Qué significa |
|---|---|
| **Seguro** | Es un dominio oficial conocido, o se revisó y no se encontraron riesgos suficientes |
| **Sin verificar** | No hay señales de riesgo, pero tampoco se pudo verificar. **No significa que sea seguro** |
| **Sospechoso** | Tiene señales de riesgo, como un link acortado |
| **Peligroso** | Tiene señales fuertes de phishing, como imitar a una marca conocida |

Ningún análisis es infalible: un veredicto favorable no garantiza que un sitio sea legítimo.

## Instalarla

La app todavía no está en Google Play. Requiere Android 10 o superior.

1. En tu teléfono, descargá el APK desde la [última versión](https://github.com/nicolasagosti/LinkScanner/releases/latest).
2. Abrí el archivo. Android te va a pedir permiso para instalar apps de origen desconocido, y Play Protect puede mostrar una advertencia porque la app no viene de la tienda.
3. Abrí LinkScanner y tocá **Elegir como predeterminado**.

Para dejar de usarla, elegí otro navegador predeterminado en los ajustes de Android o desinstalala.

## Qué no puede revisar

Solo recibe los links que Android le entrega al navegador predeterminado. Quedan afuera:

- Los links que abren directamente la app de un sitio, como la de tu banco.
- Los links que una app abre en su propio navegador interno.
- Lo que navegás dentro de otro navegador.

## Privacidad

Los links que abrís se envían a nuestro servidor para analizarlos. Los que parecen sospechosos, y los que pedís revisar a fondo, también se envían a urlscan.io y VirusTotal. Los detalles están en la [política de privacidad](PRIVACY.md).

## Para desarrolladores

- [`backend/`](backend/README.md): la API que analiza los links, en Java y Spring Boot.
- [`android/`](android/README.md): la app, en Kotlin.
