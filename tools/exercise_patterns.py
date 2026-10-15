"""Patrones de movimiento esquemáticos para la biblioteca de EnerGym.

Cada función devuelve un Clip con poses clave construidas sobre el solucionador de motion_rig. No son
capturas ni demostraciones validadas: ilustran la secuencia general del patrón con props simplificados.
Convenciones: +Z delante de la figura, +X su lado izquierdo; los vectores 'toward' y objetivos IK se
escriben para el lado izquierdo y se reflejan en el derecho.
"""
from exercise_motions import Clip, arm, arms, feet_ik, ik, leg, legs, measure, pose, REST_ANKLE

STAND = {"anchor": {"root": 1}, "ymode": {"fix": 1}}
FLOOR = {"anchor": {"root": 1}, "ymode": {"ground": 1}}
HANDS_DOWN = arms(8, 90, 12)
BAR_ON_BACK = {"armL": ik("L", [.42, .05, -.13], [.4, -1, -.2], "chest"),
	"armR": ik("R", [.42, .05, -.13], [.4, -1, -.2], "chest")}
FRONT_RACK = {"armL": ik("L", [.19, .13, .2], [.2, -1, .6], "chest"),
	"armR": ik("R", [.19, .13, .2], [.2, -1, .6], "chest")}
GOBLET = {"armL": ik("L", [.07, -.02, .26], [.3, -1, .1], "chest"),
	"armR": ik("R", [.07, -.02, .26], [.3, -1, .1], "chest")}
HANDS_HIPS = {"armL": ik("L", [.2, -.35, .05], [1, 0, -.4], "chest"),
	"armR": ik("R", [.2, -.35, .05], [1, 0, -.4], "chest")}
CHEST_HANDS = {"armL": ik("L", [.12, .0, .3], [.6, -1, 0], "chest"),
	"armR": ik("R", [.12, .0, .3], [.6, -1, 0], "chest")}


def hanging(**kw):
	"""Brazos colgando en vertical respecto al mundo (cargas en bisagra o remos)."""
	return arms(0, 0, 5, frame="world", **kw)


def stand(width=.03, root_y=.91, z=-.04, pole=(.25, 0, 1), feet_z=0.0, **kw):
	return pose(feet_ik(width, z=feet_z, pole=pole), STAND, rootY=root_y, rootXZ=[0, z], **kw)


def held(arms_pose, props):
	return arms_pose, props


# --- Sentadillas ---------------------------------------------------------------------------------------

def squat(name, hold="free", depth=.5, width=.06, lean=38, jump=False, pulse=False, sumo=False, chair=False,
	wall=False, iso=False, smith=False, hack=False, period=3.0):
	"""Sentadilla de pie con pies plantados; 'hold' decide brazos y carga."""
	props = {}
	if sumo:
		width = .22
	arms_top = arms_bottom = HANDS_DOWN
	if hold == "free":
		arms_bottom = arms(85, 5, 5)
	elif hold == "back":
		arms_top = arms_bottom = BAR_ON_BACK
		props["barbell"] = 1
	elif hold == "front":
		arms_top = arms_bottom = FRONT_RACK
		props["barbell"] = 1
		lean = min(lean, 22)
	elif hold == "goblet":
		arms_top = arms_bottom = GOBLET
		props["goblet"] = 1
		lean = min(lean, 28)
	elif hold == "low-dumbbell":
		# Una sola mancuerna vertical cuelga de ambas manos entre las piernas, no ante el pecho.
		arms_top = arms_bottom = {"armL": ik("L", [.045, -.54, .22], [.3, -1, .1], "chest"),
			"armR": ik("R", [.045, -.54, .22], [.3, -1, .1], "chest")}
		props["goblet"] = 1  # El prop de mancuerna única vertical también sirve para el agarre bajo.
		lean = min(lean, 28)
	elif hold == "dumbbells":
		arms_top = arms_bottom = hanging()
		props["dumbbells"] = 1
	elif hold == "chest":
		arms_top = arms_bottom = CHEST_HANDS
	elif hold == "band":
		arms_top = arms_bottom = {"armL": ik("L", [.2, .1, .12], [.6, -1, 0], "chest"),
			"armR": ik("R", [.2, .1, .12], [.6, -1, 0], "chest")}
	pole = (.5 if sumo else .25, 0, 1)
	top_y = .9
	bottom_y = top_y - .42 * depth / .5 if not chair else .52
	z_bottom = -.04 - (.3 if not wall else .12) * min(1.0, depth / .5)
	if hack or wall or smith:
		lean = 12 if (hack or wall) else lean - 10
	if chair:
		props["box"] = [0, .22, -.42, .42, .44, .4]
	if wall:
		props["box"] = [0, 1.0, -.42, 1.2, 2.0, .08]
	top = stand(width, top_y, -.04, pole, body=[4, 0, 0], **arms_top)
	bottom = stand(width, bottom_y, z_bottom, pole, body=[lean, 0, 0], spine=[-6, 0, 0], neck=[-lean * .4, 0, 0],
		**arms_bottom)
	if iso:
		low = dict(bottom, rootY=bottom_y + .015)
		return Clip(name, [(0, bottom), (1.5, low)], period, props, middle=0)
	if pulse:
		mid = stand(width, bottom_y + .08, z_bottom + .04, pole, body=[lean - 4, 0, 0], spine=[-5, 0, 0],
			neck=[-lean * .4, 0, 0], **arms_bottom)
		return Clip(name, [(0, top), (1.0, bottom), (1.4, mid), (1.8, bottom), (2.2, mid), (2.6, bottom)], 3.6,
			props, middle=1.0)
	if jump:
		air = pose(feet_ik(width, pole=pole, height=.28), STAND, rootY=top_y + .3, rootXZ=[0, -.04],
			body=[2, 0, 0], footL=[35, 1], footR=[35, 1], **arms(150, 20, 10))
		land = dict(bottom, rootY=bottom_y + .1)
		return Clip(name, [(0, top), (.7, bottom), (1.1, air), (1.5, land)], 2.3, props, middle=.7)
	return Clip(name, [(0, dict(top, hold=.2)), (period / 2, dict(bottom, hold=.15))], period, props,
		middle=period / 2)


def thruster(name):
	top_press = stand(.06, .91, -.04, body=[2, 0, 0], **arms(172, 70, 8), twistarmL=80, twistarmR=80)
	bottom = stand(.06, .5, -.3, body=[30, 0, 0], neck=[-12, 0, 0], **FRONT_RACK)
	rise = stand(.06, .9, -.04, body=[4, 0, 0], **FRONT_RACK)
	return Clip(name, [(0, top_press), (1.0, rise), (1.6, bottom), (2.4, rise)], 3.0, {"dumbbells": 1},
		middle=1.6)


# --- Zancadas -----------------------------------------------------------------------------------------

def _lunge_pose(front="L", depth=1.0, width=.1, root_extra=0.0, lean=8, rear_z=-.5, front_z=.38, rear_y=0.0,
	**kw):
	back = "R" if front == "L" else "L"
	fx = .12 if front == "L" else .12
	p = pose(STAND, rootY=.9 - .36 * depth + root_extra, rootXZ=[0, -.06], body=[lean, 0, 0], **kw)
	p["leg" + front] = ik(front, [fx, REST_ANKLE[1], front_z], [.15, 0, 1])
	p["leg" + back] = ik(back, [fx, REST_ANKLE[1] + .06 + rear_y, rear_z], [0, -.3, 1])
	p["foot" + back] = [-40, 0]
	return p


def lunge(name, kind="static", hold="free", period=3.2):
	props = {}
	arms_pose = HANDS_HIPS
	if hold == "dumbbells":
		arms_pose = hanging()
		props["dumbbells"] = 1
	elif hold == "back":
		arms_pose = BAR_ON_BACK
		props["barbell"] = 1
	elif hold == "goblet":
		arms_pose = GOBLET
		props["goblet"] = 1
	top = stand(.06, .91, -.04, **arms_pose)
	split_top = _lunge_pose(depth=0, **arms_pose)
	bottom = _lunge_pose(depth=1, **arms_pose)
	if kind == "static":
		return Clip(name, [(0, dict(split_top, hold=.15)), (period / 2, dict(bottom, hold=.15))], period, props,
			middle=period / 2)
	if kind == "bulgarian":
		props["box"] = [0, .22, -.78, .4, .44, .35]
		b_top = _lunge_pose(depth=.15, rear_z=-.7, rear_y=.38, front_z=.32, **arms_pose)
		b_bot = _lunge_pose(depth=1.05, rear_z=-.66, rear_y=.36, front_z=.32, lean=15, **arms_pose)
		b_top["footR"] = b_bot["footR"] = [-70, 0]
		return Clip(name, [(0, dict(b_top, hold=.15)), (period / 2, dict(b_bot, hold=.15))], period, props,
			middle=period / 2)
	if kind in ("reverse", "forward", "walking"):
		lift = _lunge_pose(depth=0, rear_z=-.1, front_z=.1, **arms_pose)
		lift["legR" if kind == "reverse" else "legL"] = ik("R" if kind == "reverse" else "L",
			[.12, REST_ANKLE[1] + .15, -.15 if kind == "reverse" else .15], [0, 0, 1])
		return Clip(name, [(0, top), (.6, lift), (1.3, dict(bottom, hold=.2)), (2.3, lift)], period, props,
			middle=1.3)
	if kind == "jump":
		air = _lunge_pose(depth=0, root_extra=.25, rear_z=-.2, front_z=.2, **arms(40, 20, 60))
		air["legL"] = ik("L", [.12, REST_ANKLE[1] + .25, .1], [0, 0, 1])
		air["legR"] = ik("R", [.12, REST_ANKLE[1] + .25, -.15], [0, -.3, 1])
		b1 = _lunge_pose("L", 1, **arms(40, 20, 60))
		b2 = _lunge_pose("R", 1, **arms(40, 20, 60))
		return Clip(name, [(0, b1), (.5, air), (1.0, b2), (1.5, air)], 2.0, props, middle=1.0)
	raise ValueError(kind)


def side_lunge(name, hold="free", cossack=False, period=3.2):
	props = {}
	arms_pose = CHEST_HANDS
	if hold == "goblet":
		arms_pose = GOBLET
		props["goblet"] = 1
	width = .45 if not cossack else .55
	top = stand(width * .55, .86, -.04, pole=(.4, 0, 1), **arms_pose)
	bottom = pose(STAND, rootY=.52 if cossack else .6, rootXZ=[.32 if cossack else .26, -.2], body=[30, 0, 0],
		neck=[-10, 0, 0], **arms_pose)
	bottom["legL"] = ik("L", [width, REST_ANKLE[1], .02], [.3, 0, 1])
	bottom["legR"] = ik("R", [width, REST_ANKLE[1], .02], [-.2, .4, 1])
	top["legL"] = bottom["legL"]
	top["legR"] = bottom["legR"]
	if cossack:
		bottom["footR"] = [-60, 0]
	other = pose(bottom, rootXZ=[-bottom["rootXZ"][0], -.2])
	other["legL"], other["legR"] = ik("L", [width, REST_ANKLE[1], .02], [-.2, .4, 1]), ik("R", [width, REST_ANKLE[1],
		.02], [.3, 0, 1])
	other["footL"], other["footR"] = (bottom.get("footR", [0, 1]), [0, 1])
	return Clip(name, [(0, top), (.9, dict(bottom, hold=.15)), (1.8, top), (2.7, dict(other, hold=.15))], 3.6,
		props, middle=.9)


def curtsy_lunge(name):
	top = stand(.05, .91, -.04, **HANDS_HIPS)
	bottom = pose(STAND, rootY=.56, rootXZ=[0, -.08], body=[10, 0, 0], **HANDS_HIPS)
	bottom["legL"] = ik("L", [.12, REST_ANKLE[1], .15], [.1, 0, 1])
	bottom["legR"] = ik("R", [-.12, REST_ANKLE[1] + .06, -.42], [-.2, -.3, 1])
	bottom["footR"] = [-40, 0]
	return Clip(name, [(0, top), (1.4, dict(bottom, hold=.2))], 2.8, {}, middle=1.4)


def step_up(name, hold="free", height=.42, box_jump=False):
	props = {"box": [0, height / 2, .42, .5, height, .45]}
	arms_pose = hanging() if hold == "dumbbells" else (HANDS_HIPS if not box_jump else arms(20, 10, 30))
	if hold == "dumbbells":
		props["dumbbells"] = 1
	down = stand(.05, .91, -.08, feet_z=-.05, **arms_pose)
	ready = pose(down)
	ready["legL"] = ik("L", [.12, height + REST_ANKLE[1], .4], [.1, 0, 1])
	up = pose(STAND, rootY=.92 + height, rootXZ=[0, .38], body=[3, 0, 0], **arms_pose)
	up["legL"] = ik("L", [.12, height + REST_ANKLE[1], .4], [.1, 0, 1])
	up["legR"] = ik("R", [.12, height + REST_ANKLE[1], .4], [.1, 0, 1])
	drive = pose(ready, rootY=.75 + height * .5, rootXZ=[0, .12], body=[22, 0, 0])
	if box_jump:
		crouch = stand(.06, .62, -.3, body=[35, 0, 0], **arms(40, 10, 20))
		crouch["armL"] = crouch["armR"] = arm("L", 30, 180, 10)
		crouch["armR"] = arm("R", 30, 180, 10)
		air = pose(feet_ik(.06, z=.2, height=height + .15), STAND, rootY=1.1 + height, rootXZ=[0, .1],
			**arms(150, 15, 10))
		land = pose(feet_ik(.06, z=.4, height=height), STAND, rootY=.65 + height, rootXZ=[0, .2], body=[30, 0, 0],
			**arms(80, 10, 10))
		return Clip(name, [(0, down), (.8, crouch), (1.2, air), (1.5, land), (2.3, up)], 3.2, props, middle=1.2)
	return Clip(name, [(0, down), (.8, ready), (1.4, drive), (2.0, dict(up, hold=.2)), (2.9, drive),
		(3.4, ready)], 4.0, props, middle=2.0)


