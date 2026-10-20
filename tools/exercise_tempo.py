"""Ritmo de los clips esquemáticos de dos poses (A en t=0, B a mitad): qué tramo es la fase excéntrica.

Una persona baja el peso más despacio de lo que lo sube. En estos clips la fase excéntrica se lleva el 60 % del
tiempo en movimiento y la concéntrica el 40 %; la duración total del clip no cambia.

La tabla se revisó a mano a partir de la altura de la carga en cada pose (ver docs/resources.md): en las poleas y
máquinas que se empujan o tiran hacia abajo, bajar es el esfuerzo. Estiramientos, movilidad y movimientos
balísticos no aparecen y conservan el reparto simétrico.
"""

ECCENTRIC_SHARE = .6

# A → B baja la carga o el cuerpo: es la fase excéntrica.
ECCENTRIC_FIRST = {
	"back-extension", "bench-press", "bench-press-close", "bench-press-close-dumbbells", "bench-press-dumbbells",
	"bench-press-incline", "bench-press-incline-dumbbells", "curtsy-lunge", "deadlift", "deadlift-band",
	"deadlift-dumbbells", "dips", "dips-bench", "floor-press", "floor-press-dumbbells", "fly", "good-morning",
	"good-morning-band", "lunge-bulgarian", "lunge-static", "lunge-static-back", "lunge-static-dumbbells",
	"pike-pushup", "pistol-assisted", "pull-through", "pull-up-negative", "pullover", "pushup-decline",
	"pushup-diamond", "pushup-incline", "pushup-knee", "pushup-wide", "rdl", "rdl-dumbbells", "single-leg-rdl",
	"single-leg-rdl-dumbbell", "skull-crusher", "skull-crusher-dumbbells", "squat", "squat-back", "squat-band",
	"squat-chair", "squat-dumbbells", "squat-front", "squat-goblet", "squat-hack", "squat-smith", "squat-sumo",
	"squat-sumo-low-dumbbell", "sumo-deadlift",
}

# A → B es el esfuerzo (sube la carga, o tira/empuja hacia abajo contra una polea o máquina).
CONCENTRIC_FIRST = {
	"bridge", "bridge-single", "calf-raise-barbell", "calf-raise-dumbbells", "calf-raise-single",
	"calf-raise-support", "chin-up", "clamshell", "crossover", "crunch", "crunch-cable", "crunch-machine",
	"curl-band", "curl-barbell", "curl-cable", "curl-concentration", "curl-dumbbell", "curl-hammer", "curl-incline",
	"curl-preacher", "curl-reverse", "dips-machine", "donkey-kick", "face-pull", "fire-hydrant", "frog-pump",
	"front-raise", "glute-kickback", "hanging-knee-raise", "hanging-leg-raise", "hip-abduction", "hip-adduction",
	"hip-thrust", "hip-thrust-dumbbell", "hip-thrust-machine", "kickback-cable", "kickback-machine",
	"lateral-raise", "lateral-raise-band", "lateral-raise-cable", "lateral-raise-machine", "leg-curl-band",
	"leg-curl-lying", "leg-curl-seated", "leg-extension", "leg-press", "leg-raise", "low-fly", "press-band",
	"press-barbell", "press-dumbbell", "press-landmine", "press-machine", "pull-up", "pull-up-assisted", "pulldown",
	"pulldown-band", "pulldown-close", "pulldown-straight-arm", "reverse-crunch", "reverse-fly",
	"reverse-fly-seated", "row-barbell", "row-chest-supported", "row-dumbbell", "row-inverted", "row-machine",
	"row-one-arm", "row-pendlay", "row-seated", "row-t-bar", "ski-erg", "superman", "toe-touch-crunch",
	"triceps-kickback", "triceps-overhead", "triceps-overhead-cable", "triceps-pushdown", "triceps-pushdown-band",
	"upright-row", "upright-row-barbell", "v-up", "woodchop",
}

assert not ECCENTRIC_FIRST & CONCENTRIC_FIRST


def first_phase(key):
	"""'eccentric', 'concentric' o None (reparto simétrico) para el tramo A → B del clip 'esq/<key>'."""
	if key in ECCENTRIC_FIRST:
		return "eccentric"
	if key in CONCENTRIC_FIRST:
		return "concentric"
	return None
