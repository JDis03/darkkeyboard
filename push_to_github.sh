#!/bin/bash

echo "=== DarkKeyboard - Push to GitHub ==="
echo ""
echo "Paso 1: Crea el repositorio en GitHub"
echo "  - Ve a: https://github.com/new"
echo "  - Nombre: darkkeyboard"
echo "  - Descripción: DarkKeyboard - Android IME with full PC layout"
echo "  - Visibilidad: Public"
echo "  - NO marques 'Initialize with README'"
echo ""
read -p "¿Ya creaste el repo? (y/n) " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo ""
    echo "Paso 2: Configurando remote y haciendo push..."
    
    # Add remote
    git remote add origin git@github.com:JDis03/darkkeyboard.git 2>/dev/null || \
        git remote set-url origin git@github.com:JDis03/darkkeyboard.git
    
    # Push
    git push -u origin main
    
    echo ""
    echo "✅ Push completado!"
    echo "Repo: https://github.com/JDis03/darkkeyboard"
else
    echo "Cancelandocanceled. Ejecuta este script cuando hayas creado el repo."
fi