# --- Bisagra de cadera ---------------------------------------------------------------------------------

def hinge(name, kind="rdl", hold="barbell", period=3.2):
	props = {}
	width = .05
	knee_bottom_y, top_lean, bottom_lean, hips_back = .82, 0, 75, -.22
	arms_pose = hanging()
	if hold == "barbell":
		props["barbell"] = 1
	elif hold in ("dumbbells", "band"):
		props["dumbbells"] = 1 if hold == "dumbbells" else 0
	elif hold == "goblet":
		props["goblet"] = 1
		arms_pose = arms(0, 0, 5, frame="world")
	if kind == "deadlift":
		knee_bottom_y, bottom_lean, hips_back = .62, 55, -.25
	elif kind == "sumo":
		width, knee_bottom_y, bottom_lean, hips_back = .26, .56, 40, -.18
	elif kind == "good-morning":
		arms_pose = BAR_ON_BACK
		knee_bottom_y, bottom_lean, hips_back = .84, 78, -.24
	elif kind == "back-extension":
		return back_extension(name)
	pole = (.45 if kind == "sumo" else .2, 0, 1)
	top = stand(width, .91, -.04, pole, body=[top_lean, 0, 0], **arms_pose)
	bottom = stand(width, knee_bottom_y, hips_back, pole, body=[bottom_lean, 0, 0], spine=[3, 0, 0],
		neck=[-bottom_lean * .5, 0, 0], **arms_pose)
	return Clip(name, [(0, dict(top, hold=.2)), (period / 2, dict(bottom, hold=.1))], period, props,
		middle=period / 2)


def single_leg_rdl(name, hold="free", airplane=False):
	props = {"dumbbellR": 1} if hold == "dumbbell" else {}
	arms_pose = hanging() if hold == "dumbbell" else arms(20, 60, 10)
	top = pose(STAND, rootY=.91, rootXZ=[0, -.04], legL=ik("L", [.1, REST_ANKLE[1], .02], [.1, 0, 1]),
		legR=leg("R", 10, 180, 15), **arms_pose)
	bottom = pose(STAND, rootY=.86, rootXZ=[0, -.12], body=[80, 0, 0], neck=[-35, 0, 0],
		legL=ik("L", [.1, REST_ANKLE[1], .02], [.1, 0, 1]), legR=leg("R", 0, 0, 3), **arms_pose)
	if airplane:
		bottom = pose(bottom, body=[60, 0, 0], spine=[0, 0, 0], **arms(80, 90, 5))
		turned = pose(bottom, body=[60, -30, -10])
		return Clip(name, [(0, top), (1.0, bottom), (2.0, turned), (3.0, bottom)], 4.0, props, middle=1.0)
	return Clip(name, [(0, top), (1.5, dict(bottom, hold=.2))], 3.0, props, middle=1.5)


def swing(name):
	back = stand(.14, .7, -.22, (.35, 0, 1), body=[60, 0, 0], neck=[-25, 0, 0])
	back["armL"] = ik("L", [.04, .5, -.15], [0, 0, -1])
	back["armR"] = ik("R", [-.04, .5, -.15], [0, 0, -1])
	front = stand(.14, .91, -.04, (.35, 0, 1), body=[-3, 0, 0])
	front["armL"] = ik("L", [.04, 1.3, .62], [0, -1, 0])
	front["armR"] = ik("R", [-.04, 1.3, .62], [0, -1, 0])
	return Clip(name, [(0, back), (.7, front)], 1.5, {"goblet": 1}, middle=.7)


def pull_through(name):
	return hinge(name, "rdl", "none")


def back_extension(name):
	"""Hiperextensión: el banco representa el apoyo de cadera; las piernas quedan fijas en el mundo."""
	props = {"box": [0, .45, .12, .45, .9, .3]}
	base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootY=1.0, rootXZ=[0, -.05])
	up = pose(base, legs(0, 0, 5), body=[95, 0, 0], neck=[-20, 0, 0], footL=[15, 0], footR=[15, 0],
		**CHEST_HANDS)
	down = pose(base, legs(60, 0, 5), body=[155, 0, 0], neck=[-10, 0, 0], footL=[15, 0], footR=[15, 0],
		**CHEST_HANDS)
	return Clip(name, [(0, dict(up, hold=.2)), (1.5, down)], 3.0, props, middle=1.5)


def nordic(name):
	"""Nórdico asistido: el cuerpo rígido gira sobre las rodillas con los tobillos fijos."""
	kneel = dict(anchor={"feet": 1}, ymode={"ground": 1})
	up = pose(kneel, legs(0, 0, 90, toward=[0, 0, -1], frame="world"), footL=[60, 0], footR=[60, 0],
		**CHEST_HANDS)
	down = pose(kneel, legs(50, 180, 40, toward=[0, 0, -1], frame="world"), body=[50, 0, 0], footL=[60, 0],
		footR=[60, 0], **arms(70, 15, 40))
	return Clip(name, [(0, up), (2.0, down), (2.6, down)], 3.6, {}, middle=2.0)


# --- Puentes de cadera -----------------------------------------------------------------------------------

def bridge(name, kind="floor", hold="none", single=False, march=False, frog=False, iso=False):
	props = {}
	supine = dict(anchor={"root": 1}, ymode={"ground": 1}, rootXZ=[0, 0])
	arms_pose = arms(10, 40, 5)
	if hold == "barbell":
		props["barbell"] = 1
		arms_pose = {"armL": ik("L", [.3, -.55, .05], [.5, 0, -1], "chest"),
			"armR": ik("R", [.3, -.55, .05], [.5, 0, -1], "chest")}
	elif hold == "dumbbell":
		props["goblet"] = 1
		arms_pose = {"armL": ik("L", [.06, -.5, .14], [1, 0, 0], "chest"),
			"armR": ik("R", [.06, -.5, .14], [1, 0, 0], "chest")}
	lift_y = .42
	if kind == "bench":
		props["box"] = [0, .21, -.62, 1.0, .42, .35]
		down_body, up_body, down_y, up_y = -40, -95, .34, .62
	else:
		down_body, up_body, down_y, up_y = -90, -112, .12, .32
	if frog:
		leg_l, leg_r = ik("L", [.3, .1, .45], [1, .5, 0]), ik("R", [.3, .1, .45], [1, .5, 0])
	else:
		leg_l, leg_r = ik("L", [.14, REST_ANKLE[1], .55], [0, 1, .2]), ik("R", [.14, REST_ANKLE[1], .55],
			[0, 1, .2])
	fixed = dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, 0])
	down = pose(fixed, rootY=down_y + (.06 if kind == "bench" else 0), body=[down_body, 0, 0], legL=leg_l,
		legR=leg_r, **arms_pose)
	up = pose(fixed, rootY=up_y + (.12 if kind == "bench" else 0), body=[up_body, 0, 0], neck=[25, 0, 0],
		legL=leg_l, legR=leg_r, **arms_pose)
	if single:
		up["legR"] = down["legR"] = leg("R", 90, 0, 10)
		down["legR"] = leg("R", 70, 0, 10)
	if iso:
		return Clip(name, [(0, up), (1.5, pose(up, rootY=up["rootY"] - .01))], 3.0, props, middle=0)
	if march:
		m1 = pose(up)
		m1["legL"] = leg("L", 100, 0, 90)
		m2 = pose(up)
		m2["legR"] = leg("R", 100, 0, 90)
		return Clip(name, [(0, down), (.8, up), (1.4, m1), (2.0, up), (2.6, m2), (3.2, up)], 4.0, props, middle=1.4)
	return Clip(name, [(0, dict(down, hold=.15)), (1.3, dict(up, hold=.4))], 2.8, props, middle=1.3)


# --- Gemelos --------------------------------------------------------------------------------------------

def calf_raise(name, hold="none", single=False, seated=False, support=False):
	props = {}
	arms_pose = HANDS_DOWN
	if hold == "dumbbells":
		props["dumbbells"] = 1
		arms_pose = hanging()
	elif hold == "barbell":
		props["barbell"] = 1
		arms_pose = BAR_ON_BACK
	if support:
		props["box"] = [0, .55, .5, .5, 1.1, .1]
		arms_pose = {"armL": ik("L", [.25, 1.08, .42], [1, -1, 0]), "armR": ik("R", [.25, 1.08, .42], [1, -1, 0])}
	if seated:
		props["box"] = [0, .22, -.15, .45, .44, .45]
		base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootY=.56, rootXZ=[0, -.15])
		down = pose(base, legs(90, 8, 90, frame="world"), **arms(40, 20, 80))
		up = pose(down, footL=[30, 0], footR=[30, 0])
		down["footL"] = down["footR"] = [-5, 0]
		return Clip(name, [(0, down), (1.0, dict(up, hold=.3))], 2.2, props, middle=1.0)
	down = stand(.05, .91, -.04, **arms_pose)
	up = stand(.05, .99, -.04, footL=[35, 1], footR=[35, 1], **arms_pose)
	up["legL"] = ik("L", [.06, REST_ANKLE[1] + .09, .0], [.1, 0, 1])
	up["legR"] = ik("R", [.06, REST_ANKLE[1] + .09, .0], [.1, 0, 1])
	down["legL"], down["legR"] = ik("L", [.06, REST_ANKLE[1], .0], [.1, 0, 1]), ik("R", [.06, REST_ANKLE[1], .0],
		[.1, 0, 1])
	if single:
		down["legR"] = up["legR"] = leg("R", 5, 180, 70)
	return Clip(name, [(0, dict(down, hold=.1)), (1.0, dict(up, hold=.3))], 2.2, props, middle=1.0)


# --- Bases de postura ----------------------------------------------------------------------------------

SUPINATED, PRONATED = -80, 80
BENCH_LYING = [0, .21, -.25, .38, .42, 1.25]


def standing(lean=0, width=.06, root_y=.91, z=-.04, **kw):
	return stand(width, root_y, z, body=[lean, 0, 0], **kw)


def seated(seat_h=.45, lean=0, feet_z=.42, **kw):
	return pose(STAND, rootY=seat_h + .1, rootXZ=[0, -.1], body=[lean, 0, 0],
		legL=ik("L", [.16, REST_ANKLE[1], feet_z], [.1, .2, 1]), legR=ik("R", [.16, REST_ANKLE[1], feet_z], [.1, .2, 1]),
		**kw)


def seat_box(seat_h=.45):
	return [0, seat_h / 2, -.12, .42, seat_h, .42]


def lying(incline=0, root_y=.56, floor=False, **kw):
	"""Boca arriba en banco (o suelo) con los pies apoyados; incline en grados sobre la horizontal."""
	feet_y = REST_ANKLE[1]
	feet = ({"legL": ik("L", [.14, feet_y, .55], [0, 1, .2]), "legR": ik("R", [.14, feet_y, .55], [0, 1, .2])}
		if floor else {"legL": ik("L", [.25, feet_y, .48], [.2, .3, 1]), "legR": ik("R", [.25, feet_y, .48], [.2, .3, 1])})
	return pose(STAND, feet, rootY=(.14 if floor else root_y) + incline * .004, rootXZ=[0, .05 if not floor else 0],
		body=[-90 + incline, 0, 0], **kw)


def cycle(name, a, b, period=2.6, props=None, hold_a=.15, hold_b=.15):
	return Clip(name, [(0, dict(a, hold=hold_a)), (period / 2, dict(b, hold=hold_b))], period, props or {},
		middle=period / 2)


# --- Bíceps ---------------------------------------------------------------------------------------------

