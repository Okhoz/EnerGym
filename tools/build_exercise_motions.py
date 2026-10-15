"""Genera app/src/main/assets/visual/energym_motions.glb y manifest.json.

Entradas (verificadas por SHA-256, fuera del APK):
- Las cuatro exportaciones OpenGym3D ya normalizadas con normalize_opengym3d.py (malla y rig MakeHuman/MPFB CC0,
  clips Mesh2Motion CC0).
- human-base-animations.glb de Mesh2Motion (revisión 653a9698f5f315523072f1c3ff3496100401317b, CC0).

Salida: una sola malla con todos los clips. Los clips esquemáticos se generan con exercise_patterns.

Uso: python -I build_exercise_motions.py carpeta_entradas
"""
import hashlib
import json
import math
import pathlib
import re
import struct
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from glb_motion import Glb, q_mul, q_conj, q_norm, write_glb  # noqa: E402
from motion_rig import Rig  # noqa: E402
import exercise_motions  # noqa: E402
from exercise_motion_catalog import CAPTURE, CC0, SCHEMATIC  # noqa: E402
from retarget_mesh2motion import height_scale, retarget  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUTPUT = ASSETS / "visual" / "energym_motions.glb"
MANIFEST = ASSETS / "visual" / "manifest.json"

INPUTS = {
	"push_up_native.glb": "80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f",
	"jumping_jacks_native.glb": "dd6f6e860be59702714edefed5dac8265d391c4d4a2244ac7fea18b52ef2abfa",
	"jog_native.glb": "ad9cb7c7b9828e1f65d00a6d02b3afddf0a0fe52070eca5e335300c1231d5a2c",
	"walk_native.glb": "fb71090b36eb0f03a17b451618e6a28a0bb83e043baf7c1f348acf3b890c92f5",
	"human-base-animations.glb": "406eb0a8dc4ab366e623b79b6e3005a4951392e1bda78ae39c1099d31147733c",
}
OPENGYM3D = {"cc0/push_up": "push_up_native.glb", "cc0/jumping_jacks": "jumping_jacks_native.glb",
	"cc0/jog": "jog_native.glb", "cc0/walk": "walk_native.glb"}
MESH2MOTION = {"cc0/sprint": ["Sprint"], "cc0/swim": ["Swim_Fwd"], "cc0/boxing": ["Punch_Jab", "Punch_Cross"]}
PROPS = ["PropBarbell", "PropDumbbell.L", "PropDumbbell.R", "PropBox", "PropBar"]
TINY = [1e-4, 1e-4, 1e-4]
ROT_TOL, POS_TOL = math.radians(.05), .0005
RETARGET_FPS = 30


class Builder:
	def __init__(self, doc, binary):
		self.doc = doc
		self.bin = bytearray(binary)

	def view(self, data, target=None):
		while len(self.bin) % 4:
			self.bin.append(0)
		v = {"buffer": 0, "byteOffset": len(self.bin), "byteLength": len(data)}
		if target:
			v["target"] = target
		self.bin += data
		self.doc["bufferViews"].append(v)
		return len(self.doc["bufferViews"]) - 1

	def accessor(self, view, offset, count, ctype, kind, normalized=False, minmax=None):
		a = {"bufferView": view, "byteOffset": offset, "componentType": ctype, "count": count, "type": kind}
		if normalized:
			a["normalized"] = True
		if minmax:
			a["min"], a["max"] = minmax
		self.doc["accessors"].append(a)
		return len(self.doc["accessors"]) - 1

	def floats(self, values, kind, minmax=False, target=None):
		width = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[kind]
		flat = [x for v in values for x in (v if width > 1 else [v])]
		view = self.view(struct.pack("<%df" % len(flat), *flat), target)
		mm = None
		if minmax:
			cols = [flat[i::width] for i in range(width)]
			mm = ([min(c) for c in cols], [max(c) for c in cols])
		return self.accessor(view, 0, len(values), 5126, kind, minmax=mm)

	def quats(self, values):
		ints = [max(-32767, min(32767, int(round(x * 32767)))) for q in values for x in q]
		view = self.view(struct.pack("<%dh" % len(ints), *ints))
		return self.accessor(view, 0, len(values), 5122, "VEC4", normalized=True)


