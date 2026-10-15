"""Retarget de clips CC0 de Mesh2Motion (rig tipo mannequin) al rig MakeHuman/MPFB de OpenGym3D.

Se copian direcciones de segmentos y rotaciones relativas al reposo de pelvis, tronco, cabeza y pies;
los dos rigs miran a +Z con Y arriba, pero el de origen está en T y el de destino en A, por eso los
brazos y piernas se transfieren como direcciones (no como rotaciones locales).
"""
import math

from glb_motion import q_conj, q_mul, q_norm, q_slerp, v_len, v_scale, v_sub, v_unit, world_transforms
from motion_rig import NECK_W, SPINE_W

SOURCE = {"pelvis": "pelvis", "chest": "spine_03", "head": "head"}
LIMBS = {"armL": ("upperarm_l", "lowerarm_l", "hand_l"), "armR": ("upperarm_r", "lowerarm_r", "hand_r"),
	"legL": ("thigh_l", "calf_l", "foot_l"), "legR": ("thigh_r", "calf_r", "foot_r")}
FPS = 30


def _delta(world, rest, i):
	return q_norm(q_mul(world[i][1], q_conj(rest[i][1])))


def _fraction(q, w):
	return q_slerp([0, 0, 0, 1], q, w)


def retarget(rig, source, animation, scale=1.0):
	"""Devuelve (tiempos, [locales por fotograma]) del clip 'animation' de la GLB de origen."""
	idx = source.index
	rest = world_transforms(source, {})
	rest_pelvis = rest[idx["pelvis"]][0]
	root = rig.idx["root"]
	rest_root_t = list(rig.glb.rest_trs(root)[0])
	duration = max(source.accessor(s["input"])[-1] for s in animation["samplers"])
	count = max(2, int(round(duration * FPS)))
	times = [duration * k / count for k in range(count + 1)]
	frames = []
	previous = {}
	for t in times:
		sampled = source.sample(animation, t)
		local = {}
		for i, v in sampled.items():
			tr, q, s = source.rest_trs(i)
			local[i] = (v.get("translation", tr), v.get("rotation", q), v.get("scale", s))
		world = world_transforms(source, local)
		body = _delta(world, rest, idx["pelvis"])
		chest = _delta(world, rest, idx[SOURCE["chest"]])
		head = _delta(world, rest, idx[SOURCE["head"]])
		spine_total = q_mul(q_conj(body), chest)
		neck_total = q_mul(q_conj(chest), head)
		r = {"body": body, "spine": [_fraction(spine_total, w) for w in SPINE_W],
			"neck": [_fraction(neck_total, w) for w in NECK_W], "clavL": [0, 0, 0, 1], "clavR": [0, 0, 0, 1],
			"wristL": [0, 0, 0, 1], "wristR": [0, 0, 0, 1], "twistarmL": 0.0, "twistarmR": 0.0}
		for key, (a, b, c) in LIMBS.items():
			pa, pb, pc = (world[idx[n]][0] for n in (a, b, c))
			d1, d2 = v_unit(v_sub(pb, pa)), v_unit(v_sub(pc, pb))
			bend = math.degrees(math.acos(max(-1.0, min(1.0, sum(x * y for x, y in zip(d1, d2))))))
			hint = d2
			# Con el miembro casi recto el plano de flexión es inestable: se conserva el del fotograma anterior.
			if bend < 8 and key in previous:
				hint = previous[key]
			previous[key] = hint
			r[key] = ("fk", d1, bend, hint)
		for side, foot in (("L", "foot_l"), ("R", "foot_r")):
			r["foot" + side] = ("world", _delta(world, rest, idx[foot]))
		offset = v_scale(v_sub(world[idx["pelvis"]][0], rest_pelvis), scale)
		r["rootT"] = [rest_root_t[k] + offset[k] for k in range(3)]
		frames.append(rig.solve(r))
	return times, frames


def height_scale(rig, source):
	rest = world_transforms(source, {})
	src = rest[source.index["pelvis"]][0][1]
	dst = rig.rest[rig.idx["root"]][0][1]
	return dst / src if src > 0 else 1.0
