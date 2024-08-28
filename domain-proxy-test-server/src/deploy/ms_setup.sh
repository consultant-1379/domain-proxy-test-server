#!/bin/bash

mkdir -p /var/tmp/emaynes
cd /var/tmp/emaynes
unzip ../dp-pkg.zip
chmod 755 *.sh

#start docker
nohup bash -c "systemctl start docker"

sudo curl -L https://github.com/docker/compose/releases/download/1.5.2/docker-compose-`uname -s`-`uname -m` -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

#Download java and jmx exporter
wget https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus/service/local/repo_groups/public/content/com/oracle/server-jre/1.8.0_212/server-jre-1.8.0_212-x64.tar.gz
wget https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.11.0/jmx_prometheus_javaagent-0.11.0.jar

# docker containers setup
if [[ $(docker ps | egrep 'grafana|prom' | wc -l) -ne 2 ]]; then
  sed -i '/url:/c\    url: http://localhost:9090' grafana/prometheus.yml

  n=0
  until [ $n -ge 10 ]
  do
     docker pull grafana/grafana:6.1.6 && docker pull prom/prometheus:v2.9.2 && docker build -t dp_grafana grafana/ && docker build -t dp_prometheus prometheus/ && break
     n=$[$n+1]
  done
  docker run -d --name prom --net host -p 9090:9090 dp_prometheus
  docker run -d --name grafana --net host -p 3000:3000 -e GF_SECURITY_ADMIN_PASSWORD="password" dp_grafana
fi

# copy NODE certificate
# load the certificate into a variable
FULLCHAIN=$(ssh -t -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i ~/.ssh/vm_private_key cloud-user@mscmce-1-internal "cat /ericsson/mediation/data/certs/tlsnetconf.cert")
#CERTIFICATE="${FULLCHAIN%%-----END CERTIFICATE-----*}-----END CERTIFICATE-----"
CHAIN=$(echo -e "${FULLCHAIN#*-----END CERTIFICATE-----}" | sed '/./,$!d')
echo "$CHAIN" > c_ca.pem

# setup wiremock TLS certs
/var/tmp/emaynes/CaCreationScriptWithEc.sh "netsim.vts.com"
/bin/cp /var/tmp/emaynes/result/java_keystores/dp_jks/dp-truststore.jks /ericsson/tor/data/domainProxy/truststore/
/bin/cp /var/tmp/emaynes/result/java_keystores/dp_jks/dp-keystore.jks /ericsson/tor/data/domainProxy/keystore/
/bin/cp /var/tmp/emaynes/result/root/ca/crl/* /ericsson/tor/data/domainProxy


# copy files to netsim
echo " -----------------------------"
echo " enter netsim password netsim "
echo " -----------------------------"
scp server-jre-1.8.0_212-x64.tar.gz target/dp-testserver-jar-with-dependencies.jar jmx_prometheus_javaagent-0.11.0.jar start_server.sh dp-config.yml log4j.xml c_ca.pem /var/tmp/emaynes/result/java_keystores/sas_jks/* netsim@netsim:~/

echo " -----------------------------"
echo " enter netsim password netsim "
echo " -----------------------------"
ssh -t -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no netsim@netsim "/netsim/start_server.sh"

/bin/cp /var/tmp/emaynes/dp_setup.sh /ericsson/enm/dumps/

echo " -----------------------------"
echo " enter administrator password "
echo " -----------------------------"
ssh -t -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no administrator@scripting-1-internal "/ericsson/enm/dumps/dp_setup.sh"
