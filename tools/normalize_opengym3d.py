"""Adaptación CC0 mínima para Filament; no altera malla, rig ni datos de movimiento.

Cada exportación OpenGym3D (revisión ea3a60130fdcfb3c4771e44d09f84ebab4ee9bae) se identifica por su
SHA-256: solo se acepta un origen conocido y solo se escribe el derivado esperado.
"""
import hashlib
import json
import pathlib
import struct
import sys

# exportación -> (destino empaquetado, SHA-256 origen, SHA-256 derivado, SHA-256 del chunk BIN)
RESOURCES = {
	"push_up.glb": ("push_up_native.glb",
		"67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326",
		"80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f",
		"338f0a42938e2ee2de53d777bbc87c32f18101aeab82d813903c40437d39e97f"),
	"jumping_jack.glb": ("jumping_jacks_native.glb",
		"94aa534eded3272a61f775d3a7067a9093df97c99f5505d64e39251c46697a0e",
		"dd6f6e860be59702714edefed5dac8265d391c4d4a2244ac7fea18b52ef2abfa",
		"b8c2aee85ea30f7510998da1b6d3fd0fb5828a636fc9b68902d705e1e276ae3f"),
	"jog.glb": ("jog_native.glb",
		"3e20fe0299cd78c9534f4c1320a4e2e4f31f8b4ae51598a69065f93a43a0c020",
		"ad9cb7c7b9828e1f65d00a6d02b3afddf0a0fe52070eca5e335300c1231d5a2c",
		"05ec689556476c4419480c1688fd43456057457f1463303172866d2995fa246b"),
	"walk.glb": ("walk_native.glb",
		"789867cd7f3ab8362f29632d1b424eb0f31193fca3a05318ac830a03b2115eb1",
		"fb71090b36eb0f03a17b451618e6a28a0bb83e043baf7c1f348acf3b890c92f5",
		"55decda8a7701e5edb2798ed3a7d1e207220a0d168afc1a3d5cd07116cc34a77"),
}


def normalize(name: str, source: pathlib.Path, destination: pathlib.Path, expected=None):
	original = source.read_bytes()
	source_sha = hashlib.sha256(original).hexdigest()
	if expected is not None:
		assert source_sha == expected[1], f"Origen inesperado: {source_sha}"
	assert original[:4] == b"glTF" and struct.unpack_from("<I", original, 4)[0] == 2
	json_size = struct.unpack_from("<I", original, 12)[0]
	document = json.loads(original[20:20 + json_size])
	animations = document["animations"]
	assert len(animations) == 2 and animations[0]["name"] == "Human.rig"
	assert animations[1]["name"] == "Human"
	assert len(animations[1]["channels"]) == 1
	assert animations[1]["channels"][0]["target"]["path"] == "weights"
	assert all(not primitive.get("targets") for mesh in document["meshes"] for primitive in mesh["primitives"])
	assert any(node.get("name") == "CamTarget" for node in document["nodes"])
	# Solo se retira la pista huérfana; permanecen todos los demás campos y el BIN completo.
	document["animations"] = animations[:1]
	payload = json.dumps(document, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
	payload += b" " * ((-len(payload)) % 4)
	binary_chunk = original[20 + json_size:]
	derived = struct.pack("<III", 0x46546C67, 2, 20 + len(payload) + len(binary_chunk))
	derived += struct.pack("<II", len(payload), 0x4E4F534A) + payload + binary_chunk
	derived_sha = hashlib.sha256(derived).hexdigest()
	binary_sha = hashlib.sha256(binary_chunk).hexdigest()
	if expected is not None:
		assert derived_sha == expected[2], f"Derivado inesperado: {derived_sha}"
		assert binary_sha == expected[3], f"BIN inesperado: {binary_sha}"
	assert derived[20 + len(payload):] == binary_chunk
	accessors = document["accessors"]
	duration = max(accessors[s["input"]]["max"][0] for s in animations[0]["samplers"])
	destination.parent.mkdir(parents=True, exist_ok=True)
	destination.write_bytes(derived)
	print(f"{name}: origen {len(original)} bytes SHA-256 {source_sha}")
	print(f"  derivado {destination.name}: {len(derived)} bytes; SHA-256 {derived_sha}")
	print(f"  BIN intacto: SHA-256 {binary_sha}; Human.rig {duration!r} s")


if __name__ == "__main__":
	if len(sys.argv) != 3:
		raise SystemExit("Uso: python -I normalize_opengym3d.py carpeta_exportaciones carpeta_destino")
	source_dir, destination_dir = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
	for export, expected in RESOURCES.items():
		normalize(export, source_dir / export, destination_dir / expected[0], expected)