def curl(name, kind="dumbbell"):
	twist = {"hammer": 0, "reverse": PRONATED, "barbell-reverse": PRONATED}.get(kind, SUPINATED)
	props = {"barbell": 1} if kind.startswith("barbell") else ({"dumbbells": 1} if kind in (
		"dumbbell", "hammer", "zottman", "incline") else {})
	if kind == "concentration":
		base = seated(lean=35, neck=[-15, 0, 0], armL=arm("L", 20, 30, 40))
		down = pose(base, armR=arm("R", 0, 0, 10, frame="world"), twistarmR=SUPINATED)
		up = pose(base, armR=arm("R", 0, 0, 135, toward=[-.3, .3, 1], frame="world"), twistarmR=SUPINATED)
		return cycle(name, down, up, 2.6, {"dumbbellR": 1, "box": seat_box()})
	if kind == "incline":
		down = lying(45, root_y=.58, **arms(0, 0, 8, frame="world"), twistarmL=SUPINATED, twistarmR=SUPINATED)
		up = lying(45, root_y=.58, **arms(10, 0, 135, toward=[0, 0, 1], frame="world"), twistarmL=SUPINATED,
			twistarmR=SUPINATED)
		props["box"] = [0, .35, -.3, .4, .7, .7]
		return cycle(name, down, up, 2.8, props)
	if kind == "preacher":
		base = seated(lean=10)
		down = pose(base, **arms(55, 5, 25), twistarmL=SUPINATED, twistarmR=SUPINATED)
		up = pose(base, **arms(55, 5, 130), twistarmL=SUPINATED, twistarmR=SUPINATED)
		return cycle(name, down, up, 2.6, {"barbell": 1, "box": seat_box()})
	if kind == "wrist":
		base = seated(lean=30, neck=[-10, 0, 0], **arms(25, 5, 75, toward=[0, -.2, 1]), twistarmL=SUPINATED,
			twistarmR=SUPINATED)
		return cycle(name, pose(base, wristL=40, wristR=40), pose(base, wristL=-45, wristR=-45), 1.8,
			{"barbell": 1, "box": seat_box()})
	down = standing(**arms(4, 10, 8), twistarmL=twist, twistarmR=twist)
	up = standing(**arms(14, 6, 138), twistarmL=twist, twistarmR=twist)
	if kind == "zottman":
		up = pose(up, twistarmL=PRONATED, twistarmR=PRONATED)
		mid = pose(up, twistarmL=SUPINATED, twistarmR=SUPINATED)
		low = pose(down, twistarmL=PRONATED, twistarmR=PRONATED)
		return Clip(name, [(0, down), (1.0, mid), (1.3, up), (2.4, low)], 3.0, props, middle=1.0)
	if kind == "cable":
		props = {"barbell": 1}
	return cycle(name, down, up, 2.6, props)


# --- Tríceps ---------------------------------------------------------------------------------------------

def triceps(name, kind="overhead"):
	if kind in ("overhead", "cable-overhead"):
		down = standing(**arms(168, 15, 150, toward=[0, 0, -1]))
		up = standing(**arms(172, 12, 10, toward=[0, 0, -1]))
		return cycle(name, down, up, 2.6, {"goblet": 1} if kind == "overhead" else {})
	if kind == "kickback":
		base = dict(standing(55, root_y=.82, z=-.16), neck=[-25, 0, 0])
		bent = pose(base, **arms(55, 180, 95, toward=[0, -1, 0], frame="world"))
		ext = pose(base, **arms(70, 180, 5, toward=[0, -1, 0], frame="world"))
		return cycle(name, bent, ext, 2.4, {"dumbbells": 1})
	if kind in ("pushdown", "band-pushdown"):
		up = standing(8, **arms(10, 5, 105, toward=[0, 0, 1]), twistarmL=PRONATED, twistarmR=PRONATED)
		down = standing(8, **arms(8, 5, 8, toward=[0, 0, 1]), twistarmL=PRONATED, twistarmR=PRONATED)
		return cycle(name, up, down, 2.4, {"barbell": 1} if kind == "pushdown" else {})
	if kind == "skull":
		top = lying(**arms(100, 8, 5), twistarmL=PRONATED, twistarmR=PRONATED)
		bottom = lying(**arms(115, 8, 125, toward=[0, 1, 0]), twistarmL=PRONATED, twistarmR=PRONATED)
		return cycle(name, top, bottom, 2.6, {"barbell": 1, "box": BENCH_LYING})
	if kind == "skull-dumbbell":
		top = lying(**arms(100, 10, 5))
		bottom = lying(**arms(115, 10, 125, toward=[0, 1, 0]))
		return cycle(name, top, bottom, 2.6, {"dumbbells": 1, "box": BENCH_LYING})
	raise ValueError(kind)


# --- Hombro: elevaciones y presses -------------------------------------------------------------------------

def raise_(name, kind="lateral", props=None):
	props = {"dumbbells": 1} if props is None else props
	if kind == "lateral":
		return cycle(name, standing(**arms(10, 90, 15)), standing(**arms(88, 85, 15)), 2.6, props)
	if kind == "front":
		return cycle(name, standing(**arms(8, 15, 8), twistarmL=PRONATED, twistarmR=PRONATED),
			standing(**arms(90, 8, 8), twistarmL=PRONATED, twistarmR=PRONATED), 2.6, props)
	if kind == "reverse-fly":
		base = dict(standing(60, root_y=.83, z=-.16), neck=[-25, 0, 0])
		return cycle(name, pose(base, **arms(5, 30, 20, frame="world")),
			pose(base, **arms(80, 90, 20, frame="world")), 2.6, props)
	if kind == "seated-reverse-fly":
		base = seated(lean=20)
		return cycle(name, pose(base, **arms(90, 10, 10)), pose(base, **arms(90, 95, 10)), 2.6,
			{"box": seat_box()})
	if kind == "upright-row":
		low = standing(**arms(5, 10, 8), twistarmL=PRONATED, twistarmR=PRONATED)
		high = standing(**arms(82, 80, 125, toward=[0, -1, .3]), twistarmL=PRONATED, twistarmR=PRONATED)
		return cycle(name, low, high, 2.6, props)
	if kind == "shrug":
		low = standing(**arms(4, 10, 6))
		high = standing(clavL=[14, 0], clavR=[14, 0], **arms(4, 10, 6))
		return cycle(name, low, high, 2.0, props, .1, .4)
	if kind == "face-pull":
		start = standing(**arms(88, 5, 5), twistarmL=PRONATED, twistarmR=PRONATED)
		end = standing(**arms(90, 95, 110, toward=[0, 1, 0]))
		return cycle(name, start, end, 2.6, {})
	if kind == "pull-apart":
		return cycle(name, standing(**arms(90, 8, 5), twistarmL=PRONATED, twistarmR=PRONATED),
			standing(**arms(90, 88, 5), twistarmL=PRONATED, twistarmR=PRONATED), 2.4, {})
	if kind == "cable-lateral":
		low = standing(armL=arm("L", 8, -30, 15), armR=arm("R", 25, 120, 10))
		high = standing(armL=arm("L", 88, 85, 15), armR=arm("R", 25, 120, 10))
		return cycle(name, low, high, 2.6, {})
	if kind == "machine-lateral":
		base = seated()
		return cycle(name, pose(base, **arms(10, 90, 90)), pose(base, **arms(85, 88, 90)), 2.6,
			{"box": seat_box()})
	raise ValueError(kind)


def press_overhead(name, kind="dumbbell"):
	twist = PRONATED if kind != "arnold" else 0
	props = {"barbell": 1} if kind in ("barbell", "push-press") else {"dumbbells": 1}
	bottom_arms = arms(80, 80, 100, toward=[0, 1, 0])
	top_arms = arms(172, 70, 8)
	if kind in ("barbell", "push-press"):
		bottom_arms = arms(60, 45, 120, toward=[0, 1, 0])
		top_arms = arms(172, 55, 8)
	if kind == "machine":
		base = seated()
		return cycle(name, pose(base, **bottom_arms, twistarmL=twist, twistarmR=twist),
			pose(base, **top_arms, twistarmL=twist, twistarmR=twist), 2.6, {"box": seat_box()})
	if kind == "band":
		props = {}
	if kind == "landmine":
		low = standing(15, armL=arm("L", 60, 10, 110, toward=[0, 1, 0]), armR=arm("R", 8, 60, 15))
		high = standing(15, armL=arm("L", 125, 10, 10), armR=arm("R", 8, 60, 15))
		return cycle(name, low, high, 2.6, {"dumbbellL": 1})
	bottom = standing(**bottom_arms, twistarmL=twist, twistarmR=twist)
	top = standing(**top_arms, twistarmL=twist, twistarmR=twist)
	if kind == "arnold":
		start = standing(**arms(70, 5, 145, toward=[0, 1, 0]), twistarmL=SUPINATED, twistarmR=SUPINATED)
		return Clip(name, [(0, start), (.8, bottom), (1.5, dict(top, hold=.2)), (2.3, bottom)], 3.2, props,
			middle=1.5)
	if kind == "push-press":
		dip = stand(.06, .8, -.08, body=[5, 0, 0], **bottom_arms, twistarmL=twist, twistarmR=twist)
		return Clip(name, [(0, bottom), (.5, dip), (.9, top), (1.8, bottom)], 2.6, props, middle=.9)
	return cycle(name, bottom, top, 2.6, props)


def pike_pushup(name, wall=False):
	"""Flexión en pica: cadera alta y cabeza hacia el suelo entre las manos."""
	hands = {"armL": ik("L", [.26, .05, .45], [.4, 0, -1]), "armR": ik("R", [.26, .05, .45], [.4, 0, -1])}
	feet = {"legL": ik("L", [.12, REST_ANKLE[1], -.55], [0, 0, 1]),
		"legR": ik("R", [.12, REST_ANKLE[1], -.55], [0, 0, 1])}
	if wall:
		hands = {"armL": ik("L", [.26, .05, .18], [.6, 0, -1]), "armR": ik("R", [.26, .05, .18], [.6, 0, -1])}
		up = pose(dict(anchor={"hands": 1}, ymode={"fix": 1}, rootY=1.0, handsXZ=[0, .18]), hands, legs(0, 0, 3),
			body=[178, 0, 0], neck=[30, 0, 0])
		down = pose(up, rootY=.97, body=[176, 0, 0])
		return Clip(name, [(0, up), (1.5, down)], 3.0, {"box": [0, 1.0, -.08, 1.2, 2.0, .08]}, middle=0)
	base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, -.05])
	up = pose(base, hands, feet, rootY=1.0, body=[105, 0, 0], neck=[10, 0, 0], footL=[20, 0], footR=[20, 0])
	down = pose(base, hands, feet, rootY=.86, body=[115, 0, 0], neck=[20, 0, 0], footL=[20, 0], footR=[20, 0])
	return cycle(name, up, down, 2.6, {})


# --- Remos ---------------------------------------------------------------------------------------------

def row(name, kind="barbell"):
	if kind in ("barbell", "pendlay", "dumbbell", "t-bar", "band"):
		lean = 70 if kind == "pendlay" else 50
		base = dict(standing(lean, root_y=.8 if kind != "pendlay" else .78, z=-.18), neck=[-lean * .4, 0, 0])
		twist = PRONATED if kind in ("barbell", "pendlay") else 0
		props = {"barbell": 1} if kind in ("barbell", "pendlay", "t-bar") else (
			{"dumbbells": 1} if kind == "dumbbell" else {})
		low = pose(base, **arms(0, 10, 8, frame="world"), twistarmL=twist, twistarmR=twist)
		high = pose(base, **arms(50, 165, 95, toward=[0, -.2, 1], frame="world"), twistarmL=twist, twistarmR=twist)
		if kind == "t-bar":
			low = pose(base, **arms(0, 0, 8, frame="world"))
			high = pose(base, **arms(45, 170, 100, toward=[0, -.2, 1], frame="world"))
		return cycle(name, low, high, 2.6, props)
	if kind == "one-arm":
		base = pose(STAND, rootY=.84, rootXZ=[0, -.15], body=[78, 0, 0], neck=[-30, 0, 0],
			legL=ik("L", [.12, .5, .1], [0, 0, 1]), legR=ik("R", [.18, REST_ANKLE[1], -.05], [.2, 0, 1]),
			armL=ik("L", [.2, .5, .55], [1, 0, 0]))
		base["legL"] = ik("L", [.12, .6, -.35], [0, -1, .5])
		low = pose(base, armR=arm("R", 0, 0, 8, frame="world"), twistarmR=0)
		high = pose(base, armR=arm("R", 45, 170, 95, toward=[0, -.2, 1], frame="world"), twistarmR=0)
		return cycle(name, low, high, 2.6, {"dumbbellR": 1, "box": [0, .25, .05, .45, .5, 1.1]})
	if kind == "chest-supported":
		base = pose(STAND, rootY=.75, rootXZ=[0, -.2], body=[45, 0, 0], neck=[-20, 0, 0],
			legL=ik("L", [.16, REST_ANKLE[1], -.45], [.1, 0, 1]), legR=ik("R", [.16, REST_ANKLE[1], -.45], [.1, 0, 1]))
		low = pose(base, **arms(0, 10, 8, frame="world"))
		high = pose(base, **arms(45, 165, 100, toward=[0, -.2, 1], frame="world"))
		return cycle(name, low, high, 2.6, {"dumbbells": 1, "box": [0, .45, .05, .4, .9, .25]})
	if kind in ("seated-cable", "machine", "band-seated"):
		base = seated(lean=5) if kind == "machine" else pose(
			dict(anchor={"root": 1}, ymode={"ground": 1}, rootXZ=[0, -.2]), legs(80, 8, 20), body=[-5, 0, 0],
			footL=[-15, 0], footR=[-15, 0])
		low = pose(base, **arms(85, 5, 5), twistarmL=0, twistarmR=0)
		high = pose(base, **arms(40, 165, 100, toward=[0, .2, 1]), twistarmL=0, twistarmR=0)
		props = {"box": seat_box()} if kind == "machine" else {}
		return cycle(name, low, high, 2.6, props)
	if kind == "renegade":
		return renegade_row(name)
	if kind == "inverted":
		bar_z = .0
		grip = [.3, 1.0, bar_z]
		base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, -.7])
		lo = pose(base, legs(0, 0, 3), rootY=.42, body=[-75, 0, 0], armL=ik("L", grip, [1, -1, 0]),
			armR=ik("R", grip, [1, -1, 0]), footL=[0, 0], footR=[0, 0])
		hi = pose(lo, rootY=.62, body=[-80, 0, 0])
		return cycle(name, lo, hi, 2.6, {"barbell": 1})
	raise ValueError(kind)