def sha(data):
	return hashlib.sha256(data).hexdigest()


def read_inputs(folder):
	out = {}
	for name, expected in INPUTS.items():
		data = (folder / name).read_bytes()
		assert sha(data) == expected, f"{name}: SHA-256 inesperado {sha(data)}"
		out[name] = data
	return out


# --- Geometría de props ---------------------------------------------------------------------------------

def cylinder(x0, x1, radius, segments=16):
	pos, nor, idx = [], [], []
	for ring_x in (x0, x1):
		for k in range(segments):
			a = 2 * math.pi * k / segments
			pos.append([ring_x, radius * math.cos(a), radius * math.sin(a)])
			nor.append([0, math.cos(a), math.sin(a)])
	for k in range(segments):
		a, b = k, (k + 1) % segments
		idx += [a, segments + a, segments + b, a, segments + b, b]
	for cap_x, sign in ((x0, -1), (x1, 1)):
		center = len(pos)
		pos.append([cap_x, 0, 0])
		nor.append([sign, 0, 0])
		for k in range(segments):
			a = 2 * math.pi * k / segments
			pos.append([cap_x, radius * math.cos(a), radius * math.sin(a)])
			nor.append([sign, 0, 0])
		for k in range(segments):
			a, b = center + 1 + k, center + 1 + (k + 1) % segments
			idx += [center, a, b] if sign > 0 else [center, b, a]
	return pos, nor, idx


def merge(*parts):
	pos, nor, idx = [], [], []
	for p, n, i in parts:
		base = len(pos)
		pos += p
		nor += n
		idx += [base + k for k in i]
	return pos, nor, idx


def box():
	pos, nor, idx = [], [], []
	for axis in range(3):
		for sign in (-1, 1):
			n = [0, 0, 0]
			n[axis] = sign
			u, v = [(axis + 1) % 3, (axis + 2) % 3]
			base = len(pos)
			for a, b in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
				p = [0, 0, 0]
				p[axis], p[u], p[v] = .5 * sign, .5 * a, .5 * b
				pos.append(p)
				nor.append(list(n))
			idx += [base, base + 1, base + 2, base, base + 2, base + 3] if sign > 0 else [base, base + 2, base + 1,
				base, base + 3, base + 2]
	return pos, nor, idx


def add_mesh(builder, name, geometry, material):
	pos, nor, idx = geometry
	p = builder.floats(pos, "VEC3", minmax=True, target=34962)
	n = builder.floats(nor, "VEC3", target=34962)
	iv = builder.view(struct.pack("<%dH" % len(idx), *idx), 34963)
	i = builder.accessor(iv, 0, len(idx), 5123, "SCALAR")
	builder.doc["meshes"].append({"name": name, "primitives": [{"attributes": {"POSITION": p, "NORMAL": n},
		"indices": i, "material": material}]})
	return len(builder.doc["meshes"]) - 1


