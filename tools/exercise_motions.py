"""Clips esquemáticos: poses clave con parámetros anatómicos, interpolación suave y muestreo.

Cada pose es un dict; los campos ausentes toman la postura de pie neutra. Las direcciones de los
segmentos van en el marco del tronco (brazos) o de la pelvis (piernas) salvo que se indique 'world'.
"""
import math

from glb_motion import q_axis, q_mul, q_norm, q_rotate, q_slerp, q_conj, q_between, v_add, v_sub, v_scale, v_unit
from motion_rig import (BAR_HEIGHT, HIDDEN, NECK_W, SPINE_W, SIDES, limb_dir, limb_front, perp, q_euler)
from exercise_tempo import ECCENTRIC_SHARE, first_phase

FPS = 15
REST_ANKLE = [0.198, 0.071, 0.015]
# Grados de extensión del tronco y de elevación de las clavículas con el pecho lleno de aire.
BREATH_SPINE, BREATH_CLAVICLE = 1.5, 2.0


def minimum_jerk(u):
	"""Perfil de velocidad de los movimientos humanos: arranca y frena sin tirones (Flash y Hogan, 1985)."""
	return u * u * u * (10 - 15 * u + 6 * u * u)


# --- Construcción de poses ---------------------------------------------------------------------------

def arm(side, elev, azim, elbow=10, toward=None, frame="chest"):
	"""El codo flexiona hacia la cara anterior del brazo salvo que 'toward' indique otra dirección."""
	d = limb_dir(side, elev, azim)
	b = _side_vec(toward, side) if toward is not None else limb_front(side, elev, azim, [0, 0, 1])
	return {"d": d, "e": float(elbow), "b": b, "f": frame}


def leg(side, elev, azim=0, knee=5, toward=None, frame="pelvis"):
	"""La rodilla flexiona hacia la cara posterior del muslo salvo que 'toward' indique otra dirección."""
	d = limb_dir(side, elev, azim)
	b = _side_vec(toward, side) if toward is not None else limb_front(side, elev, azim, [0, 0, -1])
	return {"d": d, "e": float(knee), "b": b, "f": frame}


def ik(side, target, pole, frame="world"):
	"""Objetivo de muñeca/tobillo en el mundo (x escrito para el lado izquierdo) y dirección del codo/rodilla."""
	return {"ik": _side_vec(target, side), "pole": _side_vec(pole, side), "f": frame}


def feet_ik(width=0.0, z=0.0, pole=(0.25, 0, 1), height=0.0, **kw):
	"""Pies apoyados: tobillos en su posición de reposo, separados 'width' metros extra por lado."""
	return {"legL": ik("L", [REST_ANKLE[0] + width, REST_ANKLE[1] + height, REST_ANKLE[2] + z], list(pole)),
		"legR": ik("R", [REST_ANKLE[0] + width, REST_ANKLE[1] + height, REST_ANKLE[2] + z], list(pole)), **kw}


def _side_vec(v, side):
	"""Los vectores 'toward' se escriben para el lado izquierdo; X se refleja en el derecho."""
	return [v[0] * SIDES[side], v[1], v[2]]


def arms(elev, azim, elbow=10, toward=None, frame="chest", **kw):
	return {"armL": arm("L", elev, azim, elbow, toward, frame), "armR": arm("R", elev, azim, elbow, toward, frame), **kw}


def legs(elev, azim=0, knee=5, toward=None, frame="pelvis", **kw):
	return {"legL": leg("L", elev, azim, knee, toward, frame), "legR": leg("R", elev, azim, knee, toward, frame), **kw}


def pose(*parts, **fields):
	result = {}
	for p in parts:
		result.update(p)
	result.update(fields)
	return result


