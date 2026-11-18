# Auto Música

Reproductor para el auto: pantalla horizontal, dos botones gigantes (ALEATORIO / SECUENCIAL), reproduce solo al abrir.


## Generar el APK (Capacitor + GitHub Actions)
1. Crea un repositorio privado en GitHub y sube todo este contenido.
2. Pestaña Actions → "Construir APK" → Run workflow (o se ejecuta solo con cada push a main).
3. Descarga el artefacto `auto-musica-apk`, instala `app-debug.apk` en el teléfono.

## Personalización (⚙ Configuración)
- **Estilo de los botones:** Cristal (predeterminado), Aro de luz, Acero, Neón (con anillo de progreso de la canción) y Contraste (con rótulos bajo cada botón). Se combina con los colores e imágenes.
- **Colores:** 6 temas listos (Original, Esmeralda, Rojo, Ámbar, Violeta, Plomo) y 4 selectores propios: fondo, bordes, acento, íconos y texto. «Restablecer colores» vuelve al diseño original.
- **Imágenes de botones:** se puede elegir una imagen de la galería para Anterior, Tocar, Pausa, Siguiente, Aleatorio, Secuencial y el engranaje. «Rellenar el botón» la recorta al círculo; «Como ícono» la muestra completa dentro del botón.
- Todo se guarda en el teléfono (las imágenes se reducen a 640 px).

## Comportamiento
- ALEATORIO / SECUENCIAL cambian el orden sin cortar la pista actual. Anterior en aleatorio vuelve a la pista que sonó antes.
- Al abrir la app retoma el último modo; en secuencial continúa por la última pista.
- Pantalla siempre encendida y modo inmersivo; pausa con llamadas/avisos de navegación (audio focus).
- Probar en navegador: abre `www/index.html` (la carpeta se vuelve a elegir en cada apertura).
