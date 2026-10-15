"""Segunda tanda del catálogo: gimnasio, material en casa y peso corporal.

Se importa desde catalog_expansion.py (MORE). Formato de cada fila igual que EXERCISES:
(id, nombre, métrica, objetivos, entornos, recursos, deportes, cobertura, alternativas, técnica)
"""

STR = ["STRENGTH", "GENERAL_FITNESS"]
MOB = ["MOBILITY", "GENERAL_FITNESS"]
END = ["ENDURANCE", "GENERAL_FITNESS"]
LIFT = ["strength", "calisthenics"]
SPORTS = ["football", "basketball", "other", "running", "cycling", "swimming"]
ALL = ["HOME", "GYM", "OUTDOORS"]
HG = ["HOME", "GYM"]
GYM = ["GYM"]


def gym(id_, name, technique, alts=(), metric="STRENGTH", resources=("MACHINE",), goals=STR):
	return (id_, name, metric, goals, GYM, list(resources), LIFT, "SPECIFIC", list(alts), technique)


def home(id_, name, technique, resources, alts=(), metric="STRENGTH", envs=HG, goals=STR):
	return (id_, name, metric, goals, envs, list(resources), LIFT, "SPECIFIC", list(alts), technique)


def body(id_, name, technique, alts=(), metric="STRENGTH", envs=ALL, resources=(), goals=STR, sports=LIFT):
	return (id_, name, metric, goals, envs, list(resources), sports, "SPECIFIC", list(alts), technique)


def mob(id_, name, technique, resources=(), envs=ALL, alts=()):
	return (id_, name, "MOBILITY", MOB, envs, list(resources), LIFT + ["running", "cycling", "swimming"], "SPECIFIC",
		list(alts), technique)


def prep(id_, name, technique, metric="STRENGTH", goals=STR + ["SPORT_PRACTICE"]):
	return (id_, name, metric, goals, ALL, [], SPORTS, "COMPLEMENTARY", [], technique)


