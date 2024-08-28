#!/bin/bash

echo "stoping netsim"
/netsim/inst/stop_netsim

echo "extracting java"
tar -zxvf server-jre-1.8.0_212-x64.tar.gz

#If netsim doesn't have a range available to use, the steps below enable the use a list of IP addresses
#Step 1: Enable the ipAddrPath variable in the dp-config.yml
#Step 2: Generate a list of IP addresses (change to inet6 for ipv6 addresses)
#ip addr show | grep 'inet ' | sort | awk '{ print $2 }' | sed 's/.\{3\}$//' > ipaddr_ip4.txt
#Step 3: Remove the first two lines of the file
#sed -i '1,2d' ipaddr_ip4.txt

echo "stopping test server"
ps -ef|grep dp-testserver|grep -v 'grep'|awk '{print $2}'|xargs kill &> /dev/null

echo "updating dp-config"
KEY_FILE=$(find /netsim/netsimdir/ -name s_key.pem|grep '1.8K-DG2-TDD'|head -1)
CERT_FILE=$(echo "$KEY_FILE"|sed "s/s_key/s_cert/")
sed -i "s#keyPath: .*#keyPath: \"$KEY_FILE\"#" dp-config.yml
sed -i "s#certificatePath: .*netsimdir.*#certificatePath: \"$CERT_FILE\"#" dp-config.yml

echo "starting test server"
#nohup bash -c "/netsim/jdk1.8.0_73/bin/java -Xms512m -Xmx1024m -jar dp-testserver-jar-with-dependencies.jar -c dp-config.yml > /dev/null 2>&1 &"
nohup bash -c "/netsim/jdk1.8.0_212/bin/java -Xms2048m -Xmx5120m -XX:+UseG1GC -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -Xss512k -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -XX:+DoEscapeAnalysis -XX:+UseStringDeduplication -XX:+UseCompressedOops -Dlog4j.configuration=file:/netsim/log4j.xml -jar dp-testserver-jar-with-dependencies.jar -c dp-config.yml &"