def renegade_row(name):
	plank = plank_pose(.0)
	plank_r = pose(plank)
	plank_r["armR"] = arm("R", 120, 175, 100, toward=[0, -1, 0], frame="world")
	return Clip(name, [(0, plank), (.9, plank_r), (1.8, plank)], 2.6, {"dumbbells": 1}, middle=.9)


# --- Dominadas y suspensiones -------------------------------------------------------------------------------

def hang(name, kind="pull-up"):
	from motion_rig import BAR_HEIGHT
	width = .34 if kind not in ("chin-up", "close") else .2
	grip = [width, BAR_HEIGHT, 0]
	twist = SUPINATED if kind == "chin-up" else PRONATED
	base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, -.02], twistarmL=twist, twistarmR=twist,
		armL=ik("L", grip, [1, -.3, -.2]), armR=ik("R", grip, [1, -.3, -.2]))
	if kind == "chin-up":
		base["armL"], base["armR"] = ik("L", grip, [.3, -.3, 1]), ik("R", grip, [.3, -.3, 1])
	low_y = BAR_HEIGHT - 1.18
	low = pose(base, legs(10, 0, 25), rootY=low_y)
	high = pose(base, legs(10, 0, 25), rootY=low_y + .5, neck=[-15, 0, 0])
	props = {"bar": [0, 0]}
	if kind in ("pull-up", "chin-up", "assisted"):
		if kind == "assisted":
			props["box"] = [0, .4, -.05, .5, .8, .4]
			low.update(legs(10, 0, 90))
			high.update(legs(10, 0, 90))
		return cycle(name, low, high, 3.0, props, .2, .3)
	if kind == "negative":
		return Clip(name, [(0, dict(high, hold=.4)), (2.6, dict(low, hold=.3))], 3.6, props, middle=1.3)
	if kind == "scapular":
		up = pose(low, rootY=low_y + .06, clavL=[-8, 0], clavR=[-8, 0])
		low = pose(low, clavL=[10, 0], clavR=[10, 0])
		return cycle(name, low, up, 2.2, props)
	if kind == "dead-hang":
		sway = pose(low, rootY=low_y - .005, body=[2, 0, 0])
		return Clip(name, [(0, low), (2.0, sway)], 4.0, props, middle=0)
	if kind in ("knee-raise", "leg-raise"):
		up = pose(low, legs(100, 0, 100 if kind == "knee-raise" else 8), body=[-10 if kind == "leg-raise" else -5, 0, 0])
		return cycle(name, low, up, 2.8, props)
	if kind == "muscle-up":
		pull = pose(high, rootY=low_y + .62, body=[15, 0, 0])
		over = pose(STAND, legs(10, 0, 20), rootY=BAR_HEIGHT - .15, rootXZ=[0, -.15], body=[25, 0, 0],
			armL=ik("L", grip, [.3, 0, -1]), armR=ik("R", grip, [.3, 0, -1]), twistarmL=twist, twistarmR=twist)
		top = pose(over, rootY=BAR_HEIGHT + .05, rootXZ=[0, -.08], body=[10, 0, 0])
		return Clip(name, [(0, low), (1.0, pull), (1.5, over), (2.2, dict(top, hold=.3)), (3.2, pull)], 4.0, props,
			middle=2.2)
	raise ValueError(kind)


def pulldown(name, kind="wide"):
	base = seated(lean=-12)
	if kind == "band":
		base = standing(-5)
	if kind == "straight-arm":
		base = dict(standing(30, root_y=.88, z=-.1), neck=[-10, 0, 0])
		hi = pose(base, **arms(120, 10, 10), twistarmL=PRONATED, twistarmR=PRONATED)
		lo = pose(base, **arms(25, 10, 10), twistarmL=PRONATED, twistarmR=PRONATED)
		return cycle(name, hi, lo, 2.6, {"barbell": 1})
	twist = 0 if kind == "close" else PRONATED
	azim = 20 if kind == "close" else 70
	hi = pose(base, **arms(165, azim, 10), twistarmL=twist, twistarmR=twist)
	lo = pose(base, **arms(30, azim + 20 if kind != "close" else 10, 130, toward=[0, 1, .3]), twistarmL=twist,
		twistarmR=twist)
	props = {"barbell": 1} if kind == "wide" else {}
	if kind != "band":
		props["box"] = seat_box()
	return cycle(name, hi, lo, 2.6, props)


# --- Pecho -------------------------------------------------------------------------------------------------

def bench_press(name, kind="barbell", incline=0, floor=False, hold="barbell", width="normal"):
	azim_bottom = {"normal": 70, "close": 25, "wide": 80}[width]
	twist = PRONATED if hold == "barbell" else 30
	props = {"barbell": 1} if hold == "barbell" else {"dumbbells": 1}
	props["box"] = ([0, .21, -.25, .38, .42, 1.25] if not incline else [0, .35, -.3, .4, .7, .8]) if not floor else None
	if floor:
		del props["box"]
	top = lying(incline, floor=floor, **arms(90 + incline * .3, 6, 5), twistarmL=twist, twistarmR=twist)
	bottom = lying(incline, floor=floor, **arms(90 + incline * .3, azim_bottom, 95 if not floor else 85,
		toward=[0, 0, 1]), twistarmL=twist, twistarmR=twist)
	return cycle(name, top, bottom, 2.8, props)


def fly(name, kind="dumbbell"):
	if kind == "dumbbell":
		top = lying(**arms(90, 6, 15), twistarmL=0, twistarmR=0)
		bottom = lying(**arms(90, 85, 25, toward=[0, 0, 1]), twistarmL=0, twistarmR=0)
		return cycle(name, top, bottom, 2.8, {"dumbbells": 1, "box": BENCH_LYING})
	if kind == "pullover":
		top = lying(**arms(90, 0, 15))
		bottom = lying(**arms(170, 0, 25, toward=[0, 0, 1]))
		return cycle(name, top, bottom, 2.8, {"goblet": 1, "box": BENCH_LYING})
	if kind in ("pec-deck", "machine-press", "reverse-pec-deck"):
		base = seated()
		if kind == "machine-press":
			return cycle(name, pose(base, **arms(85, 65, 95, toward=[0, 0, 1]), twistarmL=PRONATED,
				twistarmR=PRONATED), pose(base, **arms(88, 8, 5), twistarmL=PRONATED, twistarmR=PRONATED), 2.6,
				{"box": seat_box()})
		if kind == "reverse-pec-deck":
			return raise_(name, "seated-reverse-fly")
		return cycle(name, pose(base, **arms(88, 90, 30, toward=[0, 0, 1])), pose(base, **arms(88, 8, 30,
			toward=[1, 0, 0])), 2.6, {"box": seat_box()})
	if kind == "crossover":
		base = dict(standing(15, z=-.08), neck=[-5, 0, 0])
		return cycle(name, pose(base, **arms(115, 95, 25)), pose(base, **arms(60, 8, 15)), 2.6, {})
	if kind == "low-fly":
		base = standing(5)
		return cycle(name, pose(base, **arms(25, 80, 15)), pose(base, **arms(100, 5, 15)), 2.6, {})
	if kind == "band-press":
		base = standing(5)
		return cycle(name, pose(base, **arms(80, 70, 100, toward=[0, 0, 1]), twistarmL=PRONATED, twistarmR=PRONATED),
			pose(base, **arms(88, 8, 5), twistarmL=PRONATED, twistarmR=PRONATED), 2.4, {})
	raise ValueError(kind)


def dips(name, kind="bars"):
	if kind == "bench":
		base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, -.05],
			armL=ik("L", [.22, .48, -.3], [0, .4, -1]), armR=ik("R", [.22, .48, -.3], [0, .4, -1]),
			legL=ik("L", [.14, REST_ANKLE[1], .6], [0, 1, .2]), legR=ik("R", [.14, REST_ANKLE[1], .6], [0, 1, .2]))
		up = pose(base, rootY=.5, body=[5, 0, 0])
		down = pose(base, rootY=.28, body=[8, 0, 0])
		return cycle(name, up, down, 2.6, {"box": [0, .22, -.42, .5, .44, .35]})
	if kind == "machine":
		base = seated()
		return cycle(name, pose(base, **arms(20, 165, 100, toward=[0, 0, 1])), pose(base, **arms(5, 175, 8)), 2.4,
			{"box": seat_box()})
	grip = [.27, 1.5, -.02]
	base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, 0], armL=ik("L", grip, [0, .3, -1]),
		armR=ik("R", grip, [0, .3, -1]))
	up = pose(base, legs(20, 0, 95), rootY=1.25, body=[8, 0, 0])
	down = pose(base, legs(20, 0, 95), rootY=.98, body=[22, 0, 0])
	return cycle(name, up, down, 2.6, {})


# --- Flexiones y planchas -----------------------------------------------------------------------------------

def plank_pose(drop=0.0, hands_width=.27, knees=False, forearms=False, incline_h=0.0, decline_h=0.0,
	hands_z=.0, **kw):
	"""Plancha alta (o sobre antebrazos/rodillas) con manos plantadas; drop baja el pecho hacia el suelo."""
	hand_y = .05 + incline_h
	body_pitch = 72 if not knees else 62
	root_y = .55 - drop + (incline_h * .55) + decline_h * .4
	if forearms:
		root_y = .3 + decline_h * .4
		body_pitch = 80
		# Codos bajo los hombros y antebrazos apoyados hacia delante.
		hands = {"armL": arm("L", 0, 0, 90, toward=[0, 0, 1], frame="world"),
			"armR": arm("R", 0, 0, 90, toward=[0, 0, 1], frame="world")}
		feet = legs(0, 0, 3)
		fields = dict(body=[body_pitch - 8, 0, 0], footL=[0, 0], footR=[0, 0])
		fields.update(kw)
		return pose(FLOOR, feet, hands, **fields)
	hands = {"armL": ik("L", [hands_width, hand_y, .45 + hands_z], [.6, .2, -1]),
		"armR": ik("R", [hands_width, hand_y, .45 + hands_z], [.6, .2, -1])}
	feet = legs(0, 0, 3) if not knees else legs(0, 0, 100, toward=[0, 0, -1])
	pitch = body_pitch + drop * 30
	if decline_h:
		pitch = 100 + drop * 20
	fields = dict(rootY=root_y, body=[pitch, 0, 0], footL=[0, 0], footR=[0, 0], neck=[-5, 0, 0])
	fields.update(kw)
	return pose(dict(anchor={"hands": 1}, ymode={"fix": 1}, handsXZ=[0, .45 + hands_z]), feet, hands, **fields)


def pushup(name, kind="wide"):
	opts = {"wide": dict(hands_width=.42), "diamond": dict(hands_width=.07), "knee": dict(knees=True),
		"incline": dict(incline_h=.45), "decline": dict(decline_h=.45), "wall": dict()}
	props = {}
	if kind == "wall":
		base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, -.35])
		hands = {"armL": ik("L", [.27, 1.3, .3], [.7, -.3, -1]), "armR": ik("R", [.27, 1.3, .3], [.7, -.3, -1])}
		feet = {"legL": ik("L", [.12, REST_ANKLE[1], -.45], [0, 0, 1]), "legR": ik("R", [.12, REST_ANKLE[1], -.45],
			[0, 0, 1])}
		up = pose(base, hands, feet, rootY=.9, body=[20, 0, 0], footL=[25, 1], footR=[25, 1])
		down = pose(base, hands, feet, rootY=.9, rootXZ=[0, -.17], body=[34, 0, 0], footL=[25, 1], footR=[25, 1])
		return cycle(name, up, down, 2.6, {"box": [0, 1.0, .38, 1.2, 2.0, .08]})
	o = opts[kind]
	if kind == "incline":
		props["box"] = [0, .22, .45, .7, .44, .35]
	if kind == "decline":
		props["box"] = [0, .22, -1.15, .5, .44, .4]
	up = plank_pose(0, **o)
	down = plank_pose(.3 if kind not in ("incline",) else .28, **o)
	return cycle(name, up, down, 2.6, props)


def archer_pushup(name):
	up = plank_pose(0, hands_width=.55)
	left = plank_pose(.25, hands_width=.55, rootXZ=[.18, 0])
	right = plank_pose(.25, hands_width=.55, rootXZ=[-.18, 0])
	for p in (left, right):
		p["anchor"] = {"root": 1}
	up["anchor"] = {"root": 1}
	return Clip(name, [(0, up), (1.0, left), (2.0, up), (3.0, right)], 4.0, {}, middle=1.0)


