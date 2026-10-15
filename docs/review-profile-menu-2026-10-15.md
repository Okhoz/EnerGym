# Menú desde la foto de perfil — 15/10/2026

Se descargaron todas las referencias y etiquetas del remoto y se sincronizó dev mediante fast-forward. El remoto ya contenía el avatar como único disparador del menú en la cabecera, sin botón de hamburguesa. Se conserva ese diseño y sus destinos: perfil de entrenamiento, datos físicos y cuenta.

La revisión encontró y corrigió dos fallos de la foto: el uso de ImageDecoder incompatible con Android 8/8.1 y la pérdida de la selección al recrear la actividad antes de restaurar la identidad. BitmapFactory y EXIF de plataforma conservan el recorte central de 320 píxeles; la decodificación está acotada y el reemplazo del archivo es atómico. La selección pendiente valida UID y revisión, espera una identidad confirmada y sobrevive a la recreación del controlador. Los menús abiertos antes de cambiar de cuenta se invalidan. Los permisos de imagen pendientes se liberan al consumir, descartar o terminar la actividad.

Verificación:

- 353 pruebas JVM correctas, incluidas 14 de selección pendiente e invalidación entre cuentas.
- APK debug, APK de instrumentación y lintDebug correctos. El lint inicial detectó los seis errores de API de ImageDecoder y el final ya no los presenta.
- 4 pruebas Android correctas en el emulador API 37: menú desplegable real, navegación y recreación; recorte y JPEG; ocho orientaciones EXIF; conservación ante imagen inválida y archivos separados por propietario.
- Revisión visual del menú y revisión independiente de las correcciones sin incidencias pendientes.
- Las fotos de prueba usan un directorio temporal aislado. No se inició ni cerró sesión ni se modificaron datos personales. No se tocó el teléfono físico conectado.

La compatibilidad API 26/27 se comprobó con lint y revisión de las API utilizadas; no se ejecutó un emulador de esas versiones. El trabajo se limita al menú y a su foto de perfil. Sin despliegue del backend o APK en producción ni cambios de configuración cloud.
