"""Utilidades sin dependencias para leer y escribir GLB y calcular poses de un esqueleto glTF.

Cuaterniones en orden glTF [x, y, z, w]; matrices 4x4 en filas (m[fila][columna]).
"""
import json
import math
import struct

GLB_MAGIC, CHUNK_JSON, CHUNK_BIN = 0x46546C67, 0x4E4F534A, 0x004E4942


# --- Cuaterniones -----------------------------------------------------------------------------------

def q_mul(a, b):
	ax, ay, az, aw = a
	bx, by, bz, bw = b
	return [aw * bx + ax * bw + ay * bz - az * by,
		aw * by - ax * bz + ay * bw + az * bx,
		aw * bz + ax * by - ay * bx + az * bw,
		aw * bw - ax * bx - ay * by - az * bz]


def q_conj(q):
	return [-q[0], -q[1], -q[2], q[3]]


def q_norm(q):
	n = math.sqrt(sum(c * c for c in q)) or 1.0
	return [c / n for c in q]


def q_axis(axis, degrees):
	n = math.sqrt(sum(c * c for c in axis))
	s = math.sin(math.radians(degrees) / 2) / n
	return [axis[0] * s, axis[1] * s, axis[2] * s, math.cos(math.radians(degrees) / 2)]


def q_rotate(q, v):
	p = q_mul(q_mul(q, [v[0], v[1], v[2], 0.0]), q_conj(q))
	return p[:3]


def q_slerp(a, b, t):
	dot = sum(x * y for x, y in zip(a, b))
	if dot < 0:
		b, dot = [-c for c in b], -dot
	if dot > .9995:
		return q_norm([x + (y - x) * t for x, y in zip(a, b)])
	theta = math.acos(dot)
	s = math.sin(theta)
	wa, wb = math.sin((1 - t) * theta) / s, math.sin(t * theta) / s
	return [wa * x + wb * y for x, y in zip(a, b)]


def q_between(u, v):
	"""Rotación mínima que lleva la dirección u a la dirección v."""
	u, v = v_unit(u), v_unit(v)
	d = sum(a * b for a, b in zip(u, v))
	if d < -.999999:
		axis = v_cross([1, 0, 0], u)
		if v_len(axis) < 1e-6:
			axis = v_cross([0, 1, 0], u)
		return q_axis(axis, 180)
	c = v_cross(u, v)
	return q_norm([c[0], c[1], c[2], 1 + d])


def v_cross(a, b):
	return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def v_len(a):
	return math.sqrt(sum(c * c for c in a))


def v_unit(a):
	n = v_len(a) or 1.0
	return [c / n for c in a]


def v_sub(a, b):
	return [x - y for x, y in zip(a, b)]


def v_add(a, b):
	return [x + y for x, y in zip(a, b)]


def v_scale(a, s):
	return [x * s for x in a]


# --- GLB ---------------------------------------------------------------------------------------------

class Glb:
	def __init__(self, data: bytes):
		magic, version, length = struct.unpack_from("<III", data, 0)
		assert magic == GLB_MAGIC and version == 2 and length == len(data), "GLB inválido"
		json_len, json_type = struct.unpack_from("<II", data, 12)
		assert json_type == CHUNK_JSON
		self.doc = json.loads(data[20:20 + json_len])
		offset = 20 + json_len
		bin_len, bin_type = struct.unpack_from("<II", data, offset)
		assert bin_type == CHUNK_BIN
		self.bin = bytearray(data[offset + 8:offset + 8 + bin_len])
		self.nodes = self.doc["nodes"]
		self.parent = [-1] * len(self.nodes)
		for i, node in enumerate(self.nodes):
			for child in node.get("children", []):
				self.parent[child] = i
		self.index = {node.get("name"): i for i, node in enumerate(self.nodes)}

	@staticmethod
	def read(path):
		with open(path, "rb") as f:
			return Glb(f.read())

	def accessor(self, i):
		acc = self.doc["accessors"][i]
		view = self.doc["bufferViews"][acc["bufferView"]]
		width = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}[acc["type"]]
		fmt = {5126: "f", 5123: "H", 5121: "B", 5125: "I", 5122: "h", 5120: "b"}[acc["componentType"]]
		size = struct.calcsize(fmt)
		stride = view.get("byteStride", size * width)
		start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
		out = []
		for k in range(acc["count"]):
			values = struct.unpack_from("<" + fmt * width, self.bin, start + k * stride)
			if acc.get("normalized"):
				scale = {"h": 32767.0, "b": 127.0, "H": 65535.0, "B": 255.0}[fmt]
				values = [max(-1.0, v / scale) for v in values]
			out.append(list(values) if width > 1 else values[0])
		return out

	def rest_trs(self, i):
		node = self.nodes[i]
		return (list(node.get("translation", [0, 0, 0])), list(node.get("rotation", [0, 0, 0, 1])),
			list(node.get("scale", [1, 1, 1])))

	def depth_order(self):
		"""Índices de nodo con cada padre antes que sus hijos."""
		order, seen = [], set()

		def visit(i):
			if i in seen:
				return
			if self.parent[i] >= 0:
				visit(self.parent[i])
			seen.add(i)
			order.append(i)

		for i in range(len(self.nodes)):
			visit(i)
		return order

	def sample(self, animation, t):
		"""TRS local de cada nodo animado en el instante t (LINEAR/STEP)."""
		result = {}
		for channel in animation["channels"]:
			sampler = animation["samplers"][channel["sampler"]]
			times = self._cached(sampler["input"])
			values = self._cached(sampler["output"])
			path, node = channel["target"]["path"], channel["target"]["node"]
			if t <= times[0]:
				value = values[0]
			elif t >= times[-1]:
				value = values[-1]
			else:
				k = 0
				while times[k + 1] < t:
					k += 1
				u = (t - times[k]) / (times[k + 1] - times[k])
				a, b = values[k], values[k + 1]
				if sampler.get("interpolation", "LINEAR") == "STEP":
					value = a
				elif path == "rotation":
					value = q_slerp(a, b, u)
				else:
					value = [x + (y - x) * u for x, y in zip(a, b)]
			result.setdefault(node, {})[path] = list(value)
		return result

	def _cached(self, i):
		cache = self.__dict__.setdefault("_accessor_cache", {})
		if i not in cache:
			cache[i] = self.accessor(i)
		return cache[i]


def world_transforms(glb, local):
	"""local: {nodo: (t, q, s)} -> {nodo: (posición mundial, rotación mundial)}; escala ignorada salvo en t."""
	world = {}
	for i in glb.depth_order():
		t, q, s = local.get(i) or glb.rest_trs(i)
		p = glb.parent[i]
		if p < 0:
			world[i] = (list(t), q_norm(q))
		else:
			pt, pq = world[p]
			world[i] = (v_add(pt, q_rotate(pq, t)), q_norm(q_mul(pq, q)))
	return world


def write_glb(doc, binary: bytes) -> bytes:
	payload = json.dumps(doc, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
	payload += b" " * ((-len(payload)) % 4)
	binary = bytes(binary) + b"\0" * ((-len(binary)) % 4)
	total = 12 + 8 + len(payload) + 8 + len(binary)
	return (struct.pack("<III", GLB_MAGIC, 2, total) + struct.pack("<II", len(payload), CHUNK_JSON) + payload +
		struct.pack("<II", len(binary), CHUNK_BIN) + binary)
