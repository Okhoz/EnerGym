# Foto de Google como botón del menú

El modelo de identidad conserva la URL de foto de Firebase y prioriza la información del proveedor Google vinculado. La lectura sigue el contrato de [Firebase Authentication para perfiles de usuario](https://firebase.google.com/docs/auth/android/manage-users). La cabecera carga esa imagen cuando no existe una foto manual elegida por el usuario. El avatar mantiene su listener de menú y no se añade ningún botón hamburguesa. Si hay una foto manual, la opción «Usar foto de Google» permite retirarla; sin imagen disponible permanece el icono de usuario.

La carga se ejecuta fuera del hilo de interfaz, mediante HTTPS y sin enviar tokens de autenticación. Se limitan redirecciones, esperas, bytes descargados y resolución decodificada. La misma foto no se descarga en cada render. Las respuestas y la lectura de la imagen se validan por UID, revisión de cuenta y URL, incluso mientras los fragmentos tienen el estado guardado. Cerrar la vista cancela su trabajo pendiente. No se cambian las credenciales, datos de entrenamiento, nutrición ni configuración cloud.

Verificación realizada:

- 367 pruebas JVM correctas, incluidas 14 nuevas sobre metadatos de foto, carga asíncrona, cierre, fallos y aislamiento entre cuentas.
- 7 pruebas Android correctas en el emulador: política HTTPS, reducción de imagen, datos inválidos, menú real tras recreación y manejo de fotos manuales.
- APK debug, APK de instrumentación y lintDebug correctos.
- Revisión independiente final sin incidencias pendientes. Se corrigió el caso detectado de una respuesta antigua mientras la actividad conserva su estado.
- APK actualizada en el Samsung del usuario mediante instalación con reemplazo, sin desinstalar ni borrar datos. El usuario inició sesión con Google; se verificó visualmente que su foto real aparece en la cabecera y una pulsación sobre ella abre el menú de perfil, datos físicos y cuenta.

Las capturas del móvil y sus imágenes personales permanecen fuera del repositorio. Sin despliegue a producción.
