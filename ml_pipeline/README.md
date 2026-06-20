# DarkKeyboard ML Pipeline

## Requisitos

```bash
pip install -r requirements.txt
```

## Uso

### Opción 1: Modelo pre-entrenado (sin fine-tune)
```bash
python train_model.py
```
Descarga GPT-2 small y lo exporta directamente a TFLite INT8.

### Opción 2: Fine-tune con corpus español
```bash
python train_model.py --corpus /path/to/spanish_corpus.txt --epochs 3
```

### Opción 3: Secuencia más larga (más preciso, más lento)
```bash
python train_model.py --seq-length 128
```

### Opción 4: Sin quantización (modelo más grande, más preciso)
```bash
python train_model.py --no-quantize
```

## Output

```
output/
├── saved_model/              # SavedModel (intermedio)
├── suggestions_model.tflite  # Modelo final (~12MB INT8)
└── model_metadata.json       # Checksum, tamaño, config
```

## Deploy a GitHub Releases

```bash
# Crear release
gh release create v1.2.0-models \
  output/suggestions_model.tflite \
  output/spiece.model \
  --title "AI Models v1.2.0" \
  --notes "T5 encoder (t5_small_multi) TFLite model for contextual re-ranking"
```

## Arquitectura del modelo

**Current (v1.2.0+):**
- **Base**: T5 encoder (t5_small_multi)
- **Input**: encoder_token_ids [1,32] int32 + encoder_padding_mask [1,32] int32
- **Output**: embeddings [1,512] float32 (mean-pooled encoder output)
- **Tokenizer**: SentencePiece (spiece.model, ~800KB)
- **Quantization**: INT8 weights + float32 output
- **Tamaño**: ~34MB
- **Latencia**: ~30-50ms en mid-range Android

**Previous (v1.1.x):**
- **Base**: GPT-2 small (124M params)
- **Output**: logits del vocabulario (50257) para next-word prediction
- **Tamaño**: ~12MB
- **Deprecated**: Replaced by T5 encoder for better multilingual support

## Integración Android

El modelo se usa en `TFLiteReRanker.kt`:
1. `DictSuggestionEngine` genera top-20 candidatos (trie + bigrams)
2. `TFLiteReRanker` genera embeddings para contexto y cada candidato
3. Score = cosine_similarity(context_emb, candidate_emb)
4. Retorna top-3 re-ordenados por similarity
