"""Regenera todos los clips esquemáticos dentro del GLB empaquetado, sin las cinco entradas CC0 originales.

python -B tools/regenerate_schematic_motions.py

Lee app/src/main/assets/visual (GLB y manifiesto), exige que coincidan con las huellas fijadas en
ResourceManifest.java y sustituye cada clip «esq/…» por el que generan hoy exercise_patterns y exercise_motions
(ritmo excéntrico/concéntrico, perfil de mínimo tirón y respiración). Las capturas CC0, la malla, el rig, los
materiales y los props quedan igual. Después compacta el BIN: los datos de los clips sustituidos se eliminan en
lugar de quedarse sin uso. Escribe el GLB, el manifiesto y las dos huellas de ResourceManifest.java.
Para reconstruir desde las entradas CC0 originales sigue estando build_exercise_motions.py.
"""
import hashlib
import json
import pathlib
import re
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from build_exercise_motions import Builder, encode_clip, frame_tracks, PROPS  # noqa: E402
from exercise_motion_catalog import SCHEMATIC  # noqa: E402
from exercise_motions import sample  # noqa: E402
from glb_motion import Glb, write_glb  # noqa: E402
from motion_rig import Rig  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
VISUAL = ROOT / "app/src/main/assets/visual"
JAVA = ROOT / "app/src/main/java/com/energym/app/visual/ResourceManifest.java"


def sha(data):
	return hashlib.sha256(data).hexdigest()


def pinned(name):
	match = re.search(name + r' = "([0-9a-f]{64})"', JAVA.read_text(encoding="utf-8"))
	assert match, name
	return match.group(1)


def compact(doc, binary):
	"""Deja en el BIN solo los bufferViews que usan malla, skin, imágenes y animaciones; renumera las referencias."""
	assert not any("sparse" in a for a in doc["accessors"]), "Accesores dispersos no previstos"
	used = []

	def ref(index):
		if index not in used:
			used.append(index)
		return index

	for mesh in doc.get("meshes", []):
		for prim in mesh["primitives"]:
			for v in prim["attributes"].values():
				ref(v)
			if "indices" in prim:
				ref(prim["indices"])
			for target in prim.get("targets", []):
				for v in target.values():
					ref(v)
	for skin in doc.get("skins", []):
		if "inverseBindMatrices" in skin:
			ref(skin["inverseBindMatrices"])
	for anim in doc.get("animations", []):
		for s in anim["samplers"]:
			ref(s["input"])
			ref(s["output"])
	acc_map = {old: new for new, old in enumerate(used)}
	for mesh in doc.get("meshes", []):
		for prim in mesh["primitives"]:
			prim["attributes"] = {k: acc_map[v] for k, v in prim["attributes"].items()}
			if "indices" in prim:
				prim["indices"] = acc_map[prim["indices"]]
			if "targets" in prim:
				prim["targets"] = [{k: acc_map[v] for k, v in t.items()} for t in prim["targets"]]
	for skin in doc.get("skins", []):
		if "inverseBindMatrices" in skin:
			skin["inverseBindMatrices"] = acc_map[skin["inverseBindMatrices"]]
	for anim in doc.get("animations", []):
		for s in anim["samplers"]:
			s["input"], s["output"] = acc_map[s["input"]], acc_map[s["output"]]
	accessors = [doc["accessors"][old] for old in used]
	views_used = []
	for a in accessors:
		if a["bufferView"] not in views_used:
			views_used.append(a["bufferView"])
	for image in doc.get("images", []):
		if "bufferView" in image and image["bufferView"] not in views_used:
			views_used.append(image["bufferView"])
	view_map, views, out = {}, [], bytearray()
	for old in views_used:
		v = dict(doc["bufferViews"][old])
		while len(out) % 4:
			out.append(0)
		start = v.get("byteOffset", 0)
		chunk = binary[start:start + v["byteLength"]]
		v["byteOffset"] = len(out)
		out += chunk
		view_map[old] = len(views)
		views.append(v)
	for a in accessors:
		a["bufferView"] = view_map[a["bufferView"]]
	for image in doc.get("images", []):
		if "bufferView" in image:
			image["bufferView"] = view_map[image["bufferView"]]
	doc["accessors"], doc["bufferViews"] = accessors, views
	doc["buffers"][0]["byteLength"] = len(out)
	return bytes(out)


def main():
	model = (VISUAL / "energym_motions.glb").read_bytes()
	manifest_bytes = (VISUAL / "manifest.json").read_bytes()
	if sha(model) != pinned("MODEL_SHA256") or sha(manifest_bytes) != pinned("MANIFEST_SHA256"):
		raise ValueError("El GLB o el manifiesto no coinciden con las huellas de ResourceManifest; no se tocan")
	original = Glb(model)
	manifest = json.loads(manifest_bytes)
	doc = json.loads(json.dumps(original.doc))
	doc["animations"] = [a for a in doc["animations"] if not a["name"].startswith("esq/")]
	builder = Builder(doc, original.bin)
	rig = Rig(original)
	prop_nodes = {name: original.index[name] for name in PROPS}
	generated = {}
	for exercise, (key, factory) in sorted(SCHEMATIC.items()):
		name = "esq/" + key
		if name in generated:
			continue
		clip = factory(name)
		times, frames, center = sample(rig, clip)
		tracks = frame_tracks(original, times, [f[0] for f in frames], [f[1] for f in frames],
			prop_nodes, center, original.index["CamTarget"])
		encode_clip(builder, name, times, tracks, None)
		generated[name] = (clip.duration, clip.middle, clip.view_radius)
	binary = compact(doc, bytes(builder.bin))
	doc["asset"]["generator"] = re.sub(r"; schematic.*$", "", doc["asset"]["generator"]) + \
		"; clips esquemáticos regenerados con ritmo, mínimo tirón y respiración"
	result = write_glb(doc, binary)
	for row in manifest["exercises"]:
		if row["kind"] == "SCHEMATIC":
			duration, middle, radius = generated[row["clip"]]
			row.update(durationSeconds=round(duration, 6), middle=round(middle, 6), viewRadius=round(radius, 3))
	manifest["model"].update(sha256=sha(result), bytes=len(result))
	result_manifest = (json.dumps(manifest, ensure_ascii=False, indent="\t") + "\n").encode("utf-8")
	(VISUAL / "energym_motions.glb").write_bytes(result)
	(VISUAL / "manifest.json").write_bytes(result_manifest)
	text = JAVA.read_text(encoding="utf-8")
	for constant, digest in (("MODEL_SHA256", sha(result)), ("MANIFEST_SHA256", sha(result_manifest))):
		text, count = re.subn(constant + r' = "[0-9a-f]{64}"', constant + ' = "' + digest + '"', text)
		assert count == 1, constant
	JAVA.write_text(text, encoding="utf-8", newline="\n")
	print(json.dumps({"modelBytes": len(result), "previousBytes": len(model), "clips": len(doc["animations"]),
		"schematicClips": len(generated), "modelSha256": sha(result), "manifestSha256": sha(result_manifest)},
		indent=2))


if __name__ == "__main__":
	if len(sys.argv) != 1:
		raise SystemExit(__doc__)
	main()
