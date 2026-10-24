# EnerGym · Ficha y formularios de Google Play

Todo listo para copiar y pegar en Play Console. Los límites de caracteres de Google van entre paréntesis.

## Ficha principal de la tienda

**Nombre de la aplicación** (30)
```
EnerGym: Gimnasio y Dieta IA
```

**Descripción breve** (80)
```
Registra tus entrenos, progresa con IA en tu móvil y controla tu dieta. Gratis.
```

**Descripción completa** (4000)
```
EnerGym es tu cuaderno de gimnasio, tu entrenador y tu dieta en una sola app. Gratis, sin anuncios y con una inteligencia artificial que funciona en tu propio móvil.

💪 ENTRENA Y APUNTA CADA SERIE
• Registra peso y repeticiones serie a serie con botones − y +, o repite la anterior con un toque.
• Series de calentamiento, drop sets y al fallo, esfuerzo (RPE) y notas por ejercicio.
• Superseries y circuitos que pasan solos al siguiente ejercicio.
• Temporizador de descanso que te avisa aunque tengas la pantalla apagada.
• Calculadora de discos y tabla de porcentajes de tu 1RM. Kilos o libras.

📈 SOBRECARGA PROGRESIVA AUTOMÁTICA
Tu rutina sube de nivel sola cuando la cumples: primero repeticiones, luego carga. Tras cada serie, si indicas el esfuerzo, la app te dice si subir, mantener o bajar el peso.

🤖 ENTRENADOR CON IA EN TU MÓVIL
• Pídele una rutina para lo que quieras trabajar y el tiempo que tengas.
• Plan semanal tipo Empuje / Tirón / Pierna según tus días.
• Analiza tu historial: detecta ejercicios estancados, grupos musculares olvidados y molestias que anotes.
• Funciona en el propio móvil: tus mensajes no salen del teléfono.

🏋️ MÁS DE 280 EJERCICIOS
Biblioteca con técnica, búsqueda por nombre, músculo o material (tolera erratas) y vista 3D del movimiento. Crea también tus propios ejercicios.

🥗 NUTRICIÓN SIN COMPLICACIONES
• Escanea el código de barras y solo indica los gramos: calorías y macros al momento.
• Cientos de alimentos habituales y millones de productos de Open Food Facts.
• Comidas guardadas, copiar lo de ayer, control de agua y dieta del mes con IA.

📊 TU PROGRESO DE UN VISTAZO
Calendario, análisis por grupo muscular, récords, logros, peso, medidas corporales y fotos de progreso (solo en tu móvil).

🔒 TUS DATOS SON TUYOS
Copia de seguridad con tu cuenta de Google, exportación a CSV y conexión opcional con Health Connect. Sin anuncios, sin venta de datos y sin rastreo.

Empieza hoy: elige tu objetivo, entrena y deja que EnerGym te ayude a progresar cada semana.
```

**Categoría:** Salud y forma física
**Etiquetas** (elige hasta 5 en la consola): Fitness, Gimnasio, Entrenamiento, Nutrición, Contador de calorías
**Correo de contacto:** magnofit16@gmail.com
**Política de privacidad:** https://okhoz.github.io/EnerGym/privacidad.html

**Gráficos** (en `docs/google-play/graficos/`):
- Icono: `icono-512.png` (512 × 512)
- Gráfico destacado: `destacado-1024x500.png` (1024 × 500)
- Capturas de teléfono: `captura-1.png` … `captura-8.png` (1080 × 1920), en este orden:
  1. Apunta cada serie en segundos · 2. Mira cómo progresas · 3. Bate tus récords · 4. Calorías y macros ·
  5. Ejercicios en 3D · 6. Tu asistente de entreno · 7. Tu constancia de un vistazo · 8. Ponte metas

**Vídeo promocional** (en `docs/google-play/`): `video-promocional.mp4` (1080 × 1920, 40 s). Play Console no admite subir el archivo: pide la URL de un vídeo de YouTube público o no listado, sin anuncios y sin restricción de edad.

## Contenido de la aplicación (formularios)

**Anuncios:** No, la aplicación no contiene anuncios.

**Acceso a la aplicación:** Algunas funciones requieren inicio de sesión. Indica que se puede probar con el botón «Entrar en demo local» de Cuenta… solo si la versión publicada lo incluye; si no, crea una cuenta de Google de prueba y facilita su correo y contraseña a Google en este formulario.

**Clasificación de contenido** (cuestionario IARC): categoría «Utilidad, productividad, comunicación u otros». Responde **No** a violencia, sexo, lenguaje, drogas, apuestas, compras y contenido generado por usuarios compartido. Resultado esperado: PEGI 3 / Para todos.

**Público objetivo:** 16 años o más (no está dirigida a niños).

**App de noticias:** No. **App de salud gubernamental / COVID:** No.

**Aplicaciones de salud (declaración):** Marca «Actividad física y fitness» y «Nutrición y control de peso». No es un dispositivo médico.

### Seguridad de los datos

¿Recoge o comparte datos de usuario? **Sí, recoge** (no comparte).
¿Datos cifrados en tránsito? **Sí.** ¿El usuario puede pedir que se eliminen? **Sí** (correo y borrado local en la app).

| Tipo de dato | Recogido | Compartido | Opcional | Finalidad |
|---|---|---|---|---|
| Información personal › Nombre | Sí | No | No (con cuenta) | Gestión de la cuenta |
| Información personal › Correo electrónico | Sí | No | No (con cuenta) | Gestión de la cuenta |
| Información personal › ID de usuario | Sí | No | No (con cuenta) | Gestión de la cuenta, funcionalidad |
| Salud y forma física › Información de salud (peso, altura, medidas, alergias) | Sí | No | Sí | Funcionalidad de la app, personalización |
| Salud y forma física › Información de forma física (entrenos, series) | Sí | No | No | Funcionalidad de la app, personalización |
| Actividad en apps › Otro contenido generado por el usuario (notas, comidas, rutinas) | Sí | No | No | Funcionalidad de la app |

No marques: ubicación, contactos, fotos y vídeos (las fotos de progreso no salen del móvil), audio, mensajes, archivos, calendario, información financiera, navegación web, identificadores de dispositivo ni diagnósticos.

### Health Connect (declaración de permisos)

Permisos: **WRITE_EXERCISE** y **WRITE_WEIGHT** (solo escritura).
Justificación para pegar:
```
EnerGym escribe en Health Connect los entrenamientos que el usuario termina en la app (hora de inicio y fin y nombre de la rutina) y el peso corporal que anota, para que pueda verlos junto al resto de su actividad en otras apps de salud. La función es opcional y se activa en Cuenta › Preferencias. La app no lee datos de Health Connect y no los usa para publicidad ni los comparte con terceros.
```

## Antes de publicar (pendiente)

1. Subir `video-promocional.mp4` a YouTube (no listado) y pegar la URL en «Vídeo» de la ficha principal.
2. Activar GitHub Pages (Settings › Pages › Deploy from a branch › `codex/energym` › `/docs`).
3. Añadir en la app la opción de **borrar la cuenta y los datos de la nube**: Google Play la exige a las apps con inicio de sesión.
4. Tras subir el primer .aab: copiar la huella SHA-1 de «Firma de apps de Play» en Firebase para que funcione el inicio con Google.
