"""Offline geometric regressions for the catalog's three repaired schematic variants.

python -B tools/test_exercise_motion_variants.py [--root project] [--baseline original-project]
Only the Python standard library is required. No Android runtime or network is used.
"""
import argparse
import hashlib
import json
import math
import pathlib
import sys
import unittest

sys.dont_write_bytecode = True
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parent.parent)
parser.add_argument("--baseline", type=pathlib.Path)
args, unittest_args = parser.parse_known_args()
sys.path.insert(0, str(args.root / "tools"))

from glb_motion import Glb, world_transforms, v_sub, v_unit  # noqa: E402
from motion_rig import Rig  # noqa: E402
from exercise_motion_catalog import SCHEMATIC  # noqa: E402
from exercise_motions import sample, grip  # noqa: E402

CHANGED = {"band-triceps-extension", "dumbbell-sumo-squat", "plank-shoulder-taps"}


def read_assets(root):
    assets = root / "app/src/main/assets"
    model = (assets / "visual/energym_motions.glb").read_bytes()
    manifest = (assets / "visual/manifest.json").read_bytes()
    return Glb(model), json.loads(manifest), model, manifest


class MotionVariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.glb, cls.manifest, cls.model_bytes, cls.manifest_bytes = read_assets(args.root)
        cls.rows = {r["exerciseId"]: r for r in cls.manifest["exercises"]}
        cls.animations = {a["name"]: a for a in cls.glb.doc["animations"]}
        cls.rig = Rig(cls.glb)

    def frames(self, exercise, generated=False):
        """Return real world coordinates, either from source curves or packaged GLB."""
        if generated:
            clip = SCHEMATIC[exercise][1]("test/" + exercise)
            times, frames, _ = sample(self.rig, clip)
            return [(t, self.rig.world(local), props) for t, (local, props) in zip(times, frames)]
        row = self.rows[exercise]
        duration = row["durationSeconds"]
        count = round(duration * 15)
        result = []
        for k in range(count + 1):
            t = duration * k / count
            pose = self.glb.sample(self.animations[row["clip"]], t)
            local = {n: tuple(values.get(key, self.glb.rest_trs(n)[j])
                             for j, key in enumerate(("translation", "rotation", "scale")))
                     for n, values in pose.items()}
            world = world_transforms(self.glb, local)
            props = {name: (world[self.glb.index[name]][0], None, None)
                     for name in ("PropDumbbell.L", "PropDumbbell.R")}
            result.append((t, world, props))
        return result

    def point(self, world, name):
        return world[self.glb.index[name]][0]

    def check_triceps(self, generated):
        frames = self.frames("band-triceps-extension", generated)
        for _, world, _ in frames:
            for side in "LR":
                upper = v_unit(v_sub(self.point(world, "lowerarm01." + side),
                                     self.point(world, "upperarm01." + side)))
                self.assertLess(upper[1], -.85, "Pushdown must keep upper arms down beside the trunk")
        initial_y = self.point(frames[0][1], "wrist.L")[1]
        self.assertLess(min(self.point(w, "wrist.L")[1] for _, w, _ in frames), initial_y - .20,
                        "Pushdown extension must lower the hands")

    def check_sumo(self, generated):
        for _, world, props in self.frames("dumbbell-sumo-squat", generated):
            weight = props["PropDumbbell.L"][0]
            root = self.point(world, "root")
            self.assertLess(weight[1], root[1] - .08, "Sumo weight must hang below the pelvis")
            self.assertLess(abs(weight[0]), .05, "Single weight must stay between the legs")

    def check_taps(self, generated):
        frames = self.frames("plank-shoulder-taps", generated)
        contacts = {}
        for moving, opposite in (("L", "R"), ("R", "L")):
            nearest = min(frames, key=lambda f: math.dist(self.point(f[1], "wrist." + moving),
                                                        self.point(f[1], "upperarm01." + opposite)))
            distance = math.dist(self.point(nearest[1], "wrist." + moving),
                                 self.point(nearest[1], "upperarm01." + opposite))
            self.assertLess(distance, .06, moving + " hand must reach the opposite shoulder")
            self.assertLess(math.dist(grip(nearest[1], self.glb.index, moving),
                                      self.point(nearest[1], "upperarm01." + opposite)), .07,
                            "Palm grip point must reach the shoulder surface too")
            self.assertLess(self.point(nearest[1], "wrist." + opposite)[1], .15,
                            "Opposite hand must keep its floor support during a tap")
            contacts[moving] = nearest[0]
        self.assertGreater(abs(contacts["L"] - contacts["R"]), .5, "Contacts must alternate")

    def test_band_triceps_source(self):
        self.check_triceps(True)

    def test_band_triceps_packaged(self):
        self.check_triceps(False)

    def test_low_sumo_source(self):
        self.check_sumo(True)

    def test_low_sumo_packaged(self):
        self.check_sumo(False)

    def test_alternating_shoulder_contact_source(self):
        self.check_taps(True)

    def test_alternating_shoulder_contact_packaged(self):
        self.check_taps(False)

    def test_complete_catalog_and_valid_glb_curves(self):
        catalog = json.loads((args.root / "app/src/main/assets/catalog.json").read_text(encoding="utf-8"))
        self.assertEqual(287, len(catalog["exercises"]))
        self.assertEqual({e["id"] for e in catalog["exercises"]}, set(self.rows))
        self.assertEqual(287, len(self.manifest["exercises"]))
        self.assertEqual(len(self.animations), len(self.glb.doc["animations"]))
        self.assertEqual(set(self.animations), {r["clip"] for r in self.rows.values()})
        for row in self.rows.values():
            animation = self.animations[row["clip"]]
            duration = max(self.glb.accessor(s["input"])[-1] for s in animation["samplers"])
            self.assertAlmostEqual(row["durationSeconds"], duration, delta=.001)
        for name, animation in self.animations.items():
            targets = [(c["target"]["node"], c["target"]["path"]) for c in animation["channels"]]
            self.assertEqual(len(targets), len(set(targets)), name + " has duplicated target channels")
            for sampler in animation["samplers"]:
                times = self.glb.accessor(sampler["input"])
                values = self.glb.accessor(sampler["output"])
                self.assertGreaterEqual(len(times), 2, name)
                self.assertTrue(all(a < b for a, b in zip(times, times[1:])), name)
                self.assertEqual(len(times), len(values), name)
                self.assertTrue(all(math.isfinite(v) for row in values
                                    for v in (row if isinstance(row, list) else [row])), name)

    def test_packaged_hashes_match_runtime(self):
        source = (args.root / "app/src/main/java/com/energym/app/visual/ResourceManifest.java").read_text(encoding="utf8")
        self.assertIn(hashlib.sha256(self.model_bytes).hexdigest(), source)
        self.assertIn(hashlib.sha256(self.manifest_bytes).hexdigest(), source)
        self.assertEqual(hashlib.sha256(self.model_bytes).hexdigest(), self.manifest["model"]["sha256"])
        self.assertEqual(len(self.model_bytes), self.manifest["model"]["bytes"])
        self.assertLess(len(self.model_bytes), 16 * 1024 * 1024)

    @unittest.skipIf(args.baseline is None, "Pass --baseline to check the 284 unaffected exercises")
    def test_other_284_mappings_and_clips_are_unchanged(self):
        old, manifest, _, _ = read_assets(args.baseline)
        protected = {r["exerciseId"]: r for r in manifest["exercises"] if r["exerciseId"] not in CHANGED}
        self.assertEqual(284, len(protected))
        for exercise, row in protected.items():
            self.assertEqual(row, self.rows[exercise], exercise)
        old_animations = {a["name"]: a for a in old.doc["animations"]}
        self.assertEqual(old.bin, self.glb.bin[:len(old.bin)], "Existing mesh/animation binary ranges changed")
        for key in ("nodes", "skins", "meshes", "materials", "scenes", "scene"):
            self.assertEqual(old.doc.get(key), self.glb.doc.get(key), key)
        for name in {r["clip"] for r in protected.values()}:
            a, b = old_animations[name], self.animations[name]
            self.assertEqual(a, b, name)
            for sampler in a["samplers"]:
                for key in ("input", "output"):
                    accessor = sampler[key]
                    self.assertEqual(old.doc["accessors"][accessor], self.glb.doc["accessors"][accessor], name)
                    view = old.doc["accessors"][accessor]["bufferView"]
                    layout = old.doc["bufferViews"][view]
                    self.assertEqual(layout, self.glb.doc["bufferViews"][view], name)
                    start, length = layout.get("byteOffset", 0), layout["byteLength"]
                    self.assertEqual(old.bin[start:start + length], self.glb.bin[start:start + length], name)


if __name__ == "__main__":
    unittest.main(argv=[sys.argv[0], *unittest_args], verbosity=2)
