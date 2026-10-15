"""Solucionador de poses esquemáticas sobre el rig MakeHuman/MPFB de las exportaciones OpenGym3D.

Una pose se describe con ángulos anatómicos y direcciones de segmentos en el marco del cuerpo
(Y arriba, +Z delante, +X lado izquierdo de la figura). El solucionador devuelve el TRS local de los
huesos animados; la altura se resuelve apoyando el punto más bajo de la malla en el suelo.
"""
import math

from glb_motion import (q_axis, q_conj, q_mul, q_norm, q_rotate, q_slerp, v_add, v_cross, v_len, v_scale, v_sub,
	v_unit, world_transforms)

SPINE = ["spine05", "spine04", "spine03", "spine02", "spine01"]
SPINE_W = [.15, .2, .2, .2, .25]
NECK = ["neck01", "neck02", "neck03", "head"]
NECK_W = [.3, .25, .2, .25]
SIDES = {"L": 1.0, "R": -1.0}
BAR_HEIGHT = 2.35
HIDDEN = [0.0, -50.0, 0.0]


def q_euler(pitch, yaw, roll):
	"""Ry(yaw)·Rx(pitch)·Rz(roll): pitch>0 inclina la cabeza hacia +Z, yaw>0 gira la cara hacia +X."""
	return q_mul(q_axis([0, 1, 0], yaw), q_mul(q_axis([1, 0, 0], pitch), q_axis([0, 0, 1], roll)))