def add_props(builder):
	doc = builder.doc
	doc["materials"].append({"name": "PropMetal", "pbrMetallicRoughness": {"baseColorFactor": [.22, .24, .27, 1],
		"metallicFactor": .6, "roughnessFactor": .45}})
	metal = len(doc["materials"]) - 1
	doc["materials"].append({"name": "PropSupport", "pbrMetallicRoughness": {"baseColorFactor": [.42, .45, .5, 1],
		"metallicFactor": 0, "roughnessFactor": .85}})
	support = len(doc["materials"]) - 1
	barbell = add_mesh(builder, "Barbell", merge(cylinder(-.9, .9, .014), cylinder(-.72, -.66, .2),
		cylinder(.66, .72, .2), cylinder(-.66, -.62, .04), cylinder(.62, .66, .04)), metal)
	dumbbell = add_mesh(builder, "Dumbbell", merge(cylinder(-.07, .07, .016), cylinder(-.14, -.065, .05),
		cylinder(.065, .14, .05)), metal)
	cube = add_mesh(builder, "Support", box(), support)
	bar = add_mesh(builder, "Bar", cylinder(-.7, .7, .017), metal)
	meshes = {"PropBarbell": barbell, "PropDumbbell.L": dumbbell, "PropDumbbell.R": dumbbell, "PropBox": cube,
		"PropBar": bar}
	scene = doc["scenes"][doc.get("scene", 0)]
	nodes = {}
	for name in PROPS:
		# Escala mínima por defecto: invisibles y sin ampliar el límite del recurso si un clip no los usa.
		doc["nodes"].append({"name": name, "mesh": meshes[name], "translation": [0, .9, 0], "scale": TINY})
		nodes[name] = len(doc["nodes"]) - 1
		scene["nodes"].append(nodes[name])
	return nodes


# --- Canales de animación --------------------------------------------------------------------------------

def angle(a, b):
	d = abs(sum(x * y for x, y in zip(a, b)))
	return 2 * math.acos(min(1.0, d))


def continuous(qs):
	out = []
	for q in qs:
		q = q_norm(q)
		if out and sum(x * y for x, y in zip(out[-1], q)) < 0:
			q = [-c for c in q]
		out.append(q)
	return out


def encode_clip(builder, name, times, tracks, const_input):
	"""tracks: [(nodo, 'rotation'|'translation'|'scale', valores, valor_reposo)]."""
	samplers, channels = [], []
	clip_input = two_key = None
	for node, path, values, rest in tracks:
		if path == "rotation":
			values = continuous(values)
			constant = all(angle(values[0], v) < ROT_TOL for v in values)
			if constant and rest is not None and angle(values[0], rest) < ROT_TOL:
				continue
		else:
			constant = all(max(abs(x - y) for x, y in zip(values[0], v)) < POS_TOL for v in values)
			if constant and rest is not None and max(abs(x - y) for x, y in zip(values[0], rest)) < POS_TOL:
				continue
		if constant:
			# Filament ignora muestreadores de una sola clave: un valor fijo se escribe en [0, duración].
			if two_key is None:
				two_key = builder.floats([times[0], times[-1]], "SCALAR", minmax=True)
			inp, data = two_key, [values[0], values[0]]
		else:
			if clip_input is None:
				clip_input = builder.floats(times, "SCALAR", minmax=True)
			inp, data = clip_input, values
		out = builder.quats(data) if path == "rotation" else builder.floats(data, "VEC3")
		samplers.append({"input": inp, "output": out, "interpolation": "LINEAR"})
		channels.append({"sampler": len(samplers) - 1, "target": {"node": node, "path": path}})
	if clip_input is None:
		# Un clip debe durar lo que indica el manifiesto aunque solo tenga canales constantes.
		clip_input = builder.floats(times, "SCALAR", minmax=True)
		out = builder.floats([[0, 0, 0]] * len(times), "VEC3")
		samplers.append({"input": clip_input, "output": out, "interpolation": "LINEAR"})
		channels.append({"sampler": len(samplers) - 1, "target": {"node": 0, "path": "scale"}})
	builder.doc["animations"].append({"name": name, "samplers": samplers, "channels": channels})


