#!/bin/bash
# Este script crea el repo en GitHub usando SSH
# Requiere que el repo sea creado manualmente primero en github.com

echo "Creando repositorio darkkeyboard para usuario JDis03..."
echo ""
echo "Por favor, ve a https://github.com/new y crea un repo con:"
echo "  - Nombre: darkkeyboard"  
echo "  - Public"
echo "  - NO inicializar con README"
echo ""
read -p "Presiona Enter cuando hayas creado el repo..."

# Configurar remote SSH
git remote add origin git@github.com:JDis03/darkkeyboard.git

# Push
git push -u origin main

echo ""
echo "✅ Repo creado y push completado!"
echo "🔗 https://github.com/JDis03/darkkeyboard"