def q_from_frames(a1, a2, b1, b2):
	"""Rotación que lleva el marco ortonormal (a1, a2) a (b1, b2)."""
	a3, b3 = v_cross(a1, a2), v_cross(b1, b2)
	m = [[sum(bk[r] * ak[c] for bk, ak in ((b1, a1), (b2, a2), (b3, a3))) for c in range(3)] for r in range(3)]
	tr = m[0][0] + m[1][1] + m[2][2]
	if tr > 0:
		s = math.sqrt(tr + 1) * 2
		q = [(m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s, (m[1][0] - m[0][1]) / s, s / 4]
	elif m[0][0] > m[1][1] and m[0][0] > m[2][2]:
		s = math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2
		q = [s / 4, (m[0][1] + m[1][0]) / s, (m[0][2] + m[2][0]) / s, (m[2][1] - m[1][2]) / s]
	elif m[1][1] > m[2][2]:
		s = math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2
		q = [(m[0][1] + m[1][0]) / s, s / 4, (m[1][2] + m[2][1]) / s, (m[0][2] - m[2][0]) / s]
	else:
		s = math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2
		q = [(m[0][2] + m[2][0]) / s, (m[1][2] + m[2][1]) / s, s / 4, (m[1][0] - m[0][1]) / s]
	return q_norm(q)


def perp(v, axis):
	d = sum(a * b for a, b in zip(v, axis))
	return v_sub(v, v_scale(axis, d))


def limb_dir(side, elev, azim):
	"""elev 0 = hacia abajo, 90 = horizontal, 180 = arriba; azim 0 = delante, 90 = afuera, 180 = atrás."""
	s, e, a = SIDES[side], math.radians(elev), math.radians(azim)
	return [s * math.sin(e) * math.sin(a), -math.cos(e), math.sin(e) * math.cos(a)]


def limb_front(side, elev, azim, face):
	"""Lleva el vector 'face' (definido con el segmento hacia abajo) a la orientación (elev, azim)."""
	s, a = SIDES[side], math.radians(azim)
	axis = v_cross([0, -1, 0], [s * math.sin(a), 0, math.cos(a)])
	return q_rotate(q_axis(axis, elev), face)


class Rig:
	def __init__(self, glb):
		self.glb = glb
		self.idx = glb.index
		self.rest = world_transforms(glb, {})
		self.order = glb.depth_order()
		self.limbs = {}
		for side in SIDES:
			self.limbs["arm" + side] = self._limb("upperarm01." + side, "upperarm02." + side, "lowerarm01." + side,
				"wrist." + side, "shoulder01." + side)
			self.limbs["leg" + side] = self._limb("upperleg01." + side, "upperleg02." + side,
				"lowerleg01." + side, "foot." + side, "pelvis." + side)
		skin = glb.doc["skins"][0]
		prim = glb.doc["meshes"][0]["primitives"][0]["attributes"]
		pos, joints, weights = (glb.accessor(prim[k]) for k in ("POSITION", "JOINTS_0", "WEIGHTS_0"))
		ibm = glb.accessor(skin["inverseBindMatrices"])
		self.skin_joints = skin["joints"]
		# Muestra fija de vértices: basta para encontrar el punto de apoyo sin rasterizar.
		self.contact = [(pos[i], joints[i], weights[i]) for i in range(0, len(pos), 37)]
		self.ibm = [[[m[c * 4 + r] for c in range(4)] for r in range(4)] for m in ibm]

	def _limb(self, upper, twist, lower, end, parent):
		w = self.rest
		p0, p1, p2 = (w[self.idx[n]][0] for n in (upper, lower, end))
		u0, f0 = v_unit(v_sub(p1, p0)), v_unit(v_sub(p2, p1))
		h0 = v_unit(v_cross(u0, f0))
		bend = math.degrees(math.acos(max(-1, min(1, sum(a * b for a, b in zip(u0, f0))))))
		return dict(upper=self.idx[upper], twist=self.idx[twist], lower=self.idx[lower], end=self.idx[end],
			parent=self.idx[parent], u0=u0, h0=h0, bend=bend, l1=v_len(v_sub(p1, p0)), l2=v_len(v_sub(p2, p1)))

	def rest_q(self, n):
		return self.rest[n][1]

	def solve(self, r):
		"""r: pose resuelta (cuaterniones y vectores en mundo). Devuelve {nodo: (t, q, s)} y posiciones."""
		glb, idx = self.glb, self.idx
		local = {}
		world_q = {}

		def set_world(n, q):
			p = glb.parent[n]
			pq = world_q[p] if p in world_q else self._world_of(p, world_q, local)
			lq = q_norm(q_mul(q_conj(pq), q))
			t, _, s = glb.rest_trs(n)
			local[n] = (t, lq, s)
			world_q[n] = q

		def set_rel(n, d):
			"""D en ejes del mundo en reposo, relativo al padre (L = W0p^-1·D·W0)."""
			p = glb.parent[n]
			pq = world_q[p] if p in world_q else self._world_of(p, world_q, local)
			p0 = self.rest_q(p) if p >= 0 else [0, 0, 0, 1]
			lq = q_norm(q_mul(q_conj(p0), q_mul(d, self.rest_q(n))))
			t, _, s = glb.rest_trs(n)
			local[n] = (t, lq, s)
			world_q[n] = q_norm(q_mul(pq, lq))

		def world_pos(n):
			chain = []
			while n >= 0:
				chain.append(n)
				n = glb.parent[n]
			pos, q = [0.0, 0.0, 0.0], [0, 0, 0, 1]
			for k in reversed(chain):
				t, lq, _ = local[k] if k in local else glb.rest_trs(k)
				pos = v_add(pos, q_rotate(q, t))
				q = q_norm(q_mul(q, lq))
			return pos

		root = idx["root"]
		world_q[idx["Human.rig"]] = [0, 0, 0, 1]
		set_world(root, q_mul(r["body"], self.rest_q(root)))
		local[root] = (list(r.get("rootT", local[root][0])), local[root][1], local[root][2])
		for name, d in zip(SPINE, r["spine"]):
			set_rel(idx[name], d)
		for name, d in zip(NECK, r["neck"]):
			set_rel(idx[name], d)
		for side in SIDES:
			set_rel(idx["clavicle." + side], r["clav" + side])
		for key in ("armL", "armR", "legL", "legR"):
			limb = self.limbs[key]
			d1, elbow, b = self._limb_triple(limb, r[key], world_q, local, world_pos)
			bp = perp(b, d1)
			if v_len(bp) < 1e-3:
				# Flexión indefinida (vector paralelo al segmento): se toma un plano estable.
				bp = perp([0, 0, 1], d1) if abs(d1[2]) < .9 else perp([0, 1, 0], d1)
			hw = v_unit(v_cross(d1, bp))
			# El marco (segmento, eje de bisagra) define el giro del hueso superior sin ambigüedad.
			align = q_from_frames(limb["u0"], limb["h0"], d1, hw)
			set_world(limb["upper"], q_mul(align, self.rest_q(limb["upper"])))
			local[limb["twist"]] = glb.rest_trs(limb["twist"])
			world_q[limb["twist"]] = q_norm(q_mul(world_q[limb["upper"]], local[limb["twist"]][1]))
			hinge = q_axis(hw, elbow - limb["bend"])
			set_world(limb["lower"], q_mul(hinge, q_mul(align, self.rest_q(limb["lower"]))))
			mid = glb.parent[limb["end"]]
			axis = q_rotate(world_q[limb["lower"]], [0, 1, 0])
			# Pronación/supinación: giro del hueso de torsión distal alrededor del propio segmento.
			spin = q_axis(axis, r.get("twist" + key, 0.0) * (1 if key.endswith("L") else -1))
			set_world(mid, q_mul(spin, q_mul(world_q[limb["lower"]], glb.rest_trs(mid)[1])))
		for side in SIDES:
			foot = idx["foot." + side]
			mode, value = r["foot" + side]
			if mode == "world":
				set_world(foot, q_mul(value, self.rest_q(foot)))
			else:
				set_rel(foot, value)
			set_rel(idx["wrist." + side], r["wrist" + side])
		return local

	def _limb_triple(self, limb, spec, world_q, local, world_pos):
		"""(dirección superior, flexión, vector de flexión) en mundo para FK, IK o mezcla de ambos."""
		if spec[0] == "fk":
			return spec[1:]
		if spec[0] == "mix":
			a = self._limb_triple(limb, spec[1], world_q, local, world_pos)
			b = self._limb_triple(limb, spec[2], world_q, local, world_pos)
			u = spec[3]
			return (_vslerp(a[0], b[0], u), a[1] + (b[1] - a[1]) * u, _vslerp(a[2], b[2], u))
		target, pole = spec[1], spec[2]
		if spec[3] == "chest":
			# Objetivo relativo a spine01 en ejes de reposo: acompaña al tronco.
			chest = self.idx["spine01"]
			delta = q_mul(self._world_of(chest, world_q, local), q_conj(self.rest_q(chest)))
			target = v_add(world_pos(chest), q_rotate(delta, target))
			pole = q_rotate(delta, pole)
		return self._ik(limb, world_pos(limb["upper"]), target, pole)

	def _ik(self, limb, start, target, pole):
		"""Dos segmentos: el vértice intermedio queda en el plano (objetivo, polo), del lado del polo."""
		l1, l2 = limb["l1"], limb["l2"]
		span = v_sub(target, start)
		d = max(abs(l1 - l2) + 1e-3, min(l1 + l2 - 1e-4, v_len(span)))
		n = v_unit(span)
		m = perp(pole, n)
		m = v_unit(m) if v_len(m) > 1e-6 else v_unit(perp([0, 0, 1], n))
		a = math.acos(max(-1, min(1, (l1 * l1 + d * d - l2 * l2) / (2 * l1 * d))))
		d1 = v_add(v_scale(n, math.cos(a)), v_scale(m, math.sin(a)))
		knee = v_add(start, v_scale(d1, l1))
		end = v_add(start, v_scale(n, d))
		d2 = v_unit(v_sub(end, knee))
		bend = math.degrees(math.acos(max(-1, min(1, sum(x * y for x, y in zip(d1, d2))))))
		return d1, bend, v_scale(m, -1)

	def _world_of(self, n, world_q, local):
		if n < 0:
			return [0, 0, 0, 1]
		if n in world_q:
			return world_q[n]
		p = self.glb.parent[n]
		q = local[n][1] if n in local else self.glb.rest_trs(n)[1]
		world_q[n] = q_norm(q_mul(self._world_of(p, world_q, local), q))
		return world_q[n]

	def world(self, local):
		return world_transforms(self.glb, local)

	def lowest(self, world):
		"""Altura mínima de la muestra de vértices con skinning lineal."""
		mats = []
		for k, j in enumerate(self.skin_joints):
			t, q = world[j]
			mats.append(_mm(_mat(t, q), self.ibm[k]))
		low = 1e9
		for p, js, ws in self.contact:
			y = 0.0
			for j, w in zip(js, ws):
				if w > 0:
					m = mats[j][1]
					y += w * (m[0] * p[0] + m[1] * p[1] + m[2] * p[2] + m[3])
			low = min(low, y)
		return low


def _vslerp(a, b, u):
	a, b = v_unit(a), v_unit(b)
	d = max(-1.0, min(1.0, sum(x * y for x, y in zip(a, b))))
	if d > .9999:
		return v_unit([x + (y - x) * u for x, y in zip(a, b)])
	theta = math.acos(d)
	s = math.sin(theta)
	if s < 1e-4:
		return a if u < .5 else b
	wa, wb = math.sin((1 - u) * theta) / s, math.sin(u * theta) / s
	return v_unit([wa * x + wb * y for x, y in zip(a, b)])


def _mat(t, q):
	x, y, z, w = q
	return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), t[0]],
		[2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), t[1]],
		[2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), t[2]], [0, 0, 0, 1]]


def _mm(a, b):
	return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]
