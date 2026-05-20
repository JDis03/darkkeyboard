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
gh release create v1.1.0-models \
  output/suggestions_model.tflite \
  ../app/src/main/assets/bpe_vocab.json \
  ../app/src/main/assets/bpe_merges.txt \
  --title "AI Models v1.1.0" \
  --notes "GPT-2 small TFLite model for suggestions re-ranking"
```

## Arquitectura del modelo

- **Base**: GPT-2 small (124M params)
- **Input**: 5 token IDs (contexto previo)
- **Output**: logits del vocabulario (50257) para next-word prediction
- **Quantization**: INT8 weights + float32 output
- **Tamaño**: ~12MB (vs 500MB original)
- **Latencia**: ~30ms en mid-range Android

## Integración Android

El modelo se usa en `TFLiteReRanker.kt`:
1. `DictSuggestionEngine` genera top-20 candidatos (trie + bigrams)
2. `TFLiteReRanker` scorea cada candidato con GPT-2
3. Score final = 0.4 * trie_score + 0.6 * gpt2_score
4. Retorna top-3 re-ordenados
