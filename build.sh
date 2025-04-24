#!/bin/bash

# Verificamos que se haya pasado una versión como argumento
if [ -z "$1" ]; then
  echo "Uso: $0 <version>"
  exit 1
fi

version=$1

# Build de la imagen Docker
echo "🔨 Construyendo imagen Docker: $version"
docker build -t "kiot-backend:$version" .

# Guardando imagen como .tar
echo "📦 Guardando imagen en kiot.tar"
docker save -o ./kiot.tar "kiot-backend:$version"

# Enviando el archivo al servidor
echo "🚀 Enviando imagen al servidor"
scp -P 2222 kiot.tar root@kiot.chickenkiller.com:/root/kiot

echo "✅ Listo"
