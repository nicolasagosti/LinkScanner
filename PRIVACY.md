# Política de privacidad de LinkScanner

Última actualización: 6 de noviembre de 2026

LinkScanner revisa los links que abrís en tu teléfono antes de que llegues a ellos. Para eso, tiene que enviarlos a nuestro servidor y, en algunos casos, a servicios de terceros. Esta página explica qué se envía, a quién y cuándo.

La app es gratuita, no pide crear una cuenta y no tiene publicidad ni herramientas de analítica.

## Qué datos se envían a nuestro servidor

- **El link completo que abrís**, incluidos los datos que pueda llevar. Por ejemplo, un link de cambio de contraseña puede incluir un código personal.
- **Tu dirección IP**, que recibe cualquier servidor al que te conectás.

Esto pasa con cada link que abrís mientras LinkScanner es tu navegador predeterminado, y con los que escribís en la app.

## Qué hacemos con esos datos

- **El link:** lo analizamos y no lo guardamos en una base de datos ni lo escribimos en nuestros registros.
- **Los análisis externos en curso:** mantenemos el nombre del sitio, el resultado y el identificador del análisis en la memoria del servidor durante un máximo de 15 minutos, para poder responderte.
- **Tu dirección IP:** la usamos para limitar cuántos análisis se pueden pedir por minuto. La mantenemos en memoria alrededor de un minuto.

No vendemos estos datos ni los usamos para publicidad.

## Con quién se comparten

Nuestro servidor funciona en [Railway](https://railway.com), que puede registrar datos técnicos de las conexiones según sus propias políticas.

Los links se envían a estos dos servicios solo cuando el análisis inicial encuentra señales de riesgo moderadas, o cuando tocás **Revisar a fondo**:

| Servicio | Qué recibe | Quién puede verlo |
|---|---|---|
| [urlscan.io](https://urlscan.io) | El link completo, sin usuario ni contraseña si los incluía. Su sistema abre la página para analizarla | Se envía como análisis privado |
| [VirusTotal](https://www.virustotal.com) | El link sin lo que va después de `?` o `#` | **Queda visible para la comunidad de VirusTotal** |

Cada servicio trata esos datos según su propia política de privacidad.

Las direcciones IP y los nombres de equipos de redes internas no se envían a estos servicios.

## Cómo cuidarte

- **No uses Revisar a fondo con links personales**, como los de cambio de contraseña o los que incluyen datos de una cuenta. Abrir uno de esos links para analizarlo podría invalidarlo.
- **Para que tus links dejen de pasar por la app**, elegí otro navegador predeterminado en los ajustes de Android o desinstalá LinkScanner.

## Qué guarda la app en tu teléfono

La app no guarda los links que analizás ni un historial.

## Contacto

Podés escribirnos [abriendo un issue en GitHub](https://github.com/nicolasagosti/LinkScanner/issues). Los issues son públicos, así que no incluyas links personales ni datos privados.

## Cambios en esta política

Si cambia lo que hace la app con tus datos, vamos a actualizar esta página y su fecha.
