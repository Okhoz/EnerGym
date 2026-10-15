"""Añade disciplinas al catálogo v2 y les asigna ejercicios ya existentes.

Uso (desde la raíz del proyecto):  python tools/sport_expansion.py
Reescribe app/src/main/assets/catalog.json y backend/src/main/resources/catalog-v2.json con el mismo
contenido. Es idempotente: solo añade disciplinas y etiquetas que aún no existen y conserva el orden.
La cobertura de cada disciplina se deduce de los ejercicios etiquetados: sin ejercicios de una cobertura,
queda «pendiente de validación» (igual que la parte específica de fútbol o baloncesto).
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
TARGETS = [ROOT / "app/src/main/assets/catalog.json", ROOT / "backend/src/main/resources/catalog-v2.json"]


def mobility(e):
	return e["metricType"] == "MOBILITY" and e["coverage"] == "SPECIFIC"


def complementary(e):
	return e["coverage"] == "COMPLEMENTARY"


def ids(*values):
	wanted = set(values)
	return lambda e: e["id"] in wanted


def any_of(*rules):
	return lambda e: any(rule(e) for rule in rules)


YOGA_HOLDS = ids("forearm-plank", "side-plank", "reverse-plank", "glute-bridge-hold", "hollow-hold",
	"wall-handstand-hold", "inchworm", "bird-dog", "superman")
PILATES = ids("dead-bug", "bird-dog", "hollow-hold", "glute-bridge", "single-leg-glute-bridge", "glute-bridge-march",
	"side-plank", "forearm-plank", "superman", "clamshell", "band-clamshell", "flutter-kicks", "scissor-kicks",
	"reverse-crunch", "crunch", "lying-leg-raise", "v-up", "toe-touch-crunch", "heel-touches", "fire-hydrant",
	"donkey-kick", "glute-kickback", "stir-the-pot", "band-pallof-press", "cat-cow", "childs-pose",
	"supine-spinal-twist", "seated-forward-fold", "thoracic-open-book", "inchworm", "band-glute-bridge")
FUNCTIONAL = ids("barbell-back-squat", "barbell-front-squat", "barbell-deadlift", "barbell-push-press", "power-clean",
	"barbell-overhead-press", "barbell-sumo-deadlift", "barbell-romanian-deadlift", "barbell-lunge",
	"dumbbell-thruster", "dumbbell-snatch", "dumbbell-clean-press", "dumbbell-swing", "farmers-walk", "suitcase-carry",
	"renegade-row", "dumbbell-goblet-squat", "dumbbell-walking-lunge", "burpee", "box-jump-low", "jump-squat",
	"jump-lunge", "tuck-jump", "pull-up", "chin-up", "muscle-up", "parallel-bar-dips", "hanging-knee-raise",
	"hanging-leg-raise", "standard-pushup", "wall-handstand-hold", "mountain-climbers", "bear-crawl", "plank-jacks",
	"squat-thrust", "rowing-machine", "air-bike", "ski-erg", "jumping-jacks", "high-knees", "skater-jumps",
	"bodyweight-squat", "inverted-row", "pike-pushup", "v-up", "forearm-plank")
COMBAT = ids("shadow-boxing", "jumping-jacks", "high-knees", "skater-jumps", "burpee", "squat-thrust",
	"mountain-climbers", "plank-shoulder-taps")
HIKING = ids("brisk-walk", "stair-run", "treadmill-incline-walk", "stair-climber")
CLIMBING = ids("pull-up", "chin-up", "negative-pull-up", "scapular-pull-up", "dead-hang", "inverted-row",
	"hanging-knee-raise", "hanging-leg-raise", "assisted-pull-up", "lat-pulldown", "band-lat-pulldown",
	"dumbbell-row", "band-pull-apart", "band-face-pull", "barbell-wrist-curl", "barbell-reverse-curl",
	"dumbbell-zottman-curl", "hollow-hold", "l-sit", "wrist-mobility")

# (id, nombre, regla de ejercicios); se insertan antes de «Otra disciplina».
SPORTS = [
	("functional", "Entrenamiento funcional / CrossFit", FUNCTIONAL),
	("yoga", "Yoga", any_of(mobility, YOGA_HOLDS)),
	("pilates", "Pilates", PILATES),
	("hiking", "Senderismo y trail", any_of(HIKING, complementary)),
	("climbing", "Escalada", any_of(CLIMBING, complementary)),
	("combat", "Boxeo y artes marciales", any_of(COMBAT, complementary)),
	("padel", "Pádel", complementary),
	("tennis", "Tenis", complementary),
	("volleyball", "Voleibol", complementary),
]


def main():
	catalog = json.loads(TARGETS[0].read_text(encoding="utf-8"))
	known = {s["id"] for s in catalog["sports"]}
	for sport_id, name, rule in SPORTS:
		for e in catalog["exercises"]:
			if rule(e) and sport_id not in e["sportIds"]:
				e["sportIds"].append(sport_id)
		tagged = [e for e in catalog["exercises"] if sport_id in e["sportIds"]]
		status = lambda coverage: "EXAMPLE_UNREVIEWED" if any(e["coverage"] == coverage for e in tagged) \
			else "PENDING_VALIDATION"
		row = {"id": sport_id, "name": name, "specificCoverage": status("SPECIFIC"),
			"complementaryCoverage": status("COMPLEMENTARY")}
		if sport_id in known:
			catalog["sports"] = [row if s["id"] == sport_id else s for s in catalog["sports"]]
		else:
			other = next(i for i, s in enumerate(catalog["sports"]) if s["id"] == "other")
			catalog["sports"].insert(other, row)
		assert tagged, sport_id
	text = json.dumps(catalog, ensure_ascii=False, indent="\t") + "\n"
	for target in TARGETS:
		target.write_text(text, encoding="utf-8", newline="\n")
	for s in catalog["sports"]:
		count = sum(1 for e in catalog["exercises"] if s["id"] in e["sportIds"])
		print(f"{s['id']:12} {count:4}  específica={s['specificCoverage']}  complementaria={s['complementaryCoverage']}")


if __name__ == "__main__":
	main()