NEUTRAL = {
	"body": [0, 0, 0], "spine": [0, 0, 0], "neck": [0, 0, 0], "clavL": [0, 0], "clavR": [0, 0],
	"armL": arm("L", 8, 90, 12), "armR": arm("R", 8, 90, 12),
	"legL": leg("L", 3, 90, 4), "legR": leg("R", 3, 90, 4),
	"footL": [0, 1], "footR": [0, 1], "wristL": 0, "wristR": 0,
	"anchor": {"feet": 1}, "ymode": {"ground": 1}, "rootY": .92, "lift": 0, "handsXZ": [0, .3], "rootXZ": [0, 0],
	"hold": 0, "twistarmL": 0, "twistarmR": 0, "spin": 0,
}


NEUTRAL_R = None


def full(p):
	result = dict(NEUTRAL)
	result.update(p)
	return result


# --- Interpolación ------------------------------------------------------------------------------------

def _lerp(a, b, u):
	return a + (b - a) * u


def _vslerp(a, b, u):
	a, b = v_unit(a), v_unit(b)
	d = max(-1, min(1, sum(x * y for x, y in zip(a, b))))
	if d > .9999:
		return v_unit([_lerp(x, y, u) for x, y in zip(a, b)])
	if d < -.97:
		# Casi opuestas: se gira alrededor de un eje perpendicular estable (preferentemente el lateral X).
		axis = perp([1, 0, 0], a)
		if sum(c * c for c in axis) < 1e-4:
			axis = perp([0, 0, 1], a)
		angle = math.degrees(math.acos(d))
		return v_unit(q_rotate(q_axis(axis, angle * u), a))
	q = q_between(a, b)
	angle = 2 * math.acos(max(-1, min(1, q[3])))
	axis = q[:3]
	return q_rotate(q_axis(axis, math.degrees(angle) * u), a) if math.sqrt(sum(c * c for c in axis)) > 1e-9 else a


def blend(a, b, u):
	out = {}
	for k in a:
		x, y = a[k], b[k]
		if k in ("armL", "armR", "legL", "legR"):
			if ("ik" in x) != ("ik" in y) or x["f"] != y["f"] or "mix" in x or "mix" in y:
				out[k] = {"mix": (x, y, u), "f": "mix"}
			elif "ik" in x:
				out[k] = {"ik": [_lerp(p, q, u) for p, q in zip(x["ik"], y["ik"])],
					"pole": _vslerp(x["pole"], y["pole"], u), "f": x["f"]}
			else:
				out[k] = {"d": _vslerp(x["d"], y["d"], u), "e": _lerp(x["e"], y["e"], u),
					"b": _vslerp(x["b"], y["b"], u), "f": x["f"]}
		elif k in ("anchor", "ymode"):
			keys = set(x) | set(y)
			out[k] = {n: _lerp(x.get(n, 0), y.get(n, 0), u) for n in keys}
		elif isinstance(x, list):
			out[k] = [_lerp(p, q, u) for p, q in zip(x, y)]
		elif isinstance(x, (int, float)):
			out[k] = _lerp(x, y, u)
		else:
			out[k] = x
	return out


# --- Resolución --------------------------------------------------------------------------------------

def _trunk_q(values, w):
	flex, lat, twist = values
	# flex>0 inclina hacia delante (+X), lat>0 hacia el lado izquierdo, twist>0 gira la cara hacia +X.
	return q_mul(q_axis([0, 1, 0], twist * w), q_mul(q_axis([1, 0, 0], flex * w), q_axis([0, 0, -1], lat * w)))


def _resolve_limb(limb, chest, body, yaw):
	if "mix" in limb:
		x, y, u = limb["mix"]
		return ("mix", _resolve_limb(x, chest, body, yaw), _resolve_limb(y, chest, body, yaw), u)
	if "ik" in limb:
		if limb["f"] == "chest":
			return ("ik", limb["ik"], limb["pole"], "chest")
		return ("ik", limb["ik"], q_rotate(yaw, limb["pole"]), "world")
	frame = {"chest": chest, "pelvis": body, "world": yaw}[limb["f"]]
	return ("fk", q_rotate(frame, limb["d"]), limb["e"], q_rotate(frame, limb["b"]))


