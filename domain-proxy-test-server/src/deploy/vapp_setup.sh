#!/bin/bash

## run: vapp_setup.sh <vapp number>

pushd ../../
mvn install
popd

zip -r dp-pkg.zip grafana prometheus start_server.sh dp_setup.sh dp-config.yml log4j.xml CaCreationScriptWithEc.sh openssl_with_ec.cnf ../../target/dp-testserver-jar-with-dependencies.jar
echo " --------------------------"
echo " enter ms password 12shroot"
echo " --------------------------"
scp -P 2242 dp-pkg.zip ms_setup.sh root@atvts${1}.athtem.eei.ericsson.se:/var/tmp/
rm dp-pkg.zip

echo " --------------------------"
echo " enter ms password 12shroot"
echo " --------------------------"
ssh -t -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -p 2242 root@atvts${1}.athtem.eei.ericsson.se "chmod 755 /var/tmp/ms_setup.sh; /var/tmp/ms_setup.sh"

echo "====================================================="
echo "====================================================="
echo "  all done, login and go to http://localhost:9967/"
echo "         enter ms password 12shroot"
echo "====================================================="
echo "====================================================="
echo "ssh -p 2242 -L9967:192.168.0.42:3000 root@atvts${1}.athtem.eei.ericsson.se"
exec ssh -p 2242 -L9967:192.168.0.42:3000 root@atvts${1}.athtem.eei.ericsson.se