def frame_tracks(glb, times, frames, props_per_frame, prop_nodes, cam_center, cam_node):
	nodes = sorted({n for f in frames for n in f})
	tracks = []
	for n in nodes:
		rest_t, rest_q, _ = glb.rest_trs(n)
		qs = [f[n][1] if n in f else rest_q for f in frames]
		tracks.append((n, "rotation", qs, rest_q))
		ts = [list(f[n][0]) if n in f else rest_t for f in frames]
		tracks.append((n, "translation", ts, rest_t))
	for name, node in prop_nodes.items():
		used = props_per_frame is not None and props_per_frame[0][name][0][1] > -10
		if not used:
			continue
		tracks.append((node, "translation", [p[name][0] for p in props_per_frame], None))
		tracks.append((node, "rotation", [p[name][1] for p in props_per_frame], None))
		tracks.append((node, "scale", [p[name][2] for p in props_per_frame], None))
	tracks.append((cam_node, "translation", [cam_center] * len(times), None))
	return tracks


def copy_opengym3d(builder, base, source, name, const_input, cam_node):
	assert [n.get("name") for n in source.nodes] == [n.get("name") for n in base.nodes[:len(source.nodes)]]
	anim = source.doc["animations"]
	assert len(anim) == 1 and anim[0]["name"] == "Human.rig"
	samplers, channels = [], []
	cache = {}
	for ch in anim[0]["channels"]:
		s = anim[0]["samplers"][ch["sampler"]]
		if s["input"] not in cache:
			cache[s["input"]] = builder.floats(source.accessor(s["input"]), "SCALAR", minmax=True)
		kind = "VEC4" if ch["target"]["path"] == "rotation" else "VEC3"
		out = builder.floats(source.accessor(s["output"]), kind)
		samplers.append({"input": cache[s["input"]], "output": out, "interpolation": s.get("interpolation", "LINEAR")})
		channels.append({"sampler": len(samplers) - 1, "target": dict(ch["target"])})
	cam = source.nodes[source.index["CamTarget"]].get("translation", [0, 0, 0])
	duration = max(source.accessor(s["input"])[-1] for s in anim[0]["samplers"])
	out = builder.floats([cam, cam], "VEC3")
	samplers.append({"input": builder.floats([0.0, duration], "SCALAR", minmax=True), "output": out,
		"interpolation": "LINEAR"})
	channels.append({"sampler": len(samplers) - 1, "target": {"node": cam_node, "path": "translation"}})
	builder.doc["animations"].append({"name": name, "samplers": samplers, "channels": channels})
	return max(source.accessor(s["input"])[-1] for s in anim[0]["samplers"])


def camera_center(rig, frames):
	idx = rig.idx
	lo, hi = [1e9] * 3, [-1e9] * 3
	for local in frames:
		world = rig.world(local)
		for n in ("root", "head", "foot.L", "foot.R", "wrist.L", "wrist.R"):
			p = world[idx[n]][0]
			lo = [min(a, b) for a, b in zip(lo, p)]
			hi = [max(a, b) for a, b in zip(hi, p)]
	return [(a + b) / 2 for a, b in zip(lo, hi)], max(b - a for a, b in zip(lo, hi)) / 2 + .15


