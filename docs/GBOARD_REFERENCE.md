# Gboard Reference — Valores extraídos con JADX

**Versión analizada:** 17.3.8.902587967 (arm64-v8a)  
**Método:** `jadx -d /tmp/opencode/gboard/jadx_out base.apk`  
**Fecha:** 2026-06-01

---

## Gaps entre teclas

Gboard NO dibuja gaps manualmente. Usa **layer-list drawables** como fondo de cada tecla, con `padding` como inset. Cada tecla ocupa su celda completa; el cuerpo visible es la celda menos el inset.

| Propiedad | Classic Theme | M2 Full Theme | Atributo |
|-----------|--------------|---------------|---------|
| **Gap horizontal** (cada lado) | `2.5dp` | `4-5dp` | `0x7f040283` / `0x7f0402e0` |
| **Gap horizontal** (total entre teclas) | **5dp** | **8-10dp** | — |
| **Gap vertical** (abajo de cada tecla) | **10dp** | **12dp** | `0x7f0402e5` |
| **Gap vertical** (arriba de cada tecla) | **0dp** | **0dp** | `0x7f0402e8` |

> **Clave:** todo el gap vertical está en el **bottom** de cada tecla, no arriba. Esto hace que las teclas estén más cerca visualmente del borde superior de cada fila.

---

## Corner Radius

| Theme | Corner Radius | Fuente |
|-------|--------------|--------|
| **Classic** | **2dp** | `drawable/0x7f0802df.xml` → `<corners android:radius="2dp"/>` |
| **M2** | Dinámico via proto | `plc.java:106` → `min(min(w,h)/2, h) * scale` |
| **Popup keys Classic** | `4dp` | `styles.xml` línea 6857 |
| **Popup keys M2** | `24dp` | `dimens.xml` línea 1802 |
| **Keyboard body** (floating) | `20dp` | `dimens.xml` línea 344 |

---

## Altura de teclas

| Propiedad | Classic Theme | M2 Full Theme |
|-----------|--------------|---------------|
| **Keyboard body total** | `320dp` | `228-266dp` |
| **Weight sum** | 1000 | 1000 |
| **3 filas letters weight** | 762/1000 | 762/1000 |
| **Bottom row weight** | 238/1000 | 238/1000 |
| **Altura por fila** (calculada) | ~81dp | ~58dp |
| **Altura visual tecla** (fila - gap) | ~71dp | ~46dp |

---

## Elevación y sombra

| Propiedad | Valor | Fuente |
|-----------|-------|--------|
| **Key elevation** | `8dp` | `dimens.xml` línea 682 (`0x7f0703d3`) |
| **Keyboard body corner** (floating) | `20dp` | `dimens.xml` línea 344 (`0x7f0701ce`) |

---

## Arquitectura del sistema de layout

Gboard usa Android `LinearLayout` con `layout_weight`:
- Keyboard body → alto fijo (`320dp` classic)
- 4 filas → `LinearLayout` vertical con weights
- Cada tecla → `SoftKeyView` con fondo drawable layer-list

El **gap visual entre teclas** viene del `padding` del drawable de fondo, NO de coordenadas explícitas.

---

## Comparación con DarkKeyboard

| Propiedad | Gboard Classic | DarkKeyboard | Diferencia |
|-----------|---------------|--------------|------------|
| Gap horizontal (cada lado) | `2.5dp` | `0.25dp` | DK muy pequeño |
| Gap vertical | `10dp` (solo abajo) | `0.5dp` (arriba/abajo) | DK muy pequeño, distribución distinta |
| Corner radius | `2dp` | `4dp` | DK más redondeado |
| Altura visual/tecla | ~71dp | ~calculado | — |

---

## Archivos analizados

- `resources/res/drawable/_0_resource_name_obfuscated_res_0x7f0802da.xml` — key background layer-list
- `resources/res/drawable/_0_resource_name_obfuscated_res_0x7f0802df.xml` — inner key shape (2dp corners)
- `resources/res/values/styles.xml` — theme attributes con valores de gap
- `resources/res/values/dimens.xml` — dimensiones globales
- `sources/defpackage/plc.java` — M2 corner radius dinámico
- `sources/defpackage/tkp.java` — key stroke/line drawing (swipe trail)
