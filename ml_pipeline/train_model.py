#!/usr/bin/env python3
"""
DarkKeyboard — T5 Encoder → TFLite
Exporta el encoder de T5 para re-ranking de sugerencias en Android.

Uso:
    python train_model.py                   # t5_small_multi (default)
    python train_model.py --preset t5_base_multi
"""

import argparse
import hashlib
import json
import os
from pathlib import Path

import numpy as np
import tensorflow as tf
import keras_nlp

# ── Suprimir logs verbosos de TF ──────────────────────────────────────────────
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"

OUTPUT_DIR = Path("output")
SEQ_LEN    = 32

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preset", default="t5_small_multi",
                        choices=["t5_small_multi", "t5_base_multi",
                                 "flan_small_multi", "flan_base_multi"])
    parser.add_argument("--seq-len", type=int, default=SEQ_LEN)
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(exist_ok=True)

    print(f"TF {tf.__version__} | keras-nlp {keras_nlp.__version__}")
    print(f"Preset : {args.preset}")
    print(f"SeqLen : {args.seq_len}")
    print("=" * 50)

    # ── 1. Cargar backbone ────────────────────────────────────────────────────
    print("📥 Downloading T5 backbone...")
    backbone = keras_nlp.models.T5Backbone.from_preset(args.preset)
    print(f"✅ Loaded ({backbone.count_params():,} params)")

    # ── 2. Construir encoder-only model ──────────────────────────────────────
    print("🔧 Building encoder model...")

    # Construir con subclassing para evitar problemas de la API funcional
    class T5EncoderModel(tf.keras.Model):
        def __init__(self, backbone):
            super().__init__()
            self.backbone = backbone

        def call(self, inputs, training=None, mask=None):
            # inputs: dict con encoder_token_ids, encoder_padding_mask
            # Decoder con ceros — solo necesitamos el encoder
            batch = tf.shape(inputs["encoder_token_ids"])[0]
            dec_ids  = tf.zeros((batch, 1), dtype=tf.int32)
            dec_mask = tf.zeros((batch, 1), dtype=tf.int32)

            out = self.backbone(inputs={
                "encoder_token_ids"   : inputs["encoder_token_ids"],
                "encoder_padding_mask": inputs["encoder_padding_mask"],
                "decoder_token_ids"   : dec_ids,
                "decoder_padding_mask": dec_mask,
            }, training=training)

            # Mean pooling enmascarado del encoder
            enc  = out["encoder_sequence_output"]           # (B, S, 512)
            msk  = tf.cast(
                inputs["encoder_padding_mask"][:, :, tf.newaxis],
                dtype=enc.dtype
            )                                               # (B, S, 1)
            pooled = tf.reduce_sum(enc * msk, axis=1) / (
                tf.reduce_sum(msk, axis=1) + 1e-8
            )                                               # (B, 512)
            return pooled

    encoder_model = T5EncoderModel(backbone)

    # Warm-up
    dummy_ids  = tf.ones((1, args.seq_len), dtype=tf.int32)
    dummy_mask = tf.ones((1, args.seq_len), dtype=tf.int32)
    out = encoder_model({
        "encoder_token_ids"   : dummy_ids,
        "encoder_padding_mask": dummy_mask,
    })
    print(f"✅ Encoder output shape: {out.shape}")   # (1, 512)

    # ── 3. Construir modelo Keras funcional con shape fijo ───────────────────
    print("🔧 Building fixed-shape Keras model for TFLite...")

    enc_ids_in  = tf.keras.Input(shape=(args.seq_len,), dtype=tf.int32, name="encoder_token_ids")
    enc_mask_in = tf.keras.Input(shape=(args.seq_len,), dtype=tf.int32, name="encoder_padding_mask")

    # Llamar encoder_model directamente (ya construido)
    pooled_out = tf.keras.layers.Lambda(
        lambda x: encoder_model({"encoder_token_ids": x[0], "encoder_padding_mask": x[1]})
    )([enc_ids_in, enc_mask_in])

    fixed_model = tf.keras.Model(
        inputs=[enc_ids_in, enc_mask_in],
        outputs=pooled_out,
        name="t5_encoder_fixed"
    )

    # Test
    out2 = fixed_model([dummy_ids, dummy_mask])
    print(f"✅ Fixed model output: {out2.shape}")

    # ── 4. Convertir a TFLite INT8 ────────────────────────────────────────────
    print("⚙️  Converting to TFLite INT8...")
    converter = tf.lite.TFLiteConverter.from_keras_model(fixed_model)
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS,
    ]
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter._experimental_lower_tensor_list_ops = False

    def representative_dataset():
        for _ in range(20):
            ids  = np.random.randint(0, 32000, (1, args.seq_len)).astype(np.int32)
            mask = np.ones((1, args.seq_len), dtype=np.int32)
            yield [ids, mask]

    converter.representative_dataset  = representative_dataset

    tflite = converter.convert()

    tflite_path = OUTPUT_DIR / "suggestions_model.tflite"
    tflite_path.write_bytes(tflite)

    size_mb  = len(tflite) / (1024 * 1024)
    checksum = hashlib.sha256(tflite).hexdigest()
    print(f"✅ TFLite: {tflite_path} ({size_mb:.1f} MB)")
    print(f"   Checksum: {checksum[:20]}...")

    # ── 5. Metadata ───────────────────────────────────────────────────────────
    meta = {
        "preset"          : args.preset,
        "type"            : "t5-encoder-meanpool",
        "seq_length"      : args.seq_len,
        "hidden_size"     : 512,
        "size_mb"         : round(size_mb, 1),
        "checksum_sha256" : checksum,
        "quantization"    : "INT8",
        "ops"             : "TFLITE_BUILTINS",
        "tf_version"      : tf.__version__,
    }
    (OUTPUT_DIR / "model_metadata.json").write_text(
        json.dumps(meta, indent=2)
    )

    # ── 6. Actualizar checksum en Android ─────────────────────────────────────
    md_path = Path("../app/src/main/java/org/dark/keyboard/suggestions/ModelDownloader.kt")
    if md_path.exists():
        text    = md_path.read_text()
        import re
        updated = re.sub(
            r'EXPECTED_CHECKSUM = "[a-f0-9]{64}"',
            f'EXPECTED_CHECKSUM = "{checksum}"',
            text
        )
        md_path.write_text(updated)
        print("   ✅ ModelDownloader.kt checksum updated")

    print("\n" + "=" * 50)
    print("✅ Done!")
    print(f"   {tflite_path}  ({meta['size_mb']} MB)")
    print(f"   Checksum: {checksum[:20]}...")

if __name__ == "__main__":
    main()