def main(folder):
	data = read_inputs(pathlib.Path(folder))
	base = Glb(data["push_up_native.glb"])
	doc = json.loads(json.dumps(base.doc))
	doc["animations"] = []
	builder = Builder(doc, base.bin)
	prop_nodes = add_props(builder)
	cam_node = base.index["CamTarget"]
	const_input = None
	rig = Rig(base)
	clips = {}
	for clip, file in OPENGYM3D.items():
		duration = copy_opengym3d(builder, base, Glb(data[file]), clip, const_input, cam_node)
		clips[clip] = (duration, duration / 2, 0.0)
	source = Glb(data["human-base-animations.glb"])
	animations = {a["name"]: a for a in source.doc["animations"]}
	scale = height_scale(rig, source)
	for clip, names in MESH2MOTION.items():
		times, frames = [], []
		for nm in names:
			t, f = retarget(rig, source, animations[nm], scale)
			offset = times[-1] + 1 / RETARGET_FPS if times else 0.0
			times += [offset + x for x in t]
			frames += f
		start = times[0]
		times = [x - start for x in times]
		center, radius = camera_center(rig, frames)
		encode_clip(builder, clip, times, frame_tracks(base, times, frames, None, prop_nodes, center, cam_node),
			const_input)
		clips[clip] = (times[-1], times[-1] / 2, radius)
	generated = {}
	for exercise, (key, factory) in sorted(SCHEMATIC.items()):
		name = "esq/" + key
		if name in generated:
			continue
		clip = factory(name)
		times, frames, center = exercise_motions.sample(rig, clip)
		locals_ = [f[0] for f in frames]
		props = [f[1] for f in frames]
		encode_clip(builder, name, times, frame_tracks(base, times, locals_, props, prop_nodes, center, cam_node),
			const_input)
		generated[name] = clips[name] = (clip.duration, clip.middle, clip.view_radius)
	doc["buffers"][0]["byteLength"] = len(builder.bin)
	doc["asset"]["generator"] = "EnerGym tools/build_exercise_motions.py (sobre Khronos glTF Blender I/O)"
	glb = write_glb(doc, builder.bin)
	OUTPUT.write_bytes(glb)
	rows = []
	catalog = json.loads((ASSETS / "catalog.json").read_text(encoding="utf-8"))
	for e in catalog["exercises"]:
		eid = e["id"]
		if eid in CAPTURE:
			clip, match = CAPTURE[eid]
			kind = "CC0_CAPTURE"
		else:
			clip, match, kind = "esq/" + SCHEMATIC[eid][0], "PATTERN", "SCHEMATIC"
		duration, middle, radius = clips[clip]
		rows.append({"exerciseId": eid, "clip": clip, "kind": kind, "match": match,
			"durationSeconds": round(duration, 6), "middle": round(middle, 6), "viewRadius": round(radius, 3)})
	manifest = {
		"version": 2,
		"model": {
			"assetPath": "visual/energym_motions.glb",
			"sha256": sha(glb),
			"bytes": len(glb),
			"license": "CC0-1.0",
			"licenseFiles": ["visual/licenses/Mesh2Motion-CC0.md", "visual/licenses/CC0-1.0.txt"],
			"creators": "OpenGym3D · Mesh2Motion · MakeHuman/MPFB core assets · EnerGym (clips esquemáticos)",
			"sources": [
				{"file": f, "sha256": h} for f, h in INPUTS.items()
			],
			"reviewStatus": "TECHNICAL_PREVIEW_PROFESSIONAL_REVIEW_PENDING",
			"professionallyReviewed": False,
			"kinds": {
				"CC0_CAPTURE": "Captura de movimiento CC0 (Mesh2Motion, vía OpenGym3D o retarget propio).",
				"SCHEMATIC": "Animación esquemática generada por patrón de movimiento con props simplificados.",
			},
			"captureClips": {k: v[1] for k, v in CC0.items()},
		},
		"exercises": rows,
	}
	text = json.dumps(manifest, ensure_ascii=False, indent="\t") + "\n"
	MANIFEST.write_text(text, encoding="utf-8", newline="\n")
	# Las huellas fijadas en el código son la lista cerrada que acepta la app.
	java = ROOT / "app/src/main/java/com/energym/app/visual/ResourceManifest.java"
	source = java.read_text(encoding="utf-8")
	source = re.sub(r'MANIFEST_SHA256 = "[0-9a-f]{64}"', f'MANIFEST_SHA256 = "{sha(text.encode("utf-8"))}"', source)
	source = re.sub(r'MODEL_SHA256 = "[0-9a-f]{64}"', f'MODEL_SHA256 = "{sha(glb)}"', source)
	java.write_text(source, encoding="utf-8", newline="\n")
	print(f"GLB {len(glb)} bytes SHA-256 {sha(glb)}; {len(doc['animations'])} clips; "
		f"{len(rows)} ejercicios; manifiesto SHA-256 {sha(text.encode('utf-8'))}")


if __name__ == "__main__":
	if len(sys.argv) != 2:
		raise SystemExit(__doc__)
	main(sys.argv[1])