def resolve(p):
	# 'spin' gira el cuerpo sobre su eje longitudinal antes de inclinarlo (posturas de lado).
	body = q_mul(q_euler(*p["body"]), q_axis([0, 1, 0], p["spin"]))
	spine = [_trunk_q(p["spine"], w) for w in SPINE_W]
	neck = [_trunk_q(p["neck"], w) for w in NECK_W]
	chest = body
	for d in spine:
		chest = q_mul(chest, d)
	r = {"body": body, "spine": spine, "neck": neck}
	for side in SIDES:
		elev, prot = p["clav" + side]
		s = SIDES[side]
		r["clav" + side] = q_mul(q_axis([0, 0, s], elev), q_axis([0, -s, 0], prot))
	yaw = q_axis([0, 1, 0], p["body"][1])
	for key in ("armL", "armR", "legL", "legR"):
		r[key] = _resolve_limb(p[key], chest, body, yaw)
	for side in SIDES:
		plantar, flat = p["foot" + side]
		relative = q_axis([1, 0, 0], plantar)
		r["foot" + side] = ("world", q_mul(yaw, relative)) if flat >= .5 else ("rel", relative)
		r["wrist" + side] = q_axis([1, 0, 0], p["wrist" + side])
		r["twistarm" + side] = p["twistarm" + side]
	return r


NEUTRAL_R = resolve(full({}))


# --- Muestreo de un clip -----------------------------------------------------------------------------

