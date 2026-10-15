# Objetivos nutricionales — verificación 15/10/2026

Nutrición muestra el objetivo confirmado: déficit calórico, mantenimiento o superávit calórico, con calorías y gramos diarios de proteínas, hidratos y grasas. El formulario ofrece las tres opciones separadas y una estimación que responde a los datos introducidos. Guardar confirma los cambios; Cancelar descarta el borrador.

El cálculo conserva la fórmula existente de Mifflin-St Jeor y el factor de actividad, con un ajuste orientativo de −15 % para déficit y +10 % para superávit. Al registrar un nuevo peso, recalcula calorías y macros manteniendo la modalidad elegida. Tras restaurar datos, utiliza el último peso válido fechado hasta hoy y muestra su fecha de referencia. Elegir otra fecha del diario no altera ese peso de referencia. Consultar o previsualizar no genera escrituras adicionales ni modifica alimentos históricos.

El cálculo automático se limita a adultos y rechaza estimaciones demasiado bajas o con reparto energético incoherente; los perfiles antiguos y el diario siguen siendo legibles. Es una estimación basada en el peso declarado, no una evaluación clínica ni un ajuste automático a partir de tendencias metabólicas.

Consultar mi alimentación con IA prepara un mensaje editable con el objetivo confirmado y los totales del día indicado. El usuario revisa y envía el mensaje; abrir la consulta no envía datos ni registra alimentos automáticamente.

Verificación en el proyecto real:

- 320 pruebas JVM correctas, incluidas 13 nuevas sobre objetivos, pesos, restauración, cancelación y aislamiento entre cuentas.
- Compilación de APK debug, APK de instrumentación y lintDebug correctas.
- 37 pruebas Android correctas sobre navegación, buscador, persistencia, conversación y edición de rutinas y alimentos.
- Revisión visual de las opciones, vista previa y cancelación: el objetivo confirmado permanece intacto al cancelar, sin guardar cambios en la cuenta real.
- Revisión independiente del bloque de nutrición sin incidencias pendientes.
- La verificación del bloque previo mantiene 98 pruebas backend y 16 pruebas Firestore Emulator correctas; este bloque no cambia esos componentes.

Por indicación expresa del usuario se detuvo el despliegue. El backend y la APK no se publicaron en producción; la configuración de Render se abandonó sin crear el servicio. La integración online queda preparada en código, pendiente de desplegar y verificar de extremo a extremo. Las reglas Firestore y las credenciales IAM autorizadas se habían configurado antes de esta instrucción; no se realizaron nuevos cambios en la nube después. Las credenciales permanecen fuera de Git.
