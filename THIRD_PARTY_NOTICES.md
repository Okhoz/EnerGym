# Recursos del visor EnerGym

El archivo original de OpenGym3D, descargado e inspeccionado el 14-10-2026, tiene SHA-256 `67bfd1bf9c9c679dfd81f34202c661f954ce0ec4b15baac56a5781a372d11326`. Se conserva en la investigación del workspace, fuera del APK.

Se ha modificado bajo CC0 para retirar únicamente la animación `Human` de pesos de morph targets inexistentes, que incumple la validación cgltf y no puede aplicarse a esta malla. El APK incluye `app/src/main/assets/visual/push_up_native.glb`, SHA-256 `80338cac3e52dd9b7d7676c83fda2c00158a5254ac91e930710c1497b67bfe3f`. La malla, el rig, la animación `Human.rig`, todos los demás campos JSON y el chunk binario permanecen intactos. El script `tools/normalize_push_up.py` reproduce la adaptación, comprueba ambos hashes y no inventa geometría ni movimiento.

Arte, rig y movimiento: Mesh2Motion y MakeHuman/MPFB core assets, CC0 1.0 Universal. El mapeo de OpenGym3D identifica esta animación concreta como CC0. Se incluyen la declaración upstream `app/src/main/assets/visual/licenses/Mesh2Motion-CC0.md` y el texto completo `CC0-1.0.txt`. Créditos voluntarios: OpenGym3D, Mesh2Motion, MakeHuman/MPFB. No se copió código del visor o pipeline OpenGym3D. El resto del catálogo OpenGym3D tiene licencias distintas y no se redistribuye.

Filament y gltfio Android 1.77.3, The Android Open Source Project / Google, Apache License 2.0. Se utiliza su API Java/JNI; las dos versiones están fijadas. Licencia completa oficial incluida en `app/src/main/assets/visual/licenses/Filament-Apache-2.0.txt`, obtenida del [tag v1.77.3](https://github.com/google/filament/blob/v1.77.3/LICENSE). Los créditos también se muestran dentro del visor. No se ha modificado el código de estas bibliotecas.

Fuentes y límites de validación: [docs/resources.md](docs/resources.md). La licencia libre no equivale a validación profesional de entrenamiento.
