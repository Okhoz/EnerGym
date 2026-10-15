# Buscador de ejercicios y escáner de alimentos — 15 de octubre de 2026

## Cambios

- Acceso «Buscar ejercicios» desde Entrenamientos, campo con ejemplos y botón de borrado.
- Filtros combinables de disciplina, entorno, material y objetivo, basados en metadatos del catálogo.
- Filtros activos visibles y removibles, contador, paginación y estado vacío con opciones para recuperar resultados.
- Mismo buscador en el editor manual, con ejercicios compatibles por defecto y exploración ampliable sin cambiar el contexto de la rutina.
- Consulta, filtros y posición del cursor conservados al volver de técnica y ante refrescos de sincronización. El estado se separa por cuenta y borrador.
- El escáner de nutrición descarta resultados de cámara o consultas pertenecientes a un formulario/cuenta anterior.
- Base g/ml corregible según la etiqueta, sin convertir masa en volumen. Energía genérica de Open Food Facts interpretada en kJ.
- Los productos desconocidos, fichas incompletas y fallos de conexión permiten introducir los datos de la etiqueta.

## Verificación

- 269 pruebas JVM: cero fallos, errores u omisiones.
- 25 pruebas Android acotadas: filtros y navegación, rutinas manuales, codec/repositorio, restauración inicial, conversación y regresión de foco/cursor.
- La nueva regresión de refresco se reprodujo primero: falló al perder el foco. Tras la corrección conserva foco y selección incluso con callbacks consecutivos.
- Compilación debug y APK de pruebas correctas. Lint: cero errores y 103 avisos; no se afirma que el proyecto esté libre de avisos.
- Lectura óptica mediante Google Code Scanner en el emulador API 37 y consulta HTTPS real de Open Food Facts para EAN-13 3017620422003.
- Prueba adicional con la fotografía aportada por el usuario como objetivo de la cámara virtual: leyó EAN-13 8431876112207 y EnerGym abrió «ICED TEA Sabor limón · Carrefour», envase de 1,5 l y base de 100 ml.
- Vista previa de 250 ml: 53 kcal, proteínas 1,3 g, hidratos 12,5 g y grasas 1,3 g, redondeadas desde la ficha descargada. La ficha sigue siendo editable y debe contrastarse con la etiqueta.
- No se añadieron esas porciones al diario ni se cambiaron cuentas de Google. Se restauró la cámara virtual al finalizar.

## Alcance

Se ha comprobado el escáner en emulador; queda pendiente una prueba física en un teléfono. No todos los productos existen en Open Food Facts ni todas las fichas están completas. El flujo manual permanece disponible. Las fotografías y las herramientas temporales de verificación no forman parte del APK ni del repositorio.

Documentación técnica: [Google Code Scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner), [API de Open Food Facts](https://openfoodfacts.github.io/openfoodfacts-server/api/), [esquema nutricional](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product_nutrition/).
