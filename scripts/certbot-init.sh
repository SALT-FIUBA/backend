docker-compose run --rm --entrypoint "\
certbot certonly --webroot -w /var/www/certbot \
    --email matias.sambrizzi@gmail.com \
    -d saltautomation.chickenkiller.com \
    --rsa-key-size 4096 \
    --agree-tos \
    --force-renewal" certbot
