openssl req -x509 -nodes -newkey rsa:4096 -days 1\
    -keyout 'privkey.pem' \
    -out 'fullchain.pem' \
    -subj '/CN=localhost'
