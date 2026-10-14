"""Adaptación CC0 mínima para Filament; no altera malla, rig ni datos de movimiento."""
import hashlib
import json
import pathlib
import struct
import sys

SOURCE_SHA = "67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326"
DERIVED_SHA = "80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f"


def normalize(source: pathlib.Path, destination: pathlib.Path):
	original = source.read_bytes()
	assert hashlib.sha256(original).hexdigest() == SOURCE_SHA, "Origen inesperado"
	json_size = struct.unpack_from("<I", original, 12)[0]
	document = json.loads(original[20:20 + json_size])
	animations = document["animations"]
	assert len(animations) == 2 and animations[0]["name"] == "Human.rig"
	assert animations[1]["name"] == "Human"
	assert len(animations[1]["channels"]) == 1
	assert animations[1]["channels"][0]["target"]["path"] == "weights"
	assert all(not primitive.get("targets") for mesh in document["meshes"] for primitive in mesh["primitives"])
	# Solo se retira la pista huérfana; permanecen todos los demás campos y el BIN completo.
	document["animations"] = animations[:1]
	payload = json.dumps(document, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
	payload += b" " * ((-len(payload)) % 4)
	binary_chunk = original[20 + json_size:]
	derived = struct.pack("<III", 0x46546C67, 2, 20 + len(payload) + len(binary_chunk))
	derived += struct.pack("<II", len(payload), 0x4E4F534A) + payload + binary_chunk
	assert hashlib.sha256(derived).hexdigest() == DERIVED_SHA
	assert derived[20 + len(payload):] == binary_chunk
	destination.parent.mkdir(parents=True, exist_ok=True)
	destination.write_bytes(derived)
	print(f"GLB derivado: {len(derived)} bytes; SHA-256 {DERIVED_SHA}")
	print(f"BIN intacto: SHA-256 {hashlib.sha256(binary_chunk).hexdigest()}")


if __name__ == "__main__":
	if len(sys.argv) != 3:
		raise SystemExit("Uso: python normalize_push_up.py origen.glb destino.glb")
	normalize(pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]))
