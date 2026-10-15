"""Regenerate only three schematic variants in the verified 15-10-2026 GLB.

python -B tools/repair_motion_variants.py ORIGINAL_PROJECT_ROOT

The original project is read-only. Outputs are written beside this tool's project.
Inputs are pinned by SHA-256. The other 284 exercise rows, all CC0 clips, mesh,
materials, licenses and existing binary ranges are preserved. Old, unused animation
data stays in the BIN chunk, so accessors of unchanged clips do not move.
For a full rebuild from the five original CC0 inputs use build_exercise_motions.py.
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
BASE_MODEL_SHA256 = "46fc49771546ae4519d17c35e0d37d72c39779e1d4ca9d87629b3597dec3dee9"
BASE_MANIFEST_SHA256 = "c20f184a484d150ef1cc1557bfefb3ccbf5fdf08011ff0492f5dd3f6693986d2"
EXERCISES = ("band-triceps-extension", "dumbbell-sumo-squat", "plank-shoulder-taps")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main(original_root):
    source = pathlib.Path(original_root).resolve() / "app/src/main/assets/visual"
    output = ROOT / "app/src/main/assets/visual"
    model = (source / "energym_motions.glb").read_bytes()
    manifest_bytes = (source / "manifest.json").read_bytes()
    if sha(model) != BASE_MODEL_SHA256 or sha(manifest_bytes) != BASE_MANIFEST_SHA256:
        raise ValueError("Inputs differ from the reviewed original package; refusing to replace them")
    original = Glb(model)
    manifest = json.loads(manifest_bytes)
    doc = json.loads(json.dumps(original.doc))
    rows = {row["exerciseId"]: row for row in manifest["exercises"]}
    new_names = {exercise: "esq/" + SCHEMATIC[exercise][0] for exercise in EXERCISES}
    used_names = {new_names.get(exercise, row["clip"]) for exercise, row in rows.items()}
    # Shared overhead cable animation remains; only unused/replaced clips are removed.
    doc["animations"] = [a for a in doc["animations"]
                         if a["name"] in used_names and a["name"] not in set(new_names.values())]
    builder = Builder(doc, original.bin)
    rig = Rig(original)
    prop_nodes = {name: original.index[name] for name in PROPS}
    for exercise in EXERCISES:
        name = new_names[exercise]
        clip = SCHEMATIC[exercise][1](name)
        times, frames, center = sample(rig, clip)
        tracks = frame_tracks(original, times, [f[0] for f in frames], [f[1] for f in frames],
                              prop_nodes, center, original.index["CamTarget"])
        encode_clip(builder, name, times, tracks, None)
        rows[exercise].update(clip=name, durationSeconds=round(clip.duration, 6),
                              middle=round(clip.middle, 6), viewRadius=round(clip.view_radius, 3))
    doc["buffers"][0]["byteLength"] = len(builder.bin)
    doc["asset"]["generator"] += "; schematic variant repair 2026-10-15"
    result = write_glb(doc, builder.bin)
    manifest["model"].update(sha256=sha(result), bytes=len(result))
    result_manifest = (json.dumps(manifest, ensure_ascii=False, indent="\t") + "\n").encode("utf-8")
    output.mkdir(parents=True, exist_ok=True)
    (output / "energym_motions.glb").write_bytes(result)
    (output / "manifest.json").write_bytes(result_manifest)
    java = ROOT / "app/src/main/java/com/energym/app/visual/ResourceManifest.java"
    text = java.read_text(encoding="utf-8")
    for constant, digest in (("MODEL_SHA256", sha(result)), ("MANIFEST_SHA256", sha(result_manifest))):
        text, count = re.subn(constant + r' = "[0-9a-f]{64}"', constant + ' = "' + digest + '"', text)
        assert count == 1, constant
    java.write_text(text, encoding="utf-8", newline="\n")
    print(json.dumps({"modelBytes": len(result), "clips": len(doc["animations"]),
                      "modelSha256": sha(result), "manifestSha256": sha(result_manifest)}, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
