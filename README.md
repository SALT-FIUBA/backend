
TODO
====

+ Setup secure EventStore to have users and ACL 
https://developers.eventstore.com/server/v22.10/installation.html#use-docker-compose



kauth
=====

MVP of auth service with eventsorurcing using eventsoredb an open source event sotre


Optimistic updates


Snapshoting
===========

https://www.eventstore.com/blog/snapshots-in-event-sourcing
https://www.eventstore.com/blog/snapshotting-strategies


Metrics
=======

https://ktor.io/docs/micrometer-metrics.html#prometheus_endpoint
https://medium.com/@math21/how-to-monitor-a-ktor-server-using-grafana-bab54a9ac0dc
https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html
https://github.com/mathias21/KtorEasy/blob/feature/monitoring/KtorEasyGrafanaDashboard.json

Domains FREE
============

https://freedns.afraid.org/
Domain -> saltautomation.chickenkiller.com

SSL Certbot 
===========

https://pentacent.medium.com/nginx-and-lets-encrypt-with-docker-in-less-than-5-minutes-b4b8a60d3a71
https://leangaurav.medium.com/simplest-https-setup-nginx-reverse-proxy-letsencrypt-ssl-certificate-aws-cloud-docker-4b74569b3c61

Docker 
======

Exportar imagen docker a un tar:

> docker save -o ./salt-server.tar salt-server:0.0.1-preview

Port Knocking
=============

TODO

Installations
=============

Docker->https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-20-04
Docker-compose -> https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-compose-on-ubuntu-20-04

Type classes & context receivers
================================

https://blog.rockthejvm.com/kotlin-type-classes/

Mqtt integration
================
[Arquitectura](https://excalidraw.com/#room=3a9874700d8b76e32c61,l_QWfLG-qK3TJQ-p5h42Sg)

References
==========

https://github.com/EventStore/EventStoreDB-Client-Java

https://developers.eventstore.com/server/v23.10/installation.html#use-docker-compose

https://ktor.io/

https://github.com/eugene-khyst/eventstoredb-event-sourcing
https://github.com/eugene-khyst/ksqldb-event-souring
https://github.com/eugene-khyst/postgresql-event-sourcing/tree/main/postgresql-event-sourcing-core/src/main/java/eventsourcing/postgresql

https://cqrs.wordpress.com/documents/building-event-storage/


https://www.zilverline.com/blog/simple-event-sourcing-users-authentication-authorization-part-6
https://discuss.eventstore.com/t/how-to-deal-with-unicity-constraints-over-repository/1900/4

https://www.zilverline.com/blog/simple-event-sourcing-users-authentication-authorization-part-6

Mosquitto Broker  --------------> https://github.com/sukesh-ak/setup-mosquitto-with-docker?tab=readme-ov-file


SalT
====

Stauts (Birth, LastWill) -> ONLINE | OFFLINE

Commando -> https://github.com/nahueespinosa/salt-firmware/blob/master/salt/parser/saltCmd/saltCmd.h#L90


Estado -> https://github.com/nahueespinosa/salt-firmware/blob/master/salt/logic/logic.h#L128

type SaltConfg = {
    velCtOn: Double?
    velCtOff: Double?
    velFeOn: Double?
    velFeHold: Double?
    timeBlinkEnable Boolean?
    timeBlinkDisable: Boolen?
    blinkPeriod Boolean?
}

type SaltCmd = 
    SALT_CMD_ORDER_STOP      |
    SALT_CMD_ORDER_DRIFT     |
    SALT_CMD_ORDER_ISOLATED  |
    SALT_CMD_ORDER_AUTOMATIC |
    SALT_CMD_ORDER_COUNT     |
    SALT_CMD_ORDER_NULL    

type SaltCmd = {
   cmd: SaltCmd?
   config: SaltConfig?
}

type SaltState = {
    config: SaltConfig
    currentCommand: SaltCmd
    speed: {
        source: string
        value: Double
    }
}






