def hindu_pushup(name):
	start = plank_pose(0)
	pike_pose = pose(start, rootY=.95, body=[115, 0, 0], neck=[15, 0, 0])
	swoop = plank_pose(.3, neck=[-15, 0, 0])
	cobra = plank_pose(.25, body=[60, 0, 0], neck=[-30, 0, 0])
	cobra["rootY"] = .3
	return Clip(name, [(0, pike_pose), (1.0, swoop), (1.8, cobra), (2.8, pike_pose)], 3.6, {}, middle=1.8)


def plank(name, kind="high"):
	if kind == "forearm":
		base = plank_pose(forearms=True)
		return Clip(name, [(0, base), (1.5, pose(base, lift=.01))], 3.0, {}, middle=0)
	if kind == "shoulder-taps":
		base = plank_pose(0, hands_width=.3)

		def keys_for_rig(rig):
			# FK de la postura de apoyo: objetivos sobre los hombros reales de este rig.
			shoulders = measure(rig, base, ("upperarm01.L", "upperarm01.R"))
			taps = {}
			for moving, opposite in (("R", "L"), ("L", "R")):
				x, y, z = shoulders["upperarm01." + opposite]
				# ik refleja X para el lado derecho; convertir desde la posición mundial del hombro contrario.
				target = [x if moving == "L" else -x, y + .015, z]
				taps[moving] = pose(base, **{"arm" + moving: ik(moving, target, [.7, .2, -.4])})
			return [(0, base), (.6, taps["R"]), (1.2, base), (1.8, base), (2.4, taps["L"]), (3.0, base)]

		return Clip(name, keys_for_rig, 3.6, {}, middle=.6)
	if kind == "jacks":
		closed = plank_pose(0)
		open_ = pose(closed)
		open_["legL"], open_["legR"] = leg("L", 18, 90, 3), leg("R", 18, 90, 3)
		return cycle(name, closed, open_, 1.2, {}, 0, 0)
	if kind == "mountain-climbers":
		a = plank_pose(.05)
		a["legL"] = leg("L", 95, 5, 110)
		b = plank_pose(.05)
		b["legR"] = leg("R", 95, 5, 110)
		return Clip(name, [(0, a), (.45, b)], .9, {}, middle=.45)
	if kind == "stir":
		base = plank_pose(forearms=True)
		return Clip(name, [(0, pose(base, body=[72, 0, 6])), (1.0, pose(base, body=[74, 6, 0])),
			(2.0, pose(base, body=[72, 0, -6])), (3.0, pose(base, body=[70, -6, 0]))], 4.0,
			{"box": [0, .2, .35, .45, .4, .45]}, middle=1.0)
	high = plank_pose(0)
	return Clip(name, [(0, high), (1.5, pose(high, rootY=high["rootY"] + .01))], 3.0, {}, middle=0)


def side_plank(name, kind="hold", copenhagen=False):
	"""De lado sobre el antebrazo derecho, con la cara anterior hacia la cámara (+X)."""
	base = pose(dict(anchor={"root": 1}, ymode={"ground": 1}), legs(0, 0, 3), body=[76, 0, 0], spin=90,
		armR=arm("R", 0, 0, 90, toward=[-1, 0, 0], frame="world"), armL=arm("L", 10, 60, 10), footL=[0, 0],
		footR=[0, 0])
	props = {}
	if copenhagen:
		base["legL"] = leg("L", 2, 0, 3)
		base["legR"] = leg("R", 25, 0, 70)
		base["body"] = [70, 0, 0]
		props = {"box": [0, .2, -.75, .4, .4, .45]}
	if kind == "dips":
		down = pose(base, body=[88, 0, 0])
		return cycle(name, base, down, 2.4, props)
	return Clip(name, [(0, base), (1.5, pose(base, body=[base["body"][0] - 1, 0, 0]))], 3.0, props, middle=0)


def reverse_plank(name):
	base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootY=.42, rootXZ=[0, 0])
	hands = {"armL": ik("L", [.24, .05, -.55], [0, 0, 1]), "armR": ik("R", [.24, .05, -.55], [0, 0, 1])}
	p = pose(base, hands, legs(0, 0, 3), body=[-72, 0, 0], neck=[20, 0, 0], footL=[40, 0], footR=[40, 0])
	return Clip(name, [(0, p), (1.5, pose(p, rootY=.41))], 3.0, {}, middle=0)


# --- Core en el suelo -------------------------------------------------------------------------------------

SUPINE = {"anchor": {"root": 1}, "ymode": {"ground": 1}, "rootXZ": [0, 0]}
PRONE = {"anchor": {"root": 1}, "ymode": {"ground": 1}, "rootXZ": [0, 0]}


def supine(knees=True, **kw):
	"""Boca arriba en el suelo; con rodillas flexionadas y pies apoyados por defecto."""
	legs_pose = legs(50, 8, 100) if knees else legs(0, 6, 3)
	fields = dict(body=[-90, 0, 0])
	fields.update(kw)
	return pose(SUPINE, legs_pose, **fields)


def core(name, kind="crunch"):
	behind_head = {"armL": ik("L", [.12, .32, -.06], [1, .3, 0], "chest"),
		"armR": ik("R", [.12, .32, -.06], [1, .3, 0], "chest")}
	if kind == "crunch":
		down = supine(**behind_head)
		up = supine(spine=[35, 0, 0], neck=[15, 0, 0], **behind_head)
		return cycle(name, down, up, 2.4)
	if kind == "cable-crunch":
		base = pose(dict(anchor={"root": 1}, ymode={"ground": 1}), legs(0, 0, 95, toward=[0, 0, -1]),
			footL=[50, 0], footR=[50, 0], **behind_head)
		return cycle(name, pose(base, body=[15, 0, 0]), pose(base, body=[35, 0, 0], spine=[50, 0, 0], neck=[20, 0, 0]),
			2.6)
	if kind == "machine-crunch":
		base = seated(**arms(120, 20, 120, toward=[0, 0, 1]))
		return cycle(name, base, pose(base, spine=[45, 0, 0], neck=[15, 0, 0]), 2.4, {"box": seat_box()})
	if kind == "bicycle":
		a = supine(knees=False, spine=[30, 0, 25], neck=[10, 0, 0], legL=leg("L", 95, 5, 100),
			legR=leg("R", 25, 5, 5), **behind_head)
		b = supine(knees=False, spine=[30, 0, -25], neck=[10, 0, 0], legR=leg("R", 95, 5, 100),
			legL=leg("L", 25, 5, 5), **behind_head)
		return Clip(name, [(0, a), (.8, b)], 1.6, {}, middle=.8)
	if kind == "leg-raise":
		down = supine(knees=False, legL=leg("L", 15, 4, 3), legR=leg("R", 15, 4, 3), **arms(10, 30, 5))
		up = supine(knees=False, legL=leg("L", 90, 4, 3), legR=leg("R", 90, 4, 3), **arms(10, 30, 5))
		return cycle(name, down, up, 2.8)
	if kind == "reverse-crunch":
		down = supine(legL=leg("L", 90, 4, 90), legR=leg("R", 90, 4, 90), **arms(10, 30, 5))
		up = supine(body=[-115, 0, 0], legL=leg("L", 120, 4, 100), legR=leg("R", 120, 4, 100), **arms(10, 30, 5))
		return cycle(name, down, up, 2.4)
	if kind == "dead-bug":
		a = supine(knees=False, legL=leg("L", 90, 5, 90), legR=leg("R", 20, 5, 5), armL=arm("L", 90, 5, 5),
			armR=arm("R", 170, 5, 5))
		b = supine(knees=False, legR=leg("R", 90, 5, 90), legL=leg("L", 20, 5, 5), armR=arm("R", 90, 5, 5),
			armL=arm("L", 170, 5, 5))
		mid = supine(knees=False, **legs(90, 5, 90), **arms(90, 5, 5))
		return Clip(name, [(0, mid), (.9, a), (1.8, mid), (2.7, b)], 3.6, {}, middle=.9)
	if kind == "hollow":
		p = supine(knees=False, spine=[22, 0, 0], neck=[15, 0, 0], legL=leg("L", 30, 3, 3), legR=leg("R", 30, 3, 3),
			**arms(165, 8, 5))
		return Clip(name, [(0, p), (1.5, pose(p, spine=[24, 0, 0]))], 3.0, {}, middle=0)
	if kind in ("flutter", "scissor"):
		elev_a, elev_b = (40, 20) if kind == "flutter" else (30, 30)
		az_a, az_b = (4, 4) if kind == "flutter" else (35, -10)
		a = supine(knees=False, spine=[10, 0, 0], legL=leg("L", elev_a, az_a, 3), legR=leg("R", elev_b, az_b, 3),
			**arms(10, 30, 5))
		b = supine(knees=False, spine=[10, 0, 0], legL=leg("L", elev_b, az_b, 3), legR=leg("R", elev_a, az_a, 3),
			**arms(10, 30, 5))
		return Clip(name, [(0, a), (.5, b)], 1.0, {}, middle=.5)
	if kind == "v-up":
		down = supine(knees=False, legL=leg("L", 8, 4, 3), legR=leg("R", 8, 4, 3), **arms(170, 10, 5))
		up = supine(knees=False, body=[-135, 0, 0], spine=[25, 0, 0], legL=leg("L", 120, 4, 3),
			legR=leg("R", 120, 4, 3), **arms(120, 8, 5))
		return cycle(name, down, up, 2.6)
	if kind in ("russian-twist", "russian-twist-db"):
		base = dict(SUPINE, body=[-45, 0, 0], legL=leg("L", 70, 8, 100), legR=leg("R", 70, 8, 100))
		hands = {"armL": ik("L", [.06, -.1, .32], [1, -1, 0], "chest"),
			"armR": ik("R", [.06, -.1, .32], [1, -1, 0], "chest")}
		a = pose(base, hands, spine=[10, 0, 40])
		b = pose(base, hands, spine=[10, 0, -40])
		return Clip(name, [(0, a), (.8, b)], 1.6, {"goblet": 1} if kind.endswith("db") else {}, middle=.8)
	if kind == "heel-touches":
		a = supine(spine=[20, 25, 0], neck=[15, 0, 0], **arms(15, 10, 5))
		b = supine(spine=[20, -25, 0], neck=[15, 0, 0], **arms(15, 10, 5))
		return Clip(name, [(0, a), (.7, b)], 1.4, {}, middle=.7)
	if kind == "toe-touch":
		down = supine(knees=False, legL=leg("L", 90, 4, 3), legR=leg("R", 90, 4, 3), **arms(95, 5, 5))
		up = pose(down, spine=[40, 0, 0], neck=[15, 0, 0], **arms(125, 5, 5))
		return cycle(name, down, up, 2.0)
	if kind == "superman":
		base = pose(PRONE, legs(0, 6, 3), body=[90, 0, 0], **arms(170, 15, 5))
		up = pose(PRONE, body=[90, 0, 0], spine=[-25, 0, 0], neck=[-15, 0, 0], **arms(178, 15, 5))
		up["legL"], up["legR"] = leg("L", 18, 180, 3), leg("R", 18, 180, 3)
		return cycle(name, base, up, 2.6, None, .2, .5)
	if kind in ("bird-dog", "glute-kickback", "donkey-kick", "fire-hydrant", "band-kickback", "cat-cow",
		"thread-the-needle", "childs-pose"):
		return quadruped(name, kind)
	if kind == "clamshell":
		base = pose(SUPINE, body=[90, 0, 0], spin=90, legL=leg("L", 45, 0, 90), legR=leg("R", 45, 0, 90),
			armR=arm("R", 170, 0, 30), armL=arm("L", 30, 30, 60))
		open_ = pose(base, legL=leg("L", 45, 45, 90))
		return cycle(name, base, open_, 2.2)
	raise ValueError(kind)


def quadruped(name, kind="bird-dog"):
	"""A cuatro patas: manos bajo los hombros y rodillas bajo la cadera."""
	base = pose(dict(anchor={"root": 1}, ymode={"ground": 1}, rootXZ=[0, 0]), legs(90, 5, 90, toward=[0, -1, 0],
		frame="world"), body=[90, 0, 0], neck=[-10, 0, 0], footL=[45, 0], footR=[45, 0],
		**arms(90, 5, 5, frame="world"))
	base["armL"], base["armR"] = arm("L", 0, 0, 5, frame="world"), arm("R", 0, 0, 5, frame="world")
	base["legL"], base["legR"] = leg("L", 0, 0, 90, toward=[0, 0, -1], frame="world"), leg("R", 0, 0, 90,
		toward=[0, 0, -1], frame="world")
	if kind == "bird-dog":
		a = pose(base, armL=arm("L", 90, 0, 5, frame="world"))
		a["legR"] = leg("R", 92, 180, 5, frame="world")
		b = pose(base, armR=arm("R", 90, 0, 5, frame="world"))
		b["legL"] = leg("L", 92, 180, 5, frame="world")
		return Clip(name, [(0, base), (.9, dict(a, hold=.3)), (1.8, base), (2.7, dict(b, hold=.3))], 3.6, {},
			middle=.9)
	if kind in ("glute-kickback", "band-kickback"):
		up = pose(base)
		up["legL"] = leg("L", 95, 180, 15, frame="world")
		return cycle(name, base, up, 2.2)
	if kind == "donkey-kick":
		up = pose(base)
		up["legL"] = leg("L", 85, 180, 90, toward=[0, 1, 0], frame="world")
		return cycle(name, base, up, 2.2)
	if kind == "fire-hydrant":
		up = pose(base)
		up["legL"] = leg("L", 60, 90, 90, toward=[0, 0, -1], frame="world")
		return cycle(name, base, up, 2.2)
	if kind == "cat-cow":
		cat = pose(base, spine=[30, 0, 0], neck=[25, 0, 0])
		cow = pose(base, spine=[-30, 0, 0], neck=[-30, 0, 0])
		return cycle(name, cat, cow, 4.0, None, .5, .5)
	if kind == "thread-the-needle":
		thread = pose(base, spine=[10, 0, -50], neck=[0, 0, -20])
		thread["armL"] = arm("L", 90, -80, 5, frame="world")
		thread["armL"] = arm("L", 20, -70, 5, frame="world")
		opened = pose(base, spine=[0, 0, 45], armL=arm("L", 175, 60, 5, frame="world"))
		return Clip(name, [(0, base), (1.2, dict(thread, hold=.4)), (2.6, base), (3.6, dict(opened, hold=.4))], 4.8,
			{}, middle=1.2)
	if kind == "childs-pose":
		return mobility(name, "childs-pose")
	raise ValueError(kind)


