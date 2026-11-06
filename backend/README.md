# LinkScanner: backend

API que analiza un link y responde si parece phishing. Usa Java 21 y Spring Boot.

## Comandos

```bash
./mvnw test             # correr los tests
./mvnw spring-boot:run  # levantar en http://localhost:8080
```

## Uso

```bash
curl -X POST localhost:8080/api/v1/analyze \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://rnercadopago.com/"}'
```

```json
{
  "host": "rnercadopago.com",
  "verdict": "DANGEROUS",
  "score": 60,
  "officialDomain": false,
  "findings": [
    {
      "code": "BRAND_LOOKALIKE",
      "message": "El dominio se parece a Mercado Pago, pero no es el oficial",
      "weight": 60
    }
  ],
  "contentScan": "SKIPPED",
  "scans": {},
  "scanId": null
}
```

| `verdict` | Cuándo |
|---|---|
| `DANGEROUS` | Puntaje de 60 a 100 |
| `SUSPICIOUS` | Puntaje de 30 a 59 |
| `SAFE` | Puntaje menor a 30, y el dominio es oficial (`officialDomain: true`) o se revisó su contenido |
| `UNKNOWN` | Puntaje menor a 30, sin verificar. **No significa seguro** |

Un link inválido devuelve 400, y pasar el límite de pedidos devuelve 429.

## Análisis externo (urlscan.io y VirusTotal)

Para activarlos, definí las API keys antes de levantar el backend. Cada servicio sin key queda desactivado, y sin ninguna la API funciona solo con heurísticas.

```bash
export URLSCAN_API_KEY='tu-key'
export VIRUSTOTAL_API_KEY='tu-key'
```

Para no gastar cuota ni demorar cada link, por defecto solo se manda a los dos servicios lo que las heurísticas dejan en `SUSPICIOUS`, que es donde su respuesta puede cambiar el veredicto. Para revisar cualquier otro link público, incluso uno oficial o ya `DANGEROUS`, agregá `"deepScan": true` al pedido:

```bash
curl -X POST localhost:8080/api/v1/analyze \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/","deepScan":true}'
```

**Si cualquiera de los dos lo marca como malicioso, el veredicto es `DANGEROUS`.** En VirusTotal cuenta como malicioso a partir de 2 motores (`min-malicious-engines`).

Lo que recibe VirusTotal queda visible para su comunidad, así que no se le mandan la query ni el fragmento. El plan gratuito permite 4 pedidos por minuto y 500 por día, y no se puede usar en productos comerciales.

Si `contentScan` viene en `PENDING`, consultá `GET /api/v1/analyze/{scanId}` cada 2 o 3 segundos hasta que cambie. El campo `scans` muestra el estado de cada servicio. El análisis puede tardar más de un minuto, y se abandona a los 2 minutos en urlscan y a los 3 en VirusTotal.

| `contentScan` | Significado |
|---|---|
| `PENDING` | Sigue en curso |
| `COMPLETED` | Respondieron todos los servicios, y su resultado ya está en el veredicto |
| `SKIPPED` | No se escaneó: no hacía falta y no se pidió `deepScan`, no es un sitio público (IP o host interno) o no hay API key |
| `UNAVAILABLE` | No se pudo verificar el contenido. **No significa que esté limpio** |

Los análisis en curso se guardan en memoria durante 15 minutos: si el backend se reinicia, esos `scanId` devuelven 404.

## Límites

Se configuran en `linkscanner.limits`, dentro de `src/main/resources/application.yml`:

| Límite | Valor | Al superarlo |
|---|---|---|
| Análisis por IP | 30 por minuto | Responde 429 |
| Escaneos externos, sumando a todos los clientes | 2 por minuto y 50 por día | Responde con las heurísticas y `contentScan: UNAVAILABLE` |

Los topes de escaneo protegen la cuota, pero no a los demás usuarios: alguien que los agote deja a todos sin escaneo externo hasta que se renueven. Evitarlo requiere autenticar a los clientes.

## Publicarlo

La imagen se construye desde la raíz del repo y corre con cualquier servicio de contenedores:

```bash
docker build -t linkscanner-backend .
docker run -p 8080:8080 linkscanner-backend
```

Variables de entorno:

| Variable | Para qué |
|---|---|
| `PORT` | Puerto donde escucha. Por defecto, 8080 |
| `URLSCAN_API_KEY`, `VIRUSTOTAL_API_KEY` | Activan cada servicio externo. Cargalas como secretos del hosting, nunca en el repo |
| `FORWARD_HEADERS_STRATEGY` | Poné `native` si hay un proxy delante, que es lo habitual en un hosting. Sin eso, todos los usuarios cuentan como una sola IP y comparten el límite. Sin proxy, dejala en `none` |

Tiene que correr **en una sola instancia y siempre encendida**: los análisis en curso y los límites viven en memoria. El servicio debe ofrecer HTTPS, porque la app Android en versión release no acepta otra cosa. Para comprobar que está viva, usá `GET /actuator/health`.

Después de publicarlo, instalá la app apuntando a la URL pública: `./gradlew installDebug -Plinkscanner.backendUrl=https://tu-dominio`.

## Dónde tocar

- **Marcas, TLDs, acortadores, hosting gratuito y palabras clave:** `linkscanner.detection` en `src/main/resources/application.yml`.
- **Una regla nueva:** una clase `@Component` que implemente `Heuristic`, en `services/heuristics`. Se registra sola.
- **Otro servicio externo:** una clase `@Component` que implemente `ContentScanner`, en `services`. Se registra sola.
