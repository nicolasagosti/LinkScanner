# LinkScanner: backend

API que analiza un link y responde si parece phishing. Usa Java 21 y Spring Boot.

## Comandos

```bash
./mvnw test             # correr los tests
./mvnw spring-boot:run  # levantar en http://localhost:8080
```

## Uso

Para probarlo a mano, abrí http://localhost:8080 con el backend levantado. Esa página es solo para desarrollo: hay que sacarla antes de publicar el backend (`src/main/resources/static/index.html`).

Por API:

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

Un link inválido devuelve 400.

## Análisis externo (urlscan.io y VirusTotal)

Para activarlos, definí las API keys antes de levantar el backend. Cada servicio sin key queda desactivado, y sin ninguna la API funciona solo con heurísticas.

```bash
export URLSCAN_API_KEY='tu-key'
export VIRUSTOTAL_API_KEY='tu-key'
```

Todo link público se manda a los dos servicios, incluso los oficiales y los que ya salen `DANGEROUS`, así que cada análisis gasta cuota. **Si cualquiera de los dos lo marca como malicioso, el veredicto es `DANGEROUS`.** En VirusTotal cuenta como malicioso a partir de 2 motores (`min-malicious-engines`).

Lo que recibe VirusTotal queda visible para su comunidad, así que no se le mandan la query ni el fragmento. El plan gratuito permite 4 pedidos por minuto y 500 por día, y no se puede usar en productos comerciales.

Si `contentScan` viene en `PENDING`, consultá `GET /api/v1/analyze/{scanId}` cada 2 o 3 segundos hasta que cambie. El campo `scans` muestra el estado de cada servicio. El análisis puede tardar más de un minuto, y se abandona a los 2 minutos en urlscan y a los 3 en VirusTotal.

| `contentScan` | Significado |
|---|---|
| `PENDING` | Sigue en curso |
| `COMPLETED` | Respondieron todos los servicios, y su resultado ya está en el veredicto |
| `SKIPPED` | No se escaneó: no es un sitio público (IP o host interno) o no hay API key |
| `UNAVAILABLE` | No se pudo verificar el contenido. **No significa que esté limpio** |

Los análisis en curso se guardan en memoria durante 15 minutos: si el backend se reinicia, esos `scanId` devuelven 404.

## Dónde tocar

- **Marcas, TLDs, acortadores, hosting gratuito y palabras clave:** `linkscanner.detection` en `src/main/resources/application.yml`.
- **Una regla nueva:** una clase `@Component` que implemente `Heuristic`, en `services/heuristics`. Se registra sola.
- **Otro servicio externo:** una clase `@Component` que implemente `ContentScanner`, en `services`. Se registra sola.