class Clip:
	"""keys: lista (segundos, pose) o fábrica según rig; el último instante cierra el bucle."""

	def __init__(self, name, keys, duration, props=None, middle=None, kind="SCHEMATIC"):
		self.name, self.duration, self.kind = name, float(duration), kind
		self._keys_for_rig = keys if callable(keys) else None
		self.keys = []
		self._base_keys = None
		if self._keys_for_rig is None:
			self._set_keys(keys)
		self.props = props or {}
		self.middle = float(middle) if middle is not None else self._default_middle()
		self._base_middle = self.middle
		# Fase del tramo A → B ('eccentric', 'concentric' o None) y si el clip respira; ver prepare().
		self.phase = None
		self.breathing = False

	def _set_keys(self, keys):
		self.keys = [(float(t), full(p)) for t, p in keys]
		assert self.keys[0][0] == 0 and all(a[0] < b[0] for a, b in zip(self.keys, self.keys[1:]))
		assert self.keys[-1][0] < self.duration
		self._base_keys = list(self.keys)

	def prepare(self, rig):
		"""Resuelve objetivos que dependen de la geometría del rig y aplica el ritmo; se puede repetir."""
		if self._keys_for_rig is not None:
			self._set_keys(self._keys_for_rig(rig))
		self.keys = list(self._base_keys)
		self.middle = self._base_middle
		self._tempo()

	def _tempo(self):
		"""
		Bajar el peso lleva más tiempo que subirlo: en los clips de dos poses de exercise_tempo, la fase excéntrica
		se lleva el 60 % del tiempo en movimiento y la concéntrica el 40 %, sin cambiar la duración ni las pausas.
		"""
		key = self.name[4:] if self.name.startswith("esq/") else self.name
		self.phase = first_phase(key)
		self.breathing = True
		if self.phase is None or len(self.keys) != 2:
			return
		(_, a), (t1, b) = self.keys
		moving = self.duration - a.get("hold", 0) - b.get("hold", 0)
		share = ECCENTRIC_SHARE if self.phase == "eccentric" else 1 - ECCENTRIC_SHARE
		bottom = a.get("hold", 0) + share * moving
		self.keys[1] = (bottom, b)
		if abs(self.middle - t1) < 1e-9:
			self.middle = bottom

	def _default_middle(self):
		return self.keys[len(self.keys) // 2][0] if len(self.keys) > 1 else self.duration / 2

	def at(self, t):
		return self._breathe(self._pose_at(t), t)

	def _pose_at(self, t):
		keys = self.keys + [(self.duration, self.keys[0][1])]
		for (t0, a), (t1, b) in zip(keys, keys[1:]):
			if t0 <= t <= t1:
				hold = a.get("hold", 0)
				span = t1 - t0
				u = 0 if span - hold <= 0 else max(0.0, (t - t0 - hold) / (span - hold))
				return blend(a, b, minimum_jerk(min(1.0, u)))
		return blend(keys[-1][1], keys[-1][1], 0)

	def _breathe(self, p, t):
		"""
		Respiración leve (pecho y hombros), periódica dentro del clip para que el bucle cierre: se toma aire al
		bajar el peso y se suelta al hacer fuerza; sin fase conocida, un ciclo cada ~4 s.
		"""
		if not self.breathing:
			return p
		if self.phase is not None and len(self.keys) == 2:
			bottom = self.keys[1][0]
			if t <= bottom:
				u = minimum_jerk(t / bottom)
				air = u if self.phase == "eccentric" else 1 - u
			else:
				u = minimum_jerk((t - bottom) / (self.duration - bottom))
				air = 1 - u if self.phase == "eccentric" else u
		else:
			cycles = max(1, round(self.duration / 4.0))
			air = .5 - .5 * math.cos(2 * math.pi * cycles * t / self.duration)
		p = dict(p)
		p["spine"] = [p["spine"][0] - BREATH_SPINE * air] + list(p["spine"][1:])
		p["clavL"] = [p["clavL"][0] + BREATH_CLAVICLE * air] + list(p["clavL"][1:])
		p["clavR"] = [p["clavR"][0] + BREATH_CLAVICLE * air] + list(p["clavR"][1:])
		return p


def grip(world, idx, side):
	w = world[idx["wrist." + side]][0]
	k = world[idx["finger3-1." + side]][0]
	return v_scale(v_add(w, k), .5)


def sample(rig, clip):
	"""Devuelve tiempos y, por nodo, listas de TRS; también el centro de cámara del clip."""
	clip.prepare(rig)
	idx = rig.idx
	root = idx["root"]
	rest_root_t = list(rig.glb.rest_trs(root)[0])
	rest_feet = v_scale(v_add(rig.rest[idx["foot.L"]][0], rig.rest[idx["foot.R"]][0]), .5)
	count = max(2, int(round(clip.duration * FPS)))
	times = [clip.duration * k / count for k in range(count + 1)]
	frames = []
	lo, hi = [1e9] * 3, [-1e9] * 3
	for t in times:
		p = clip.at(t % clip.duration if t < clip.duration else 0.0)
		local, world, shift = solve_pose(rig, p, rest_root_t, rest_feet)
		pos = lambda n: world[idx[n]][0]
		props = place_props(clip.props, world, idx, shift)
		for n in ("root", "head", "foot.L", "foot.R", "wrist.L", "wrist.R"):
			pt = v_add(pos(n), shift)
			lo = [min(a, b) for a, b in zip(lo, pt)]
			hi = [max(a, b) for a, b in zip(hi, pt)]
		frames.append((local, props))
	center = v_scale(v_add(lo, hi), .5)
	# Radio de encuadre: mitad de la mayor extensión de las articulaciones más el volumen del cuerpo.
	clip.view_radius = max(b - a for a, b in zip(lo, hi)) / 2 + .15
	return times, frames, center


def solve_pose(rig, p, rest_root_t=None, rest_feet=None):
	"""Resuelve una pose: rotaciones, IK y desplazamiento de la raíz (apoyo en suelo, anclajes)."""
	idx = rig.idx
	root = idx["root"]
	if rest_root_t is None:
		rest_root_t = list(rig.glb.rest_trs(root)[0])
		rest_feet = v_scale(v_add(rig.rest[idx["foot.L"]][0], rig.rest[idx["foot.R"]][0]), .5)
	r = resolve(p)
	has_ik = any(r[k][0] != "fk" for k in ("armL", "armR", "legL", "legR"))
	probe = dict(r)
	if has_ik:
		for k in ("armL", "armR", "legL", "legR"):
			if probe[k][0] != "fk":
				probe[k] = NEUTRAL_R[k]
	probe["rootT"] = rest_root_t
	local = rig.solve(probe)
	world = rig.world(local)
	shift = _shift(rig, p, world, rest_feet)
	if has_ik:
		# Con IK el apoyo depende de los miembros resueltos: se corrige la raíz y se vuelve a resolver.
		for _ in range(3):
			r["rootT"] = v_add(rest_root_t, shift)
			local = rig.solve(r)
			world = rig.world(local)
			extra = _shift(rig, p, world, rest_feet, only_ground=True)
			if abs(extra[1]) < 1e-4:
				break
			shift = v_add(shift, extra)
		return local, world, [0.0, 0.0, 0.0]
	t0, q0, s0 = local[root]
	local[root] = (v_add(t0, shift), q0, s0)
	return local, world, shift


def measure(rig, p, names):
	"""Posiciones mundiales de los nodos indicados en una pose (para plantar manos o pies)."""
	local, world, shift = solve_pose(rig, full(p))
	return {n: v_add(world[rig.idx[n]][0], shift) for n in names}


def _shift(rig, p, world, rest_feet, only_ground=False):
	idx = rig.idx
	pos = lambda n: world[idx[n]][0]
	feet = v_scale(v_add(pos("foot.L"), pos("foot.R")), .5)
	hands = v_scale(v_add(grip(world, idx, "L"), grip(world, idx, "R")), .5)
	rootp = pos("root")
	anchor = {} if only_ground else p["anchor"]
	total = sum(anchor.values()) or 1
	sx = sz = 0.0
	for name, w in anchor.items():
		if name == "feet":
			cur, (tx, tz) = feet, (rest_feet[0], rest_feet[2])
		elif name == "hands":
			cur, (tx, tz) = hands, p["handsXZ"]
		else:
			cur, (tx, tz) = rootp, p["rootXZ"]
		sx += w / total * (tx - cur[0])
		sz += w / total * (tz - cur[2])
	ym = p["ymode"]
	ytotal = sum(ym.values()) or 1
	sy = 0.0
	for name, w in ym.items():
		if name == "ground":
			sy += w / ytotal * (-rig.lowest(world) + p["lift"])
		elif only_ground:
			continue
		elif name == "fix":
			sy += w / ytotal * (p["rootY"] - rootp[1])
		elif name == "hang":
			sy += w / ytotal * (BAR_HEIGHT - hands[1])
	return [sx, sy, sz]


def place_props(spec, world, idx, shift):
	out = {}
	gl, gr = (v_add(grip(world, idx, s), shift) for s in "LR")
	if spec.get("barbell"):
		axis = v_unit(v_sub(gl, gr))
		out["PropBarbell"] = (v_scale(v_add(gl, gr), .5), q_between([1, 0, 0], axis), [1, 1, 1])
	else:
		out["PropBarbell"] = (HIDDEN, [0, 0, 0, 1], [1, 1, 1])
	for side, g in (("L", gl), ("R", gr)):
		name = "PropDumbbell." + side
		if spec.get("goblet"):
			out[name] = ((v_scale(v_add(gl, gr), .5), q_between([1, 0, 0], [0, 1, 0]), [1, 1, 1]) if side == "L" else
				(HIDDEN, [0, 0, 0, 1], [1, 1, 1]))
		elif spec.get("dumbbells") or spec.get("dumbbell" + side):
			a = world[idx["finger2-1." + side]][0]
			b = world[idx["finger5-1." + side]][0]
			out[name] = (g, q_between([1, 0, 0], v_unit(v_sub(a, b))), [1, 1, 1])
		else:
			out[name] = (HIDDEN, [0, 0, 0, 1], [1, 1, 1])
	box = spec.get("box")
	out["PropBox"] = ((box[:3], [0, 0, 0, 1], box[3:]) if box else (HIDDEN, [0, 0, 0, 1], [1, 1, 1]))
	bar = spec.get("bar")
	out["PropBar"] = (([0, BAR_HEIGHT, bar[1]], [0, 0, 0, 1], [1, 1, 1]) if bar else (HIDDEN, [0, 0, 0, 1], [1, 1, 1]))
	return out