def floor_flow(name, kind="burpee"):
	"""Transiciones suelo-pie: burpee, squat thrust, gusano y gateo del oso."""
	top = stand(.06, .91, -.04, **HANDS_DOWN)
	crouch = stand(.08, .48, -.32, body=[55, 0, 0], neck=[-25, 0, 0])
	crouch["armL"], crouch["armR"] = ik("L", [.25, .05, .32], [.5, 0, -1]), ik("R", [.25, .05, .32], [.5, 0, -1])
	plank_p = plank_pose(0, hands_z=-.13)
	if kind in ("burpee", "squat-thrust"):
		keys = [(0, top), (.5, crouch), (.9, plank_p), (1.4, crouch)]
		if kind == "burpee":
			down = plank_pose(.3, hands_z=-.13)
			air = pose(feet_ik(.06, height=.25), STAND, rootY=1.18, rootXZ=[0, -.04], **arms(170, 15, 5))
			keys = [(0, top), (.5, crouch), (.9, plank_p), (1.3, down), (1.7, plank_p), (2.1, crouch), (2.5, air)]
			return Clip(name, keys, 3.0, {}, middle=1.3)
		return Clip(name, keys, 1.9, {}, middle=.9)
	if kind == "inchworm":
		fold = stand(.06, .86, -.25, body=[120, 0, 0], neck=[0, 0, 0])
		fold["armL"], fold["armR"] = ik("L", [.25, .05, .32], [.5, 0, -1]), ik("R", [.25, .05, .32], [.5, 0, -1])
		walk = plank_pose(0, hands_z=.2)
		walk["anchor"] = {"root": 1}
		walk["rootXZ"] = [0, .05]
		return Clip(name, [(0, top), (1.0, fold), (2.2, dict(walk, hold=.3)), (3.4, fold)], 4.4, {}, middle=2.2)
	if kind == "bear-crawl":
		base = plank_pose(0)
		base["legL"] = leg("L", 85, 5, 95)
		base["legR"] = leg("R", 85, 5, 95)
		base["rootY"] = .5
		base["body"] = [85, 0, 0]
		a = pose(base, legL=leg("L", 105, 5, 115))
		a["armR"] = ik("R", [.27, .12, .55], [.6, .2, -1])
		b = pose(base, legR=leg("R", 105, 5, 115))
		b["armL"] = ik("L", [.27, .12, .55], [.6, .2, -1])
		return Clip(name, [(0, a), (.6, b)], 1.2, {}, middle=.6)
	raise ValueError(kind)


# --- Locomoción, saltos y cardio --------------------------------------------------------------------------

def run_cycle(name, kind="high-knees"):
	"""Marcha/carrera en el sitio con braceo; los pies alternan y la pelvis rebota ligeramente."""
	knee_a = {"high-knees": (100, 110), "butt-kicks": (10, 150), "march": (60, 80), "aqua": (80, 100),
		"stair": (75, 95)}[kind]
	lift = .06 if kind in ("high-knees", "butt-kicks") else 0.0
	ground = dict(anchor={"feet": 1}, ymode={"ground": 1})

	def phase(side):
		other = "R" if side == "L" else "L"
		p = pose(ground, body=[6 if kind != "butt-kicks" else 2, 0, 0], lift=lift)
		p["leg" + side] = leg(side, knee_a[0], 3, knee_a[1]) if kind != "butt-kicks" else leg(side, 10, 180, 150)
		p["leg" + other] = leg(other, 3, 90, 4)
		p["arm" + side] = arm(side, 35, 180, 90, toward=[0, 1, 0])
		p["arm" + other] = arm(other, 45, 5, 90)
		p["foot" + side] = [20, 0]
		return p

	period = .9 if kind in ("high-knees", "butt-kicks") else 1.4
	props = {}
	if kind == "stair":
		props["box"] = [0, .1, .25, .6, .2, .4]
	return Clip(name, [(0, phase("L")), (period / 2, phase("R"))], period, props, middle=period / 4)


def jump(name, kind="tuck"):
	top = stand(.06, .91, -.04, **HANDS_DOWN)
	if kind in ("tuck", "pogo"):
		crouch = stand(.08, .66 if kind == "tuck" else .84, -.18 if kind == "tuck" else -.06,
			body=[30 if kind == "tuck" else 8, 0, 0], **arms(20, 180, 20))
		crouch["armL"], crouch["armR"] = arm("L", 25, 170, 20), arm("R", 25, 170, 20)
		if kind == "tuck":
			air = pose(STAND, rootY=1.35, rootXZ=[0, -.04], legL=leg("L", 110, 5, 120), legR=leg("R", 110, 5, 120),
				**arms(70, 10, 60))
			air["anchor"] = {"root": 1}
			return Clip(name, [(0, top), (.5, crouch), (.9, air), (1.3, crouch)], 1.8, {}, middle=.9)
		air = pose(feet_ik(.06, height=.15), STAND, rootY=1.07, rootXZ=[0, -.04], footL=[30, 1], footR=[30, 1],
			**HANDS_DOWN)
		return Clip(name, [(0, crouch), (.3, air)], .6, {}, middle=.3)
	if kind in ("skater", "shuffle"):
		def side_pose(sign):
			p = pose(STAND, rootY=.72 if kind == "skater" else .8, rootXZ=[.3 * sign, -.1],
				body=[30 if kind == "skater" else 20, 0, 0], neck=[-10, 0, 0])
			stance, free = ("L", "R") if sign > 0 else ("R", "L")
			p["leg" + stance] = ik(stance, [.42 if stance == "L" else .42, REST_ANKLE[1], 0], [.3, 0, 1])
			if kind == "skater":
				p["leg" + free] = leg(free, 25, 200 if free == "R" else 200, 60)
				p["arm" + stance] = arm(stance, 60, -40, 20)
				p["arm" + free] = arm(free, 50, 150, 20)
			else:
				p["leg" + free] = ik(free, [-.05, REST_ANKLE[1], 0], [.3, 0, 1])
				p.update(CHEST_HANDS)
			return p
		if kind == "skater":
			a, b = side_pose(1), side_pose(-1)
			a["legL"] = ik("L", [.42 + .3, REST_ANKLE[1], 0], [.3, 0, 1])
			b["legR"] = ik("R", [.42 + .3, REST_ANKLE[1], 0], [.3, 0, 1])
			for p, s in ((a, 1), (b, -1)):
				p["rootXZ"] = [.55 * s, -.1]
			return Clip(name, [(0, a), (.7, b)], 1.4, {}, middle=.7)
		a = stand(.3, .82, -.08, body=[20, 0, 0], **CHEST_HANDS)
		b = stand(.12, .84, -.08, body=[20, 0, 0], **CHEST_HANDS)
		return Clip(name, [(0, a), (.4, b)], .8, {}, middle=.4)
	if kind == "deceleration":
		run = pose(dict(anchor={"feet": 1}, ymode={"ground": 1}), body=[15, 0, 0], legL=leg("L", 70, 3, 80),
			legR=leg("R", 15, 180, 40), armL=arm("L", 35, 180, 90, toward=[0, 1, 0]), armR=arm("R", 45, 5, 90))
		brake = stand(.15, .62, -.25, body=[35, 0, 0], neck=[-15, 0, 0], **arms(60, 20, 60))
		return Clip(name, [(0, run), (.8, dict(brake, hold=.4)), (2.0, top)], 2.6, {}, middle=.8)
	raise ValueError(kind)


def jumping_jack_plain(name):
	closed = stand(.0, .91, -.04, **HANDS_DOWN)
	open_ = stand(.3, .88, -.04, **arms(165, 80, 10))
	return cycle(name, closed, open_, 1.2, None, 0, 0)


def cardio_machine(name, kind="bike"):
	if kind in ("bike", "air-bike", "elliptical", "stair-climber"):
		seat = kind in ("bike", "air-bike")
		base = pose(STAND, rootY=.88 if seat else 1.0, rootXZ=[0, -.15], body=[25 if seat else 8, 0, 0],
			neck=[-15 if seat else 0, 0, 0])
		hands = {"armL": ik("L", [.22, 1.0 if seat else 1.15, .35], [.5, -1, 0]),
			"armR": ik("R", [.22, 1.0 if seat else 1.15, .35], [.5, -1, 0])}
		r, cy, cz = (.17, .3, .22) if seat else (.14, .32 if kind == "elliptical" else .35, .05)
		keys = []
		steps = 6
		period = 1.2 if kind != "stair-climber" else 1.6
		for k in range(steps):
			import math as _m
			a = 2 * _m.pi * k / steps
			p = pose(base, hands)
			if kind == "stair-climber":
				ly = .12 + .18 * (.5 + .5 * _m.cos(a))
				ry = .12 + .18 * (.5 - .5 * _m.cos(a))
				p["legL"] = ik("L", [.14, ly, .08], [0, 0, 1])
				p["legR"] = ik("R", [.14, ry, .08], [0, 0, 1])
			else:
				p["legL"] = ik("L", [.14, cy + r * _m.sin(a), cz + r * _m.cos(a)], [0, 0, 1])
				p["legR"] = ik("R", [.14, cy - r * _m.sin(a), cz - r * _m.cos(a)], [0, 0, 1])
			if kind == "air-bike":
				p["armL"] = ik("L", [.22, 1.0, .35 + .12 * _m.cos(a)], [.5, -1, 0])
				p["armR"] = ik("R", [.22, 1.0, .35 - .12 * _m.cos(a)], [.5, -1, 0])
			if kind == "elliptical":
				p["armL"] = ik("L", [.25, 1.1, .3 - .1 * _m.cos(a)], [.5, -1, 0])
				p["armR"] = ik("R", [.25, 1.1, .3 + .1 * _m.cos(a)], [.5, -1, 0])
			keys.append((period * k / steps, p))
		props = {"box": [0, .38, -.2, .28, .1 + .66, .3]} if seat else {}
		if seat:
			props["box"] = [0, .35, -.22, .3, .7, .3]
		return Clip(name, keys, period, props, middle=period / 2)
	if kind == "rowing":
		base = dict(anchor={"root": 1}, ymode={"fix": 1}, rootY=.32)
		catch = pose(base, rootXZ=[0, .25], body=[30, 0, 0], legL=ik("L", [.14, .2, .75], [.2, 1, .5]),
			legR=ik("R", [.14, .2, .75], [.2, 1, .5]), **arms(80, 5, 5))
		finish = pose(base, rootXZ=[0, -.25], body=[-15, 0, 0], legL=ik("L", [.14, .2, .75], [.2, 1, .5]),
			legR=ik("R", [.14, .2, .75], [.2, 1, .5]), **arms(40, 160, 110, toward=[0, .2, 1]))
		return Clip(name, [(0, catch), (.8, finish), (1.1, finish)], 2.2,
			{"box": [0, .12, 0, .3, .24, 1.6]}, middle=.8)
	if kind == "ski-erg":
		up = standing(5, **arms(165, 15, 15))
		down = stand(.06, .78, -.22, body=[55, 0, 0], neck=[-20, 0, 0], **arms(25, 175, 10, frame="world"))
		return Clip(name, [(0, up), (.6, down)], 1.6, {}, middle=.6)
	raise ValueError(kind)


def swim(name, kind="kick"):
	base = pose(dict(anchor={"root": 1}, ymode={"fix": 1}, rootY=.6, rootXZ=[0, 0]), body=[90, 0, 0],
		neck=[-15, 0, 0], **arms(175, 10, 5))
	if kind == "kick":
		a = pose(base, legL=leg("L", 15, 3, 15), legR=leg("R", 8, 180, 5))
		b = pose(base, legR=leg("R", 15, 3, 15), legL=leg("L", 8, 180, 5))
		return Clip(name, [(0, a), (.35, b)], .7, {"box": [0, .6, .85, .4, .04, .5]}, middle=.35)
	if kind == "backstroke":
		back = pose(base, body=[-90, 0, 0], neck=[15, 0, 0])
		a = pose(back, spine=[0, 0, 15], armL=arm("L", 175, 10, 5), armR=arm("R", 5, 30, 5),
			legL=leg("L", 10, 3, 15), legR=leg("R", 5, 180, 5))
		mid = pose(back, armL=arm("L", 90, 5, 5), armR=arm("R", 90, 5, 5))
		b = pose(back, spine=[0, 0, -15], armR=arm("R", 175, 10, 5), armL=arm("L", 5, 30, 5),
			legR=leg("R", 10, 3, 15), legL=leg("L", 5, 180, 5))
		return Clip(name, [(0, a), (.6, mid), (1.2, b), (1.8, mid)], 2.4, {}, middle=1.2)
	raise ValueError(kind)