MORE = [
	# ---------- Máquinas y poleas ----------
	gym("smith-squat", "Sentadilla en máquina Smith", "Barra guiada sobre la espalda alta y pies algo adelantados. Baja con el tronco firme hasta donde mantengas la espalda neutra y sube empujando con todo el pie. Usa los topes de seguridad.", ["barbell-back-squat", "leg-press"]),
	gym("smith-bench-press", "Press de banca en máquina Smith", "Tumbado con la barra guiada sobre la parte baja del pecho. Escápulas juntas, baja controlado y empuja sin rebotar.", ["barbell-bench-press", "machine-chest-press"], resources=("MACHINE", "BENCH")),
	gym("hack-squat", "Sentadilla hack", "Espalda apoyada en el respaldo y pies a la anchura de caderas. Baja hasta que los muslos queden paralelos o lo que permita tu movilidad y sube sin bloquear las rodillas.", ["leg-press", "smith-squat"]),
	gym("seated-leg-curl", "Curl femoral sentado", "Rodilla alineada con el eje y rodillo sobre los tobillos. Flexiona llevando los talones hacia abajo y atrás, pausa y vuelve lento.", ["leg-curl"]),
	gym("machine-hip-adduction", "Aducción de cadera en máquina", "Sentado con las almohadillas por dentro de las rodillas. Junta las piernas contra la resistencia, pausa y abre controlado.", ["machine-hip-abduction"]),
	gym("machine-hip-thrust", "Hip thrust en máquina", "Espalda apoyada y cinturón o almohadilla sobre la cadera. Empuja con los talones hasta alinear rodillas, cadera y hombros y aprieta glúteos arriba.", ["barbell-hip-thrust", "glute-bridge"]),
	gym("machine-glute-kickback", "Patada de glúteo en máquina", "Tronco apoyado y pie en la plataforma. Extiende la cadera llevando la pierna atrás sin arquear la zona lumbar y vuelve controlado.", ["glute-kickback"]),
	gym("seated-calf-raise", "Gemelo sentado", "Almohadilla sobre los muslos y punta de los pies en el borde. Baja los talones y sube al máximo. Trabaja el sóleo.", ["machine-calf-raise"]),
	gym("machine-shoulder-press", "Press de hombros en máquina", "Agarres a la altura de los hombros y espalda apoyada. Empuja hacia arriba sin bloquear y baja hasta la altura de las orejas.", ["dumbbell-shoulder-press"]),
	gym("machine-lateral-raise", "Elevación lateral en máquina", "Brazos contra las almohadillas. Eleva hasta la altura de los hombros sin encogerlos y baja lento.", ["dumbbell-lateral-raise"]),
	gym("reverse-pec-deck", "Pájaros en máquina (contractor inverso)", "Pecho contra el respaldo y brazos al frente. Abre hacia atrás juntando escápulas y vuelve controlado. Deltoides posterior.", ["dumbbell-reverse-fly", "cable-face-pull"]),
	gym("machine-row", "Remo en máquina con apoyo de pecho", "Pecho apoyado y agarres al frente. Tira llevando los codos atrás y juntando escápulas, sin despegar el pecho.", ["seated-cable-row"]),
	gym("cable-crossover", "Cruce de poleas", "De pie entre dos poleas altas, un paso adelante. Junta las manos delante del pecho en arco con codos semiflexionados y abre controlado.", ["pec-deck", "dumbbell-fly"]),
	gym("cable-low-fly", "Aperturas en polea baja", "Poleas abajo. Lleva las manos de abajo hacia arriba hasta la altura del pecho en arco. Enfatiza el pecho superior.", ["cable-crossover"]),
	gym("cable-lateral-raise", "Elevación lateral en polea", "De lado a la polea baja, agarre con la mano contraria. Eleva el brazo hasta la altura del hombro y baja lento manteniendo la tensión.", ["dumbbell-lateral-raise"]),
	gym("cable-overhead-triceps", "Extensión de tríceps sobre la cabeza en polea", "De espaldas a la polea con la cuerda tras la cabeza. Extiende los codos hacia delante y arriba sin mover los hombros.", ["dumbbell-overhead-triceps"]),
	gym("cable-crunch", "Crunch en polea", "De rodillas frente a la polea alta con la cuerda junto a la cabeza. Flexiona el tronco llevando los codos a los muslos y vuelve lento.", ["crunch"]),
	gym("cable-pull-through", "Pull-through en polea", "De espaldas a la polea baja con la cuerda entre las piernas. Bisagra de cadera atrás y extiende apretando glúteos.", ["barbell-romanian-deadlift"]),
	gym("cable-glute-kickback", "Patada de glúteo en polea", "Tobillera en polea baja. Con el tronco algo inclinado, lleva la pierna atrás extendiendo la cadera y vuelve.", ["glute-kickback"]),
	gym("straight-arm-pulldown", "Jalón con brazos rectos", "Frente a la polea alta con brazos casi estirados. Lleva la barra hasta los muslos en arco activando dorsales y vuelve.", ["lat-pulldown"]),
	gym("close-grip-pulldown", "Jalón agarre estrecho", "Agarre neutro estrecho. Tira hacia la parte alta del pecho llevando los codos pegados al cuerpo y sube controlado.", ["lat-pulldown"]),
	gym("machine-preacher-curl", "Curl predicador en máquina", "Brazos sobre el apoyo. Flexiona los codos sin despegarlos y baja hasta casi extender.", ["dumbbell-biceps-curl"]),
	gym("machine-dip", "Fondos en máquina", "Sentado con los agarres a los lados. Empuja hacia abajo extendiendo los codos y vuelve controlado.", ["parallel-bar-dips", "bench-dips"]),
	gym("back-extension", "Hiperextensiones lumbares", "Cadera apoyada en el banco romano. Baja el tronco con la espalda neutra y sube hasta alinear tronco y piernas, sin pasar de esa línea.", ["superman", "barbell-romanian-deadlift"]),
	gym("machine-ab-crunch", "Crunch abdominal en máquina", "Sentado con los agarres en el pecho. Flexiona el tronco contra la resistencia y vuelve lento.", ["crunch"]),
	gym("treadmill-incline-walk", "Caminata inclinada en cinta", "Cinta con 8–12 % de pendiente a paso vivo, sin agarrarte a las barras. Registra minutos y distancia.", ["brisk-walk"], metric="ENDURANCE", goals=END),
	gym("stair-climber", "Escaladora", "Postura erguida y pasos completos. Mantén un ritmo constante o por intervalos sin apoyar el peso en los brazos.", ["treadmill-incline-walk"], metric="ENDURANCE", goals=END),
	gym("air-bike", "Bicicleta de aire", "Empuja y tira de los brazos mientras pedaleas. Ideal para intervalos cortos e intensos.", ["stationary-bike"], metric="ENDURANCE", goals=END),
	gym("ski-erg", "Ski erg", "Tira de los agarres desde arriba flexionando cadera y tronco como un esquiador de fondo y vuelve a la extensión.", ["rowing-machine"], metric="ENDURANCE", goals=END),
	# ---------- Barra ----------
	gym("barbell-sumo-deadlift", "Peso muerto sumo", "Pies muy abiertos y manos por dentro de las rodillas. Pecho arriba, empuja el suelo y sube la barra pegada al cuerpo.", ["barbell-deadlift"], resources=("BARBELL",)),
	gym("barbell-good-morning", "Buenos días con barra", "Barra en la espalda alta, rodillas suaves. Lleva la cadera atrás inclinando el tronco con la espalda neutra y vuelve. Carga ligera.", ["barbell-romanian-deadlift"], resources=("BARBELL",)),
	gym("barbell-lunge", "Zancada con barra", "Barra en la espalda alta. Paso adelante y baja hasta que la rodilla trasera casi roce el suelo; vuelve empujando con la pierna delantera.", ["dumbbell-walking-lunge"], resources=("BARBELL",)),
	gym("pendlay-row", "Remo Pendlay", "Tronco paralelo al suelo y barra apoyada en cada repetición. Tira explosivo hacia el abdomen y devuelve la barra al suelo.", ["barbell-row"], resources=("BARBELL",)),
	gym("t-bar-row", "Remo en T", "Barra anclada en una esquina o soporte. Bisagra de cadera y tira del extremo hacia el pecho juntando escápulas.", ["barbell-row"], resources=("BARBELL",)),
	gym("barbell-upright-row", "Remo al mentón con barra", "Agarre a la anchura de hombros. Sube la barra pegada al cuerpo hasta el pecho llevando los codos arriba, sin pasar de la altura de los hombros.", ["dumbbell-lateral-raise"], resources=("BARBELL",)),
	gym("barbell-skull-crusher", "Press francés con barra", "Tumbado en banco con la barra sobre la frente. Flexiona solo los codos y extiende sin abrirlos.", ["dumbbell-overhead-triceps"], resources=("BARBELL", "BENCH")),
	gym("barbell-shrug", "Encogimientos con barra", "Barra delante de los muslos. Eleva los hombros hacia las orejas, pausa y baja.", ["dumbbell-shrug"], resources=("BARBELL",)),
	gym("barbell-floor-press", "Press en el suelo con barra", "Tumbado en el suelo, baja la barra hasta que los codos toquen el suelo y empuja. Limita el recorrido para cuidar el hombro.", ["barbell-bench-press"], resources=("BARBELL",)),
	gym("barbell-push-press", "Push press", "Barra en los hombros. Pequeña flexión de rodillas y extiende piernas y brazos a la vez para llevar la barra arriba.", ["barbell-overhead-press"], resources=("BARBELL",)),
	gym("power-clean", "Cargada de potencia", "Desde el suelo, extiende piernas y cadera con fuerza, encoge y recibe la barra sobre los hombros con los codos al frente. Técnica avanzada: aprende con poco peso.", ["barbell-deadlift"], resources=("BARBELL",)),
	gym("landmine-press", "Press landmine", "Barra anclada en un extremo. Empuja el otro extremo desde el hombro hacia delante y arriba con el tronco firme.", ["dumbbell-shoulder-press"], resources=("BARBELL",)),
	gym("barbell-calf-raise", "Gemelo de pie con barra", "Barra en la espalda alta. Sube a la punta de los pies, pausa y baja lento.", ["dumbbell-calf-raise"], resources=("BARBELL",)),
	gym("barbell-reverse-curl", "Curl inverso con barra", "Agarre prono a la anchura de hombros. Flexiona los codos sin moverlos. Trabaja antebrazo y braquial.", ["dumbbell-hammer-curl"], resources=("BARBELL",)),
	gym("barbell-wrist-curl", "Curl de muñeca con barra", "Antebrazos apoyados en los muslos y palmas arriba. Flexiona solo las muñecas y baja controlado.", [], resources=("BARBELL",)),
	# ---------- Mancuernas ----------
	home("dumbbell-deadlift", "Peso muerto con mancuernas", "Mancuernas junto a los pies. Espalda neutra, empuja el suelo y sube extendiendo cadera y rodillas.", ["DUMBBELLS"], ["barbell-deadlift", "dumbbell-romanian-deadlift"]),
	home("dumbbell-sumo-squat", "Sentadilla sumo con mancuerna", "Pies muy abiertos y puntas hacia fuera con una mancuerna colgando entre las piernas. Baja con el torso erguido y sube apretando glúteos.", ["DUMBBELLS"], ["dumbbell-goblet-squat"]),
	home("dumbbell-static-lunge", "Zancada estática con mancuernas", "Posición de zancada fija con una mancuerna en cada mano. Baja en vertical y sube sin mover los pies.", ["DUMBBELLS"], ["split-squat"]),
	home("dumbbell-lateral-lunge", "Zancada lateral con mancuerna", "Mancuerna en el pecho. Paso lateral amplio llevando la cadera atrás y vuelve al centro.", ["DUMBBELLS"], ["side-lunge"]),
	home("dumbbell-single-leg-rdl", "Peso muerto a una pierna con mancuerna", "Mancuerna en la mano contraria a la pierna de apoyo. Inclina el tronco llevando la otra pierna atrás y vuelve con la cadera nivelada.", ["DUMBBELLS"], ["dumbbell-romanian-deadlift"]),
	home("dumbbell-hip-thrust", "Hip thrust con mancuerna", "Escápulas en el banco y mancuerna sobre la cadera. Empuja con los talones hasta alinear el cuerpo.", ["DUMBBELLS", "BENCH"], ["barbell-hip-thrust"]),
	home("dumbbell-chest-supported-row", "Remo con apoyo en banco inclinado", "Pecho sobre un banco inclinado y mancuernas colgando. Tira llevando los codos atrás y baja estirando.", ["DUMBBELLS", "BENCH"], ["dumbbell-row"]),
	home("renegade-row", "Remo renegado", "En plancha alta sobre dos mancuernas. Rema con un brazo sin girar la cadera y alterna.", ["DUMBBELLS"], ["dumbbell-row", "forearm-plank"]),
	home("dumbbell-upright-row", "Remo al mentón con mancuernas", "Mancuernas delante de los muslos. Súbelas pegadas al cuerpo hasta el pecho con los codos altos.", ["DUMBBELLS"], ["barbell-upright-row"]),
	home("dumbbell-front-raise", "Elevación frontal", "Brazos estirados delante de los muslos. Eleva al frente hasta la altura de los hombros y baja lento.", ["DUMBBELLS"], ["dumbbell-lateral-raise"]),
	home("dumbbell-concentration-curl", "Curl concentrado", "Sentado con el codo apoyado en el interior del muslo. Flexiona el codo y baja controlado.", ["DUMBBELLS"], ["dumbbell-biceps-curl"]),
	home("dumbbell-incline-curl", "Curl inclinado", "Sentado en banco inclinado con brazos colgando. Flexiona sin adelantar los codos.", ["DUMBBELLS", "BENCH"], ["dumbbell-biceps-curl"]),
	home("dumbbell-zottman-curl", "Curl Zottman", "Sube con palmas arriba, gira arriba a palmas abajo y baja lento. Bíceps y antebrazo.", ["DUMBBELLS"], ["dumbbell-hammer-curl"]),
	home("dumbbell-skull-crusher", "Press francés con mancuernas", "Tumbado con mancuernas sobre el pecho. Flexiona los codos llevándolas junto a la cabeza y extiende.", ["DUMBBELLS", "BENCH"], ["barbell-skull-crusher"]),
	home("dumbbell-close-grip-press", "Press cerrado con mancuernas", "Tumbado, mancuernas juntas con agarre neutro. Baja al pecho con los codos pegados y empuja.", ["DUMBBELLS", "BENCH"], ["barbell-close-grip-press"]),
	home("dumbbell-snatch", "Arrancada con mancuerna", "Mancuerna entre los pies. Extiende cadera con fuerza y llévala por encima de la cabeza en un solo movimiento. Técnica avanzada.", ["DUMBBELLS"], ["dumbbell-thruster"]),
	home("dumbbell-clean-press", "Cargada y press con mancuernas", "Sube las mancuernas a los hombros con un impulso de cadera y empújalas por encima de la cabeza.", ["DUMBBELLS"], ["dumbbell-thruster"]),
	home("farmers-walk", "Paseo del granjero", "Una mancuerna pesada en cada mano, hombros abajo y tronco erguido. Camina con pasos cortos y controlados.", ["DUMBBELLS"], ["suitcase-carry"], envs=ALL),
	home("suitcase-carry", "Paseo de maleta", "Una sola mancuerna a un lado. Camina sin inclinarte hacia el peso. Trabaja oblicuos y agarre.", ["DUMBBELLS"], ["farmers-walk"], envs=ALL),
	home("dumbbell-side-bend", "Inclinación lateral con mancuerna", "De pie con una mancuerna a un lado. Inclina el tronco hacia ese lado y vuelve con el oblicuo contrario.", ["DUMBBELLS"], ["side-plank"]),
	home("dumbbell-russian-twist", "Giro ruso con mancuerna", "Sentado con el tronco inclinado y pies apoyados o elevados. Gira llevando la mancuerna de un lado a otro.", ["DUMBBELLS", "MAT"], ["russian-twist"]),
	home("dumbbell-swing", "Swing con mancuerna", "Sujeta la mancuerna con ambas manos. Bisagra de cadera atrás y proyecta la cadera para balancearla hasta el pecho.", ["DUMBBELLS"], ["cable-pull-through"]),
	home("dumbbell-woodchop", "Leñador con mancuerna", "Mancuerna con ambas manos junto a una cadera. Llévala en diagonal por encima del hombro contrario girando el tronco.", ["DUMBBELLS"], ["cable-woodchop"]),
	# ---------- Bandas ----------
	home("band-squat", "Sentadilla con banda", "Pisa la banda y sujeta los extremos en los hombros. Haz una sentadilla manteniendo la tensión.", ["BANDS"], ["bodyweight-squat"], envs=ALL),
	home("band-deadlift", "Peso muerto con banda", "Pisa la banda y sujeta los extremos. Bisagra de cadera y sube extendiendo cadera.", ["BANDS"], ["dumbbell-deadlift"], envs=ALL),
	home("band-good-morning", "Buenos días con banda", "Banda bajo los pies y tras el cuello. Lleva la cadera atrás inclinando el tronco y vuelve.", ["BANDS"], ["barbell-good-morning"], envs=ALL),
	home("band-glute-bridge", "Puente de glúteo con banda", "Banda sobre las rodillas. Haz el puente empujando las rodillas hacia fuera.", ["BANDS", "MAT"], ["glute-bridge"]),
	home("band-kickback", "Patada de glúteo con banda", "Banda anclada al tobillo. Lleva la pierna atrás extendiendo la cadera.", ["BANDS"], ["glute-kickback"], envs=ALL),
	home("band-lat-pulldown", "Jalón con banda", "Banda anclada arriba. Tira hacia el pecho llevando los codos abajo.", ["BANDS"], ["lat-pulldown"], envs=ALL),
	home("band-overhead-press", "Press de hombros con banda", "Pisa la banda y empuja los extremos por encima de la cabeza.", ["BANDS"], ["dumbbell-shoulder-press"], envs=ALL),
	home("band-pallof-press", "Press Pallof", "De lado al anclaje, banda en el pecho. Extiende los brazos al frente sin dejar que el tronco gire y vuelve. Antirrotación.", ["BANDS"], ["cable-woodchop"], envs=ALL),
	home("band-hamstring-curl", "Curl femoral con banda", "Boca abajo con la banda en los tobillos anclada delante. Flexiona las rodillas llevando talones al glúteo.", ["BANDS", "MAT"], ["leg-curl"]),
	home("band-clamshell", "Almeja con banda", "Tumbado de lado con rodillas flexionadas y banda sobre ellas. Abre la rodilla de arriba sin girar la pelvis.", ["BANDS", "MAT"], ["band-lateral-walk"]),
	# ---------- Peso corporal: piernas y glúteo ----------
	body("bodyweight-sumo-squat", "Sentadilla sumo", "Pies muy abiertos y puntas hacia fuera. Baja con el torso erguido y sube apretando glúteos.", ["bodyweight-squat"]),
	body("pulse-squat", "Sentadilla con rebotes cortos", "Baja a media sentadilla y haz pequeños rebotes sin subir del todo. Mantén la tensión en piernas.", ["bodyweight-squat"]),
	body("cossack-squat", "Sentadilla cosaca", "Piernas muy abiertas. Baja sobre una pierna mientras la otra queda estirada con la punta arriba y cambia de lado.", ["side-lunge"]),
	body("pistol-squat-assisted", "Sentadilla a una pierna asistida", "Sujétate a un apoyo y baja sobre una pierna con la otra adelantada. Sube sin perder el control. Progresión hacia la pistol.", ["split-squat"], resources=("SUPPORT",), envs=HG),
	body("curtsy-lunge", "Zancada cruzada", "Lleva una pierna en diagonal por detrás de la otra y baja como en una reverencia. Vuelve y alterna.", ["reverse-lunge"]),
	body("single-leg-calf-raise", "Gemelo a una pierna", "Apoyado ligeramente, sube a la punta de un pie y baja en tres segundos.", ["supported-calf-raise"], resources=("SUPPORT",), envs=HG),
	body("glute-bridge-march", "Puente con marcha", "En puente, levanta alternativamente un pie del suelo sin que la cadera caiga.", ["glute-bridge"]),
	body("frog-pump", "Frog pump", "Tumbado con plantas de los pies juntas y rodillas abiertas. Eleva la cadera apretando glúteos en repeticiones rápidas.", ["glute-bridge"]),
	body("donkey-kick", "Patada de burro", "A cuatro patas, con la rodilla a 90° empuja la planta del pie hacia el techo y vuelve.", ["glute-kickback"]),
	body("fire-hydrant", "Fire hydrant", "A cuatro patas, abre la rodilla hacia el lado sin girar la pelvis y vuelve. Glúteo medio.", ["band-clamshell"]),
	body("clamshell", "Almeja", "Tumbado de lado con rodillas flexionadas. Abre la rodilla de arriba manteniendo los pies juntos.", ["band-clamshell"], resources=("MAT",), envs=HG),
	body("jump-lunge", "Zancadas con salto", "Desde zancada, salta y cambia de pierna en el aire aterrizando suave. Requiere buen control.", ["reverse-lunge"]),
	body("tuck-jump", "Salto con rodillas al pecho", "Salta llevando las rodillas al pecho y aterriza suave con las rodillas alineadas.", ["jump-squat"]),
	body("box-jump-low", "Salto a escalón bajo", "Salta con ambos pies a un escalón estable y bajo, aterriza suave y baja andando. Solo superficies seguras.", ["jump-squat"], envs=["GYM", "OUTDOORS"]),
	# ---------- Peso corporal: empuje y tracción ----------
	body("wide-pushup", "Flexión con agarre ancho", "Manos más abiertas que los hombros y cuerpo en línea. Baja el pecho y empuja. Más pecho.", ["standard-pushup"]),
	body("decline-pushup", "Flexión declinada", "Pies sobre una silla estable y manos en el suelo. Baja con el cuerpo recto y empuja. Más hombro y pecho superior.", ["pike-pushup", "standard-pushup"], resources=("CHAIR",), envs=HG),
	body("incline-pushup", "Flexión inclinada", "Manos sobre una silla estable o un banco y cuerpo recto. Más fácil que la flexión en el suelo.", ["knee-pushup", "wall-pushup"], resources=("CHAIR",), envs=HG),
	body("archer-pushup", "Flexión arquero", "Manos muy abiertas; baja hacia un lado manteniendo el otro brazo estirado y alterna. Avanzado.", ["wide-pushup"]),
	body("hindu-pushup", "Flexión hindú", "Desde perro boca abajo, baja el pecho rozando el suelo en arco y termina en cobra; vuelve.", ["pike-pushup"]),
	body("negative-pull-up", "Dominada negativa", "Requiere barra fija. Sube con ayuda de un salto o apoyo y baja en 3–5 segundos con control.", ["pull-up", "assisted-pull-up"], envs=["GYM", "OUTDOORS"]),
	body("scapular-pull-up", "Dominada escapular", "Colgado de la barra con brazos estirados, baja y junta las escápulas elevando el cuerpo unos centímetros.", ["dead-hang"], envs=["GYM", "OUTDOORS"]),
	body("hanging-knee-raise", "Elevación de rodillas colgado", "Colgado de una barra fija, sube las rodillas al pecho sin balanceo y baja lento.", ["lying-leg-raise"], envs=["GYM", "OUTDOORS"]),
	body("hanging-leg-raise", "Elevación de piernas colgado", "Colgado de la barra, sube las piernas estiradas hasta la horizontal o más y baja controlado.", ["hanging-knee-raise"], envs=["GYM", "OUTDOORS"]),
	body("muscle-up", "Muscle-up", "En barra fija: dominada explosiva que pasa por encima de la barra y termina con un fondo. Muy avanzado.", ["pull-up", "parallel-bar-dips"], envs=["GYM", "OUTDOORS"]),
	body("wall-handstand-hold", "Pino contra la pared", "Manos a un palmo de la pared y piernas apoyadas. Empuja el suelo con brazos estirados y mantén. Avanzado.", ["pike-pushup"], metric="ISOMETRIC", resources=("WALL",), envs=HG),
	body("l-sit", "L-sit", "En paralelas o dos apoyos estables, eleva el cuerpo y mantén las piernas estiradas al frente.", ["hollow-hold"], metric="ISOMETRIC", resources=("SUPPORT",), envs=HG),
	body("reverse-plank", "Plancha invertida", "Sentado con manos detrás, eleva la cadera hasta alinear el cuerpo mirando al techo y mantén.", ["forearm-plank"], metric="ISOMETRIC"),
	# ---------- Abdominales y tronco ----------
	body("plank-shoulder-taps", "Plancha con toques de hombro", "En plancha alta, toca el hombro contrario alternando manos sin balancear la cadera.", ["forearm-plank"]),
	body("plank-jacks", "Plancha con saltos", "En plancha, abre y cierra las piernas con pequeños saltos manteniendo la cadera estable.", ["mountain-climbers"]),
	body("bear-crawl", "Gateo del oso", "A cuatro patas con rodillas a un palmo del suelo. Avanza mano y pie contrarios a la vez sin subir la cadera.", ["mountain-climbers"]),
	body("inchworm", "Gusano", "De pie, apoya las manos y camina con ellas hasta la plancha; vuelve caminando hacia los pies y levántate.", ["worlds-greatest-stretch"]),
	body("flutter-kicks", "Patadas de tijera vertical", "Boca arriba con zona lumbar pegada, alterna pequeñas patadas arriba y abajo con piernas estiradas.", ["lying-leg-raise"], resources=("MAT",), envs=HG),
	body("scissor-kicks", "Tijeras", "Boca arriba con piernas elevadas, crúzalas horizontalmente alternando arriba y abajo.", ["flutter-kicks"], resources=("MAT",), envs=HG),
	body("v-up", "V-up", "Tumbado estirado, sube a la vez tronco y piernas hasta tocar los pies formando una V y baja controlado.", ["crunch"], resources=("MAT",), envs=HG),
	body("russian-twist", "Giro ruso", "Sentado con el tronco inclinado, gira llevando las manos de un lado a otro de la cadera.", ["bicycle-crunch"], resources=("MAT",), envs=HG),
	body("heel-touches", "Toques de talón", "Tumbado con rodillas flexionadas, eleva hombros y lleva la mano a cada talón alternando.", ["crunch"], resources=("MAT",), envs=HG),
	body("reverse-crunch", "Crunch inverso", "Tumbado con rodillas a 90°, lleva las rodillas al pecho despegando ligeramente la cadera y baja lento.", ["lying-leg-raise"], resources=("MAT",), envs=HG),
	body("toe-touch-crunch", "Toques de punta", "Piernas estiradas hacia el techo; eleva el tronco intentando tocar las puntas de los pies.", ["crunch"], resources=("MAT",), envs=HG),
	body("side-plank-dips", "Plancha lateral con elevaciones", "En plancha lateral, baja la cadera unos centímetros y súbela. Alterna lados.", ["side-plank"]),
	body("stir-the-pot", "Plancha con círculos", "En plancha de antebrazos, dibuja pequeños círculos con los codos sin mover la cadera.", ["forearm-plank"], metric="ISOMETRIC", resources=("MAT",), envs=HG),
	# ---------- Cardio sin material ----------
	body("high-knees", "Rodillas arriba", "Corre en el sitio subiendo las rodillas a la altura de la cadera y braceando.", ["jumping-jacks"], metric="ENDURANCE", goals=END, sports=LIFT + ["running"]),
	body("butt-kicks", "Talones al glúteo", "Corre en el sitio llevando los talones hacia los glúteos con ritmo ágil.", ["high-knees"], metric="ENDURANCE", goals=END, sports=LIFT + ["running"]),
	body("shadow-boxing", "Boxeo de sombra", "Guardia alta y golpes al aire (directos, crochés y uppercuts) con desplazamientos. Controla el gesto sin bloquear los codos.", ["jumping-jacks"], metric="ENDURANCE", goals=END),
	body("squat-thrust", "Squat thrust", "Agáchate, lleva los pies atrás a plancha y vuelve a la sentadilla. Un burpee sin salto.", ["burpee"], metric="ENDURANCE", goals=END),
	body("skater-jumps", "Saltos de patinador", "Salta lateralmente de un pie al otro aterrizando suave y con la rodilla alineada.", ["jump-squat"], metric="ENDURANCE", goals=END),
	body("stair-run", "Subir escaleras", "Sube escaleras a buen ritmo pisando con todo el pie y baja andando para recuperar.", ["running-intervals"], metric="ENDURANCE", goals=END, envs=["OUTDOORS"], sports=["running"]),
	body("fartlek-run", "Fartlek", "Carrera continua alternando cambios de ritmo libres (rápido–suave) según el terreno o sensaciones.", ["running-intervals"], metric="ENDURANCE", goals=END, envs=["OUTDOORS"], sports=["running"]),
	body("long-run", "Tirada larga", "Carrera continua larga a ritmo cómodo que permita conversar. Base aeróbica.", ["running-easy"], metric="ENDURANCE", goals=END, envs=["OUTDOORS"], sports=["running"]),
	body("hill-sprints", "Cuestas cortas", "Sprints de 10–20 s cuesta arriba con recuperación caminando hacia abajo.", ["running-intervals"], metric="ENDURANCE", goals=END, envs=["OUTDOORS"], sports=["running"]),
	("cycling-hills", "Subidas en bicicleta", "ENDURANCE", END, ["OUTDOORS"], ["BICYCLE"], ["cycling"], "SPECIFIC", ["cycling-intervals"], "Busca una subida segura y alterna tramos sentado y de pie, manteniendo una cadencia controlada. Casco obligatorio."),
	("swimming-backstroke", "Espalda (natación)", "ENDURANCE", END, ["POOL"], ["POOL"], ["swimming"], "SPECIFIC", ["swimming-easy"], "Cuerpo horizontal boca arriba, brazada alterna entrando por encima del hombro y patada constante."),
	("aqua-jogging", "Correr en el agua", "ENDURANCE", END, ["POOL"], ["POOL"], ["swimming", "running"], "SPECIFIC", ["swimming-easy"], "Corre en la piscina sin tocar el fondo o con el agua a la cintura, con braceo de carrera. Bajo impacto."),
	# ---------- Movilidad y estiramientos ----------
	mob("wall-angels", "Ángeles en la pared", "Espalda, cabeza y brazos contra la pared. Desliza los brazos arriba y abajo sin despegarlos.", resources=("WALL",), envs=HG),
	mob("doorway-chest-stretch", "Estiramiento de pecho en marco de puerta", "Antebrazo apoyado en el marco a la altura del hombro. Avanza el cuerpo hasta notar estiramiento en el pecho.", resources=("WALL",), envs=HG),
	mob("quad-stretch", "Estiramiento de cuádriceps", "De pie con apoyo, lleva el talón al glúteo sujetando el tobillo y mantén las rodillas juntas."),
	mob("figure-four-stretch", "Estiramiento del glúteo en 4", "Boca arriba, cruza un tobillo sobre la rodilla contraria y acerca esa pierna al pecho.", resources=("MAT",), envs=HG),
	mob("cobra-stretch", "Cobra", "Boca abajo, apoya las manos bajo los hombros y eleva el pecho estirando el abdomen, sin forzar la zona lumbar.", resources=("MAT",), envs=HG),
	mob("downward-dog", "Perro boca abajo", "Desde cuatro apoyos, eleva la cadera formando una V invertida y empuja los talones hacia el suelo.", resources=("MAT",), envs=HG),
	mob("thread-the-needle", "Enhebrar la aguja", "A cuatro patas, pasa un brazo por debajo del otro girando el tronco y vuelve abriéndolo al techo.", resources=("MAT",), envs=HG),
	mob("hip-circles", "Círculos de cadera", "De pie con manos en la cintura, dibuja círculos amplios con la cadera en ambos sentidos."),
	mob("arm-circles", "Círculos de brazos", "Brazos en cruz, haz círculos de pequeños a grandes hacia delante y hacia atrás."),
	mob("leg-swings", "Balanceos de pierna", "Con apoyo, balancea una pierna adelante y atrás y luego lateralmente, sin forzar el final."),
	mob("shoulder-rolls", "Rotaciones de hombros", "Lleva los hombros arriba, atrás y abajo en círculos lentos."),
	mob("wrist-mobility", "Movilidad de muñecas", "A cuatro apoyos o de pie, rota las muñecas y balancea el peso suavemente sobre las manos."),
	mob("frog-stretch", "Estiramiento de rana", "A cuatro patas con las rodillas muy abiertas, lleva la cadera atrás hasta notar estiramiento en los aductores.", resources=("MAT",), envs=HG),
	mob("couch-stretch", "Estiramiento del sofá", "Rodilla trasera junto a la pared con el empeine apoyado en ella y pie delantero adelante. Eleva el tronco para estirar el cuádriceps.", resources=("WALL", "MAT"), envs=HG),
	mob("seated-forward-fold", "Flexión sentada", "Sentado con piernas estiradas, inclina el tronco desde la cadera hacia los pies con la espalda larga.", resources=("MAT",), envs=HG),
	mob("butterfly-stretch", "Mariposa", "Sentado con las plantas de los pies juntas, deja caer las rodillas y lleva el tronco suavemente adelante.", resources=("MAT",), envs=HG),
	mob("supine-spinal-twist", "Torsión tumbado", "Boca arriba, lleva las rodillas flexionadas a un lado manteniendo los hombros en el suelo y cambia.", resources=("MAT",), envs=HG),
	mob("scorpion-stretch", "Escorpión", "Boca abajo con brazos en cruz, lleva un pie hacia la mano contraria girando la cadera y alterna.", resources=("MAT",), envs=HG),
	# ---------- Preparación complementaria para deportes ----------
	prep("prep-lateral-shuffle", "Desplazamientos laterales", "En posición atlética, desplázate lateralmente sin cruzar los pies y frena con la pierna exterior. Cambios de dirección.", metric="SPORT_PRACTICE", goals=["SPORT_PRACTICE", "GENERAL_FITNESS"]),
	prep("prep-short-sprints", "Sprints cortos", "Aceleraciones de 10–20 m con recuperación completa caminando. Calienta antes.", metric="SPORT_PRACTICE", goals=["SPORT_PRACTICE", "GENERAL_FITNESS"]),
	prep("prep-pogo-jumps", "Saltos pogo", "Saltos pequeños y rápidos sobre la punta de los pies con rodillas casi estiradas. Rigidez de tobillo.", metric="SPORT_PRACTICE", goals=["SPORT_PRACTICE", "GENERAL_FITNESS"]),
	prep("prep-deceleration", "Frenadas controladas", "Corre 5–10 m y frena en dos o tres pasos bajando la cadera y con las rodillas alineadas.", metric="SPORT_PRACTICE", goals=["SPORT_PRACTICE", "GENERAL_FITNESS"]),
	prep("prep-copenhagen-plank", "Plancha Copenhague asistida", "Plancha lateral con la pierna de arriba apoyada en una superficie baja y la rodilla flexionada. Fortalece aductores.", metric="ISOMETRIC"),
	prep("prep-nordic-assisted", "Nórdico asistido", "De rodillas con los tobillos sujetos de forma segura, inclínate hacia delante lo que controles y vuelve ayudándote con las manos.", metric="STRENGTH"),
	prep("prep-hip-airplane", "Avión de cadera", "Sobre una pierna con el tronco inclinado, abre y cierra la cadera girando el tronco con control. Estabilidad.", metric="MOBILITY", goals=MOB + ["SPORT_PRACTICE"]),
	prep("prep-bird-dog", "Bird dog de preparación", "A cuatro patas, extiende brazo y pierna contrarios sin girar la cadera y alterna. Control del tronco.", metric="STRENGTH"),
]
