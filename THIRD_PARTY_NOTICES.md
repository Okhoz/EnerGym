# Recursos del visor EnerGym

## Modelo y animaciones 3D (`app/src/main/assets/visual/energym_motions.glb`)

Un único GLB contiene la malla, el rig y los 257 clips que usan los 287 ejercicios del catálogo. Se genera con `tools/build_exercise_motions.py` a partir de estas entradas, verificadas por SHA-256 y no incluidas en el APK:

| Entrada | Origen | SHA-256 | Licencia |
| --- | --- | --- | --- |
| `push_up_native.glb` | OpenGym3D `push_up.glb` normalizada con `tools/normalize_opengym3d.py` | `80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f` | CC0 1.0 |
| `jumping_jacks_native.glb` | OpenGym3D `jumping_jack.glb` normalizada | `dd6f6e860be59702714edefed5dac8265d391c4d4a2244ac7fea18b52ef2abfa` | CC0 1.0 |
| `jog_native.glb` | OpenGym3D `jog.glb` normalizada | `ad9cb7c7b9828e1f65d00a6d02b3afddf0a0fe52070eca5e335300c1231d5a2c` | CC0 1.0 |
| `walk_native.glb` | OpenGym3D `walk.glb` normalizada | `fb71090b36eb0f03a17b451618e6a28a0bb83e043baf7c1f348acf3b890c92f5` | CC0 1.0 |
| `human-base-animations.glb` | [Mesh2Motion](https://github.com/Mesh2Motion/mesh2motion-app/blob/653a9698f5f315523072f1c3ff3496100401317b/static/animations/human-base-animations.glb), revisión `653a9698f5f315523072f1c3ff3496100401317b` | `406eb0a8dc4ab366e623b79b6e3005a4951392e1bda78ae39c1099d31147733c` | CC0 1.0 |

- Malla y rig: MakeHuman/MPFB core assets (CC0), tal como los exporta OpenGym3D. La malla se toma de `push_up_native.glb`; su chunk BIN se conserva y se le añaden datos de animación y props.
- Capturas CC0 (14 ejercicios): los clips Mesh2Motion «Pushup», «Jumping Jacks», «Jog» y «Walk» de las exportaciones OpenGym3D (copiados sin cambios) y «Sprint», «Swim_Fwd», «Punch_Jab» y «Punch_Cross» de `human-base-animations.glb`, transferidos al rig MakeHuman/MPFB con `tools/retarget_mesh2motion.py` (direcciones de segmentos y rotaciones relativas al reposo). Declaración de Mesh2Motion: arte, rigs y animaciones bajo CC0.
- Animaciones esquemáticas (273 ejercicios, 250 clips): generadas por EnerGym con `tools/exercise_patterns.py`, `tools/exercise_motions.py` y `tools/motion_rig.py` a partir de poses clave por patrón de movimiento. Son obra propia sobre el modelo CC0 y se distribuyen bajo CC0 1.0. Props (barra, mancuernas, apoyo, barra fija) generados por el mismo script.
- No se redistribuye ningún recurso Mixamo, CMU ni el resto del catálogo OpenGym3D con licencias distintas. No se usa el paquete `human-mocap-animations.glb` de Mesh2Motion.
- Copias de licencia en el APK: `app/src/main/assets/visual/licenses/Mesh2Motion-CC0.md` y `CC0-1.0.txt`. Créditos voluntarios: OpenGym3D, Mesh2Motion, MakeHuman/MPFB.

## Filament

Filament y gltfio Android 1.77.3, The Android Open Source Project / Google, Apache License 2.0. Se utiliza su API Java/JNI; las dos versiones están fijadas. Licencia completa oficial incluida en `app/src/main/assets/visual/licenses/Filament-Apache-2.0.txt`, obtenida del [tag v1.77.3](https://github.com/google/filament/blob/v1.77.3/LICENSE). Los créditos también se muestran dentro del visor. No se ha modificado el código de estas bibliotecas.

Fuentes y límites de validación: [docs/resources.md](docs/resources.md). La licencia libre no equivale a validación profesional de entrenamiento.