# --- Máquinas de pierna y glúteo ----------------------------------------------------------------------------

def leg_machine(name, kind="extension"):
	if kind == "extension":
		base = pose(STAND, rootY=.55, rootXZ=[0, -.1], body=[-8, 0, 0], **arms(15, 40, 10))
		bent = pose(base, legs(90, 5, 95, frame="world"))
		ext = pose(base, legs(90, 5, 10, frame="world"))
		ext["legL"], ext["legR"] = leg("L", 88, 5, 8, frame="world"), leg("R", 88, 5, 8, frame="world")
		return cycle(name, bent, ext, 2.4, {"box": [0, .25, -.15, .42, .5, .45]})
	if kind == "seated-curl":
		base = pose(STAND, rootY=.55, rootXZ=[0, -.1], body=[-8, 0, 0], **arms(15, 40, 10))
		return cycle(name, pose(base, legs(90, 5, 10, frame="world")), pose(base, legs(90, 5, 100, frame="world")),
			2.4, {"box": [0, .25, -.15, .42, .5, .45]})
	if kind in ("lying-curl", "band-curl"):
		base = pose(PRONE, body=[90, 0, 0], neck=[-20, 0, 0], **arms(150, 30, 90, toward=[0, -1, 0]))
		return cycle(name, pose(base, legs(0, 4, 5)), pose(base, legs(0, 4, 110, toward=[0, 1, 0])), 2.4,
			{"box": [0, .2, .2, .45, .4, 1.4]} if kind == "lying-curl" else {})
	if kind == "leg-press":
		base = pose(dict(anchor={"root": 1}, ymode={"fix": 1}, rootY=.45, rootXZ=[0, -.2]), body=[-55, 0, 0],
			**arms(15, 40, 10))
		bent = pose(base, legL=ik("L", [.18, 1.0, .25], [.3, 1, 0]), legR=ik("R", [.18, 1.0, .25], [.3, 1, 0]))
		ext = pose(base, legL=ik("L", [.18, 1.25, .62], [.3, 1, 0]), legR=ik("R", [.18, 1.25, .62], [.3, 1, 0]))
		return cycle(name, bent, ext, 2.8, {"box": [0, .2, -.35, .45, .4, .6]})
	if kind in ("abduction", "adduction"):
		base = pose(STAND, rootY=.55, rootXZ=[0, -.1], body=[-5, 0, 0], **arms(15, 40, 10))
		closed = pose(base, legs(85, 5, 90, frame="world"))
		open_ = pose(base, legs(85, 40, 90, frame="world"))
		a, b = (closed, open_) if kind == "abduction" else (open_, closed)
		return cycle(name, a, b, 2.4, {"box": [0, .25, -.15, .42, .5, .45]})
	if kind in ("kickback-machine", "cable-kickback"):
		base = standing(25 if kind == "kickback-machine" else 15, z=-.06)
		base["legL"] = leg("L", 3, 0, 15)
		up = pose(base)
		up["legL"] = leg("L", 50, 180, 20)
		return cycle(name, base, up, 2.2)
	if kind == "lateral-walk":
		a = stand(.22, .82, -.1, body=[18, 0, 0], **HANDS_HIPS)
		b = stand(.08, .82, -.1, body=[18, 0, 0], **HANDS_HIPS)
		return Clip(name, [(0, a), (.6, b)], 1.2, {}, middle=.6)
	raise ValueError(kind)


# --- Movilidad y estiramientos ------------------------------------------------------------------------------

def child_pose():
	return pose(dict(anchor={"feet": 1}, ymode={"ground": 1}), body=[118, 0, 0], neck=[10, 0, 0],
		legL=leg("L", 55, 0, 150, toward=[0, 0, -1], frame="world"),
		legR=leg("R", 55, 0, 150, toward=[0, 0, -1], frame="world"), footL=[60, 0], footR=[60, 0],
		**arms(80, 10, 5, frame="world"))


def kneeling_lunge(front="L", depth=0.0, lean=-5, rear_up=False, **kw):
	"""Media rodilla: rodilla trasera apoyada; depth adelanta la cadera."""
	back = "R" if front == "L" else "L"
	p = pose(STAND, rootY=.6 - depth * .08, rootXZ=[0, -.1 + depth * .18], body=[lean, 0, 0], **kw)
	p["leg" + front] = ik(front, [.13, REST_ANKLE[1], .45], [.1, 0, 1])
	p["leg" + back] = leg(back, 15 + depth * 20, 180, 95 if not rear_up else 150, toward=[0, 1 if rear_up else -.2,
		-1 if not rear_up else 0], frame="world")
	p["foot" + back] = [40, 0]
	return p


def mobility(name, kind):
	if kind == "childs-pose":
		child = child_pose()
		return Clip(name, [(0, child), (2.0, pose(child, spine=[5, 0, 0]))], 4.0, {}, middle=0)
	if kind in ("hip-flexor", "couch"):
		a = kneeling_lunge(depth=0, rear_up=kind == "couch", **HANDS_HIPS)
		b = kneeling_lunge(depth=1, rear_up=kind == "couch", lean=-10, **HANDS_HIPS)
		props = {"box": [0, .6, -.62, 1.2, 1.2, .08]} if kind == "couch" else {}
		return cycle(name, a, b, 4.0, props, .3, 1.0)
	if kind == "worlds-greatest":
		low = kneeling_lunge(depth=1, lean=55)
		low["armR"] = ik("R", [.2, .05, .4], [-.5, 0, -1])
		low["armL"] = ik("L", [.22, .08, .4], [.5, 0, 1])
		low["legR"] = leg("R", 35, 180, 15, frame="world")
		low["rootY"] = .55
		twist = pose(low, spine=[0, 0, 55], neck=[0, 0, 25])
		twist["armL"] = arm("L", 175, 70, 5, frame="world")
		return Clip(name, [(0, low), (1.5, dict(twist, hold=.6)), (3.2, low)], 4.0, {}, middle=1.5)
	if kind in ("ankle-wall", "prep-ankle"):
		a = kneeling_lunge(depth=0, **arms(80, 10, 60))
		b = kneeling_lunge(depth=1.2, **arms(80, 10, 30))
		b["legL"] = ik("L", [.13, REST_ANKLE[1], .45], [.1, 0, 1])
		props = {"box": [0, .6, .78, 1.2, 1.2, .08]} if kind == "ankle-wall" else {}
		return cycle(name, a, b, 2.6, props)
	if kind == "calf-wall":
		base = pose(STAND, rootY=.88, rootXZ=[0, -.25], body=[25, 0, 0],
			armL=ik("L", [.25, 1.35, .3], [1, -1, 0]), armR=ik("R", [.25, 1.35, .3], [1, -1, 0]),
			legL=ik("L", [.12, REST_ANKLE[1], .05], [.1, 0, 1]), legR=ik("R", [.12, REST_ANKLE[1], -.6], [0, 0, 1]))
		deep = pose(base, rootXZ=[0, -.18], body=[30, 0, 0])
		return cycle(name, base, deep, 4.0, {"box": [0, 1.0, .38, 1.2, 2.0, .08]}, .3, 1.0)
	if kind == "quad":
		base = standing(armL=arm("L", 15, 180, 100, toward=[0, 1, 0]), armR=arm("R", 30, 70, 10))
		base["legL"] = leg("L", 20, 180, 150, toward=[0, 1, 0])
		base["legL"] = leg("L", 5, 180, 155)
		base["armL"] = ik("L", [.15, .45, -.28], [1, 0, 0], "world")
		base["legR"] = ik("R", [.1, REST_ANKLE[1], .0], [.1, 0, 1])
		deep = pose(base)
		deep["legL"] = leg("L", 15, 180, 155)
		deep["armL"] = ik("L", [.15, .5, -.36], [1, 0, 0], "world")
		return cycle(name, base, deep, 4.0, {}, .4, 1.0)
	if kind == "hamstring":
		base = stand(.05, .9, -.06, body=[10, 0, 0], **HANDS_HIPS)
		base["legL"] = ik("L", [.12, REST_ANKLE[1], .35], [0, 1, 1])
		base["footL"] = [-30, 0]
		deep = pose(base, rootY=.8, rootXZ=[0, -.18], body=[55, 0, 0], neck=[-20, 0, 0])
		deep["legR"] = ik("R", [.12, REST_ANKLE[1], -.06], [.1, 0, 1])
		base["legR"] = deep["legR"]
		return cycle(name, base, deep, 4.0, {}, .3, 1.0)
	if kind in ("seated-forward-fold", "butterfly", "hip-90-90", "frog"):
		sit = dict(anchor={"root": 1}, ymode={"ground": 1})
		if kind == "seated-forward-fold":
			up = pose(sit, legs(90, 4, 3, frame="world"), body=[-5, 0, 0], **arms(150, 10, 5))
			down = pose(sit, legs(90, 4, 3, frame="world"), body=[45, 0, 0], spine=[25, 0, 0], neck=[15, 0, 0],
				**arms(75, 8, 5, frame="world"))
			return cycle(name, up, down, 4.0, None, .3, 1.0)
		if kind == "butterfly":
			legs_b = {"legL": leg("L", 80, 70, 140, toward=[-.6, 0, -.3], frame="world"),
				"legR": leg("R", 80, 70, 140, toward=[-.6, 0, -.3], frame="world")}
			up = pose(sit, legs_b, body=[5, 0, 0], armL=ik("L", [.06, .1, .45], [1, 0, 0]),
				armR=ik("R", [.06, .1, .45], [1, 0, 0]))
			down = pose(up, body=[35, 0, 0], spine=[15, 0, 0])
			return cycle(name, up, down, 4.0, None, .3, 1.0)
		if kind == "frog":
			legs_f = {"legL": leg("L", 0, 0, 90, toward=[0, 0, -1], frame="world"),
				"legR": leg("R", 0, 0, 90, toward=[0, 0, -1], frame="world")}
			base = pose(dict(anchor={"root": 1}, ymode={"ground": 1}), body=[80, 0, 0], neck=[-15, 0, 0],
				legL=leg("L", 30, 100, 90, toward=[0, 0, -1], frame="world"),
				legR=leg("R", 30, 100, 90, toward=[0, 0, -1], frame="world"), footL=[50, 0], footR=[50, 0],
				**arms(90, 0, 90, toward=[0, -1, 0], frame="world"))
			back = pose(base, rootXZ=[0, -.12], body=[70, 0, 0])
			return cycle(name, base, back, 4.0, None, .3, 1.0)
		a = pose(sit, body=[5, 0, 0], legL=leg("L", 80, 20, 90, toward=[1, 0, 0], frame="world"),
			legR=leg("R", 80, 110, 90, toward=[0, 0, 1], frame="world"), **arms(30, 30, 20))
		b = pose(sit, body=[5, 0, 0], legR=leg("R", 80, 20, 90, toward=[1, 0, 0], frame="world"),
			legL=leg("L", 80, 110, 90, toward=[0, 0, 1], frame="world"), **arms(30, 30, 20))
		mid = pose(sit, legs(80, 30, 90, toward=[0, 1, 0], frame="world"), body=[0, 0, 0], **arms(30, 30, 20))
		return Clip(name, [(0, a), (1.2, mid), (2.4, b), (3.6, mid)], 4.8, {}, middle=1.2)
	if kind == "pigeon":
		base = pose(dict(anchor={"root": 1}, ymode={"ground": 1}), body=[20, 0, 0],
			legL=leg("L", 85, -60, 100, toward=[-1, 0, 0], frame="world"), legR=leg("R", 85, 180, 5, frame="world"),
			footR=[60, 0], armL=ik("L", [.25, .05, .35], [1, 0, -1]), armR=ik("R", [.25, .05, .35], [1, 0, -1]))
		down = pose(base, body=[60, 0, 0], neck=[-10, 0, 0])
		return cycle(name, base, down, 4.0, None, .3, 1.0)
	if kind in ("figure-four", "supine-twist"):
		if kind == "figure-four":
			base = pose(SUPINE, body=[-90, 0, 0], legL=leg("L", 80, -50, 90, toward=[-1, 0, 0]),
				legR=leg("R", 90, 5, 90), armL=ik("L", [.12, .45, .25], [1, 0, 0], "world"),
				armR=ik("R", [.12, .45, .25], [1, 0, 0], "world"))
			base["armL"], base["armR"] = arm("L", 60, 20, 60), arm("R", 60, 20, 60)
			deep = pose(base, legR=leg("R", 110, 5, 90))
			return cycle(name, base, deep, 4.0, None, .3, 1.0)
		base = pose(SUPINE, body=[-90, 0, 0], legL=leg("L", 5, 3, 3), legR=leg("R", 80, 5, 90), **arms(90, 90, 5))
		twist = pose(base, body=[-90, 0, 0], spine=[0, 0, 0], legR=leg("R", 80, -60, 90))
		twist["body"] = [-90, 0, 30]
		return cycle(name, base, twist, 4.0, None, .3, 1.0)
	if kind == "cobra":
		base = pose(dict(anchor={"root": 1}, ymode={"ground": 1}), legs(0, 5, 3), body=[90, 0, 0], footL=[60, 0],
			footR=[60, 0], **arms(40, 20, 90, toward=[0, -1, .3], frame="world"))
		up = pose(base, spine=[-40, 0, 0], neck=[-20, 0, 0], **arms(10, 15, 10, frame="world"))
		return cycle(name, base, up, 3.6, None, .3, .8)
	if kind == "downward-dog":
		hands = {"armL": ik("L", [.26, .05, .5], [.4, 0, -1]), "armR": ik("R", [.26, .05, .5], [.4, 0, -1])}
		feet = {"legL": ik("L", [.12, REST_ANKLE[1], -.6], [0, 0, 1]),
			"legR": ik("R", [.12, REST_ANKLE[1], -.6], [0, 0, 1])}
		base = pose(dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, -.15]), hands, feet, rootY=1.0,
			body=[125, 0, 0], neck=[10, 0, 0], footL=[0, 1], footR=[0, 1])
		pedal = pose(base)
		pedal["legL"] = ik("L", [.12, REST_ANKLE[1] + .1, -.5], [0, 0, 1])
		return cycle(name, base, pedal, 3.0)
	if kind == "scorpion":
		base = pose(dict(anchor={"root": 1}, ymode={"ground": 1}), legs(0, 5, 3), body=[90, 0, 0],
			**arms(90, 90, 5))
		twist = pose(base, body=[90, 0, 0], spine=[0, 0, -35])
		twist["legL"] = leg("L", 60, -140, 70, toward=[0, 1, 0])
		return cycle(name, base, twist, 3.6, None, .3, .8)
	if kind == "open-book":
		side = pose(dict(anchor={"root": 1}, ymode={"ground": 1}), legs(90, 5, 90), body=[90, 0, 0], spin=90,
			**arms(90, 0, 5))
		opened = pose(side, spine=[0, 0, 70], neck=[0, 0, 30], armL=arm("L", 90, 175, 5))
		return cycle(name, side, opened, 4.0, None, .3, .8)
	if kind == "wall-angels":
		low = standing(armL=arm("L", 90, 95, 90, toward=[0, 1, 0]), armR=arm("R", 90, 95, 90, toward=[0, 1, 0]))
		high = standing(**arms(165, 80, 20))
		return cycle(name, low, high, 3.2, {"box": [0, 1.0, -.25, 1.2, 2.0, .06]})
	if kind == "doorway":
		base = stand(.05, .91, -.04, armL=arm("L", 90, 95, 90, toward=[0, 1, 0]),
			armR=arm("R", 90, 95, 90, toward=[0, 1, 0]))
		lean = pose(base, rootXZ=[0, .1], body=[8, 0, 0])
		lean["legL"] = ik("L", [.12, REST_ANKLE[1], .3], [.1, 0, 1])
		base["legL"] = lean["legL"]
		return cycle(name, base, lean, 4.0, {}, .3, 1.0)
	if kind == "shoulder-dislocate":
		front = standing(**arms(10, 30, 5), twistarmL=PRONATED, twistarmR=PRONATED)
		over = standing(**arms(150, 45, 5), twistarmL=PRONATED, twistarmR=PRONATED)
		back = standing(**arms(30, 150, 5), twistarmL=PRONATED, twistarmR=PRONATED)
		return Clip(name, [(0, front), (1.0, over), (2.0, back), (3.0, over)], 4.0, {}, middle=2.0)
	if kind == "arm-circles":
		keys = [(k * .3, standing(**arms(90 + 25 * __import__("math").sin(k * 1.0472), 88 + 25 * __import__("math").cos(
			k * 1.0472), 5))) for k in range(6)]
		return Clip(name, keys, 1.8, {}, middle=.9)
	if kind == "shoulder-rolls":
		import math as _m
		keys = [(k * .4, standing(clavL=[10 * _m.sin(k * 1.0472), 10 * _m.cos(k * 1.0472)],
			clavR=[10 * _m.sin(k * 1.0472), 10 * _m.cos(k * 1.0472)], **HANDS_DOWN)) for k in range(6)]
		return Clip(name, keys, 2.4, {}, middle=1.2)
	if kind == "neck":
		base = standing(**HANDS_DOWN)
		return Clip(name, [(0, pose(base, neck=[0, 0, 50])), (1.5, pose(base, neck=[25, 0, 0])),
			(3.0, pose(base, neck=[0, 0, -50])), (4.5, pose(base, neck=[25, 0, 0]))], 6.0, {}, middle=1.5)
	if kind == "hip-circles":
		import math as _m
		keys = []
		for k in range(6):
			a = k * _m.pi / 3
			p = stand(.15, .9, -.04, body=[-6 * _m.cos(a), 0, 8 * _m.sin(a)], **HANDS_HIPS)
			p["rootXZ"] = [.07 * _m.sin(a), -.04 + .06 * _m.cos(a)]
			keys.append((k * .5, p))
		return Clip(name, keys, 3.0, {}, middle=1.5)
	if kind == "leg-swings":
		base = stand(.05, .91, -.04, armR=arm("R", 70, 90, 10), armL=arm("L", 10, 60, 10))
		base["legR"] = ik("R", [.1, REST_ANKLE[1], .0], [.1, 0, 1])
		fwd = pose(base, legL=leg("L", 70, 0, 8), body=[-5, 0, 0])
		bwd = pose(base, legL=leg("L", 30, 180, 8), body=[8, 0, 0])
		return Clip(name, [(0, fwd), (.7, bwd)], 1.4, {}, middle=.7)
	if kind == "wrist":
		base = standing(**arms(80, 10, 15), twistarmL=PRONATED, twistarmR=PRONATED)
		return Clip(name, [(0, pose(base, wristL=40, wristR=40)), (.6, pose(base, wristL=-40, wristR=-40))], 1.2, {},
			middle=.6)
	if kind in ("standing-shoulders", "dynamic-warmup", "mat"):
		if kind == "mat":
			return quadruped(name, "cat-cow")
		if kind == "dynamic-warmup":
			a = stand(.05, .91, -.04, **arms(160, 30, 10))
			b = stand(.05, .91, -.04, **arms(150, 90, 10))
			b["legL"] = leg("L", 70, 0, 80)
			c = stand(.05, .91, -.04, **arms(160, 30, 10))
			c["legR"] = leg("R", 70, 0, 80)
			return Clip(name, [(0, a), (.8, b), (1.6, a), (2.4, c)], 3.2, {}, middle=.8)
		return Clip(name, [(0, standing(**arms(10, 60, 10))), (1.2, standing(**arms(170, 40, 10))),
			(2.4, standing(**arms(90, 120, 10)))], 3.2, {}, middle=1.2)
	raise ValueError(kind)


