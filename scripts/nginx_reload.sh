#!/bin/sh
# nginx_reload.sh

# Obtener el ID del contenedor de NGINX
NGINX_CONTAINER=$(docker ps -q -f name=nginx)

# Recargar NGINX
if [ -n "$NGINX_CONTAINER" ]; then
  docker exec "$NGINX_CONTAINER" nginx -s reload
  echo "NGINX reloaded"
else
  echo "NGINX container not found"
fi