def carry(name, kind="farmers"):
	ground = dict(anchor={"feet": 1}, ymode={"ground": 1})
	arms_pose = hanging() if kind == "farmers" else {"armL": arm("L", 3, 90, 5), "armR": arm("R", 0, 0, 5,
		frame="world")}
	props = {"dumbbells": 1} if kind == "farmers" else {"dumbbellR": 1}

	def step(side):
		other = "R" if side == "L" else "L"
		p = pose(ground, arms_pose, body=[3, 0, 0])
		p["leg" + side] = leg(side, 30, 3, 40)
		p["leg" + other] = leg(other, 12, 180, 5)
		return p

	return Clip(name, [(0, step("L")), (.6, step("R"))], 1.2, props, middle=.6)


def side_bend(name):
	base = standing(**HANDS_DOWN)
	base["armL"] = ik("L", [.25, .2, .02], [1, 0, 0], "chest")
	base["armL"] = arm("L", 120, 90, 120, toward=[0, 1, 0])
	return cycle(name, pose(base, spine=[0, -25, 0], armR=arm("R", 0, 0, 5, frame="world")),
		pose(base, spine=[0, 15, 0], armR=arm("R", 0, 0, 5, frame="world")), 2.6, {"dumbbellR": 1})


def woodchop(name, kind="cable"):
	hi = stand(.15, .9, -.04, spine=[0, 0, 40], **arms(140, 40, 10))
	hi["armL"], hi["armR"] = arm("L", 140, 15, 10), arm("R", 140, 70, 10)
	lo = stand(.15, .72, -.12, body=[25, 0, 0], spine=[0, 0, -40], **arms(30, 5, 10))
	lo["armL"], lo["armR"] = arm("L", 35, -30, 10), arm("R", 35, 30, 10)
	return cycle(name, hi, lo, 2.2, {"goblet": 1} if kind == "dumbbell" else {})


def pallof(name):
	base = stand(.12, .85, -.08, body=[5, 0, 0], **CHEST_HANDS)
	out = pose(base, **arms(88, 5, 5))
	return cycle(name, base, out, 2.6, {}, .1, .8)


def clean(name, kind="power-clean"):
	floor = stand(.08, .6, -.25, body=[55, 0, 0], neck=[-25, 0, 0], **hanging())
	pull = stand(.08, .93, -.02, body=[-3, 0, 0], footL=[25, 1], footR=[25, 1], clavL=[12, 0], clavR=[12, 0],
		**hanging())
	catch = stand(.1, .78, -.1, body=[10, 0, 0], **FRONT_RACK)
	stand_rack = stand(.08, .91, -.04, **FRONT_RACK)
	props = {"barbell": 1} if kind == "power-clean" else {"dumbbells": 1}
	keys = [(0, floor), (.6, pull), (.9, catch), (1.5, stand_rack)]
	if kind == "clean-press":
		keys.append((2.1, stand(.08, .91, -.04, **arms(172, 70, 8), twistarmL=PRONATED, twistarmR=PRONATED)))
		keys.append((2.7, stand_rack))
		return Clip(name, keys, 3.6, props, middle=1.5)
	if kind == "snatch":
		one = stand(.08, .6, -.25, body=[55, 0, 0], neck=[-25, 0, 0], armR=arm("R", 0, 0, 5, frame="world"),
			armL=arm("L", 40, 60, 30))
		mid = stand(.08, .93, -.02, armR=arm("R", 90, 60, 120, toward=[0, 1, 0]), armL=arm("L", 20, 60, 30))
		top = stand(.08, .91, -.04, armR=arm("R", 175, 40, 5), armL=arm("L", 15, 60, 30))
		return Clip(name, [(0, one), (.7, mid), (1.1, dict(top, hold=.3)), (2.0, mid)], 2.8, {"dumbbellR": 1},
			middle=1.1)
	return Clip(name, keys, 2.4, props, middle=.9)


def shadow_box(name):
	guard = stand(.1, .86, -.04, body=[8, 0, 0], neck=[-5, 0, 0],
		armL=arm("L", 50, 10, 130, toward=[0, 1, 0]), armR=arm("R", 45, 15, 140, toward=[0, 1, 0]))
	jab = pose(guard, armL=arm("L", 88, 5, 5), spine=[0, 0, -15])
	cross = pose(guard, armR=arm("R", 88, -5, 5), spine=[0, 0, 25])
	return Clip(name, [(0, guard), (.3, jab), (.6, guard), (1.0, cross), (1.4, guard)], 1.8, {}, middle=.3)


def pistol_assisted(name):
	"""Sentadilla a una pierna con las manos en un apoyo delante."""
	support = {"armL": ik("L", [.2, .9, .55], [1, -1, 0]), "armR": ik("R", [.2, .9, .55], [1, -1, 0])}
	top = pose(STAND, support, rootY=.9, rootXZ=[0, -.04], legL=ik("L", [.1, REST_ANKLE[1], .02], [.1, 0, 1]),
		legR=leg("R", 30, 0, 20))
	bottom = pose(STAND, rootY=.45, rootXZ=[0, -.3], body=[35, 0, 0], neck=[-10, 0, 0],
		legL=ik("L", [.1, REST_ANKLE[1], .02], [.1, 0, 1]), legR=leg("R", 80, 0, 10))
	bottom["armL"], bottom["armR"] = ik("L", [.2, .7, .5], [1, -1, 0]), ik("R", [.2, .7, .5], [1, -1, 0])
	return cycle(name, top, bottom, 3.2, {"box": [0, .45, .7, .5, .9, .1]})


def l_sit(name):
	hands = {"armL": ik("L", [.24, .05, -.02], [0, 0, -1]), "armR": ik("R", [.24, .05, -.02], [0, 0, -1])}
	base = pose(dict(anchor={"root": 1}, ymode={"fix": 1}, rootXZ=[0, 0]), hands, legs(90, 4, 3), rootY=.38,
		body=[8, 0, 0], footL=[30, 0], footR=[30, 0])
	return Clip(name, [(0, base), (1.5, pose(base, rootY=.39))], 3.0, {}, middle=0)
