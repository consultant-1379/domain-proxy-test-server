#!/bin/bash

#=============
#Copyright 2016 SAS Project Authors. All Rights Reserved.
#
#Licensed under the Apache License, Version 2.0 (the "License");
#you may not use this file except in compliance with the License.
#You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#Unless required by applicable law or agreed to in writing, software
#distributed under the License is distributed on an "AS IS" BASIS,
#WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#See the License for the specific language governing permissions and
#limitations under the License.
#=============

SAS_IP=$1
if [ -z "$SAS_IP" ]; then
    echo "Please pass common name of sas server as first parameter to script"
    exit 1
fi



_KEYTOOL=keytool
_MKDIR=/bin/mkdir
_MV=/bin/mv

echo Usage: ./CACreationScript.sh

DIR=./root
PAPA=$DIR/ca
CERTI=$PAPA/certs
CRL=$PAPA/crl
CSR=$PAPA/newcerts
KEY=$PAPA/private
INDEX=./index.txt

$_MKDIR -p $DIR
$_MKDIR -p $PAPA
$_MKDIR -p $CERTI
$_MKDIR -p $CRL
$_MKDIR -p $CSR
$_MKDIR -p $KEY
touch $INDEX
echo 1000 > $DIR/crlnumber

CONFIG_FILE=openssl_with_ec.cnf
ROOT=root_ca
UNKNOWN=unknown
SIG=_sign
EC=ecc
ROOT_VALIDITY=9300
INT_VALIDITY=7475
EE_VALIDITY=5185
EX_VALIDITY=1
ROOT_RSA_KEY_SIZE=4096
INT_RSA_KEY_SIZE=4096
EE_RSA_KEY_SIZE=2048

#ROOT

#RSA

openssl req -new -x509 -newkey rsa:$ROOT_RSA_KEY_SIZE -sha384 -nodes -days $ROOT_VALIDITY -extensions $ROOT -out $ROOT.crt -keyout $ROOT.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Wireless Innovation Forum/OU=www.wirelessinnovation.org/CN=Root CA-1" -config $CONFIG_FILE

#Unknown ROOT

openssl req -new -x509 -newkey rsa:$ROOT_RSA_KEY_SIZE -sha384 -nodes -days $ROOT_VALIDITY -extensions $ROOT -out $UNKNOWN$ROOT.crt -keyout $UNKNOWN$ROOT.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Wireless Innovation Forum/OU=www.wirelessinnovation.org/CN=Unknown Root CA-1" -config $CONFIG_FILE

#SAS Intermediate
SAS=sas_ca
SAS_SIGN=$SAS$SIG

openssl req -new -newkey rsa:$INT_RSA_KEY_SIZE -nodes -reqexts $SAS -out $SAS.csr -keyout $SAS.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Wireless Innovation Forum/OU=www.wirelessinnovation.org/CN=WInnForum RSA SAS CA-1" -config $CONFIG_FILE

openssl ca -create_serial -cert $ROOT.crt -keyfile $ROOT.pkey -outdir ./root/ca/newcerts -batch -out $SAS.crt -utf8 -days $INT_VALIDITY -policy policy_anything -md sha384 -extensions $SAS_SIGN -config $CONFIG_FILE -in $SAS.csr

#SAS VALID EE
SAS_EE=sas_req
SAS_EE_SIGN=$SAS_EE$SIG

openssl req -new -newkey rsa:$EE_RSA_KEY_SIZE -nodes -reqexts $SAS_EE -out $SAS_EE.csr -keyout $SAS_EE.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Valid Cert/OU=www.validcert.org/CN=$SAS_IP" -config $CONFIG_FILE

openssl ca -create_serial -cert $SAS.crt -keyfile $SAS.pkey -outdir $CSR -batch -out $SAS_EE.crt -utf8 -days $EE_VALIDITY -policy policy_anything -md sha384 -extensions $SAS_EE_SIGN -config $CONFIG_FILE -in $SAS_EE.csr

openssl ca -config $CONFIG_FILE -gencrl -out $CRL/ca.crl -cert $ROOT.crt -keyfile $ROOT.pkey

#SAS EXPIRED EE

echo "herehere"

SAS_EX_EE=sas_expired_req

openssl req -new -newkey rsa:$EE_RSA_KEY_SIZE -nodes -reqexts $SAS_EE -out $SAS_EX_EE.csr -keyout $SAS_EX_EE.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Expired Cert/OU=www.expiredcert.org/CN=$SAS_IP" -config $CONFIG_FILE

openssl ca -create_serial -cert $SAS.crt -keyfile $SAS.pkey -outdir $CSR -batch -out $SAS_EX_EE.crt -utf8 -days $EX_VALIDITY -policy policy_anything -md sha384 -extensions $SAS_EE_SIGN -config $CONFIG_FILE -in $SAS_EX_EE.csr

#SAS REVOKED EE
SAS_RV_EE=sas_revoked_req

openssl req -new -newkey rsa:$EE_RSA_KEY_SIZE -nodes -reqexts $SAS_EE -out $SAS_RV_EE.csr -keyout $SAS_RV_EE.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Revoked Cert/OU=www.revokedcert.org/CN=$SAS_IP" -config $CONFIG_FILE

openssl ca -create_serial -cert $SAS.crt -keyfile $SAS.pkey -outdir $CSR -batch -out $SAS_RV_EE.crt -utf8 -days $EE_VALIDITY -policy policy_anything -md sha384 -extensions $SAS_EE_SIGN -config $CONFIG_FILE -in $SAS_RV_EE.csr

openssl ca -config $CONFIG_FILE -revoke $SAS_RV_EE.crt -cert $SAS.crt -keyfile $SAS.pkey

openssl ca -config $CONFIG_FILE -gencrl -out $CRL/$SAS.crl -cert $SAS.crt -keyfile $SAS.pkey

#SAS CORRUPTED EE
SAS_CR_EE=sas_corrupted_req

openssl req -new -newkey rsa:$EE_RSA_KEY_SIZE -nodes -reqexts $SAS_EE -out $SAS_CR_EE.csr -keyout $SAS_CR_EE.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Wireless Innovation Forum/OU=www.wirelessinnovation.org/CN=corrupted$SAS_IP" -config $CONFIG_FILE

openssl ca -create_serial -cert $SAS.crt -keyfile $SAS.pkey -outdir $CSR -batch -out $SAS_CR_EE.crt -utf8 -days $EE_VALIDITY -policy policy_anything -md sha384 -extensions $SAS_EE_SIGN -config $CONFIG_FILE -in $SAS_CR_EE.csr

#SAS UNKNOWN CA EE
SAS_UN_EE=sas_unknown_req

openssl req -new -newkey rsa:$EE_RSA_KEY_SIZE -nodes -reqexts $SAS_EE -out $SAS_UN_EE.csr -keyout $SAS_UN_EE.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Unknown CA/OU=www.unknownca.org/CN=$SAS_IP" -config $CONFIG_FILE

openssl ca -create_serial -cert $UNKNOWN$ROOT.crt -keyfile $UNKNOWN$ROOT.pkey -outdir $CSR -batch -out $SAS_UN_EE.crt -utf8 -days $EE_VALIDITY -policy policy_anything -md sha384 -extensions $SAS_EE_SIGN -config $CONFIG_FILE -in $SAS_UN_EE.csr

#Domain Proxy Intermediate
DP=dp_ca
DP_SIGN=$DP$SIG

openssl req -new -newkey rsa:$INT_RSA_KEY_SIZE -nodes -reqexts $DP -out $DP.csr -keyout $DP.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Wireless Innovation Forum/OU=www.wirelessinnovation.org/CN=Domain Proxy CA-1" -config $CONFIG_FILE

openssl ca -create_serial -cert $ROOT.crt -keyfile $ROOT.pkey -outdir $CSR -batch -out $DP.crt -utf8 -days $INT_VALIDITY -policy policy_anything -md sha384 -extensions $DP_SIGN -config $CONFIG_FILE -in $DP.csr

#Domain Proxy EE
DP_EE=dp_req
DP_EE_SIGN=$DP_EE$SIG

openssl req -new -newkey rsa:$EE_RSA_KEY_SIZE -nodes -reqexts $DP_EE -out $DP_EE.csr -keyout $DP_EE.pkey -subj "/C=US/ST=District of Columbia/L=Washington/O=Wireless Innovation Forum/OU=www.wirelessinnovation.org/CN=Domain Proxy End-Entity Example" -config $CONFIG_FILE

openssl ca -create_serial -cert $DP.crt -keyfile $DP.pkey -outdir $CSR -batch -out $DP_EE.crt -utf8 -days $EE_VALIDITY -policy policy_anything -md sha384 -extensions $DP_EE_SIGN -config $CONFIG_FILE -in $DP_EE.csr

#SAS VALID KEYSTORE
SAS_VALID_JKS=sas-valid-server-certificate.jks

openssl pkcs12 -export -inkey $SAS_EE.pkey -in $SAS_EE.crt -certfile $SAS.crt -password pass:3ric550N -out $SAS_EE.pfx

$_KEYTOOL -importkeystore -srckeystore $SAS_EE.pfx -srcstoretype pkcs12 -destkeystore $SAS_VALID_JKS -deststoretype jks -deststorepass 3ric550N -srcstorepass 3ric550N

#SAS EXPIRED KEYSTORE
SAS_EXPIRED_JKS=sas-expired-server-certificate.jks

openssl pkcs12 -export -inkey $SAS_EX_EE.pkey -in $SAS_EX_EE.crt -certfile $SAS.crt -password pass:3ric550N -out $SAS_EX_EE.pfx

$_KEYTOOL -importkeystore -srckeystore $SAS_EX_EE.pfx -srcstoretype pkcs12 -destkeystore $SAS_EXPIRED_JKS -deststoretype jks -deststorepass 3ric550N -srcstorepass 3ric550N

#SAS REVOKED KEYSTORE
SAS_REVOKED_JKS=sas-revoked-server-certificate.jks

openssl pkcs12 -export -inkey $SAS_RV_EE.pkey -in $SAS_RV_EE.crt -certfile $SAS.crt -password pass:3ric550N -out $SAS_RV_EE.pfx

$_KEYTOOL -importkeystore -srckeystore $SAS_RV_EE.pfx -srcstoretype pkcs12 -destkeystore $SAS_REVOKED_JKS -deststoretype jks -deststorepass 3ric550N -srcstorepass 3ric550N

#SAS CORRUPTED KEYSTORE
SAS_CORRUPTED_JKS=sas-corrupted-server-certificate.jks

openssl pkcs12 -export -inkey $SAS_CR_EE.pkey -in $SAS_CR_EE.crt -certfile $SAS.crt -password pass:3ric550N -out $SAS_CR_EE.pfx

$_KEYTOOL -importkeystore -srckeystore $SAS_CR_EE.pfx -srcstoretype pkcs12 -destkeystore $SAS_CORRUPTED_JKS -deststoretype jks -deststorepass 3ric550N -srcstorepass 3ric550N

#SAS UNKNOWN CA KEYSTORE
SAS_UNKNOWN_JKS=sas-unknown-ca-server-certificate.jks

openssl pkcs12 -export -inkey $SAS_UN_EE.pkey -in $SAS_UN_EE.crt -certfile $SAS.crt -password pass:3ric550N -out $SAS_UN_EE.pfx

$_KEYTOOL -importkeystore -srckeystore $SAS_UN_EE.pfx -srcstoretype pkcs12 -destkeystore $SAS_UNKNOWN_JKS -deststoretype jks -deststorepass 3ric550N -srcstorepass 3ric550N

#SAS TRUSTORE
SAS_TRUSTSTORE=sas-truststore.jks

$_KEYTOOL -noprompt -import -trustcacerts -alias rootca -file $ROOT.crt -storepass 3ric550N -keystore $SAS_TRUSTSTORE

#DOMAIN PROXY KEYSTORE
DP_KEYSTORE=dp-keystore.jks

openssl pkcs12 -export -inkey $DP_EE.pkey -in $DP_EE.crt -certfile $DP.crt -password pass:3ric550N -out $DP_EE.pfx

$_KEYTOOL -importkeystore -srckeystore $DP_EE.pfx -srcstoretype pkcs12 -destkeystore $DP_KEYSTORE -deststoretype jks -deststorepass 3ric550N -srcstorepass 3ric550N

#DOMAIN PROXY TRUSTSTORE
DP_TRUSTSTORE=dp-truststore.jks

$_KEYTOOL -noprompt -import -trustcacerts -alias rootca -file $ROOT.crt -storepass 3ric550N -keystore $DP_TRUSTSTORE


#########################
#			#
#	CLEAN-UP	#
#			#
#########################

ROOT_CERTS=root_certs
UNKNOWN_ROOT=$ROOT_CERTS/unknown_root
INTERMEDIATE_CERTS=intermediate_certs
SAS_CAS=$INTERMEDIATE_CERTS/sas_cas
DP_CAS=$INTERMEDIATE_CERTS/dp_cas
END_ENTITY_CERTS=end_entity_certs
SAS_EE_=$END_ENTITY_CERTS/sas_ee
DP_EE_=$END_ENTITY_CERTS/dp_ee
KEYSTORES=java_keystores
DP_JKS=$KEYSTORES/dp_jks
SAS_JKS=$KEYSTORES/sas_jks
CERT_CONTAINERS=pkcs_containers
DP_CONT=$CERT_CONTAINERS/dp
SAS_CONT=$CERT_CONTAINERS/sas

$_MKDIR -p $ROOT_CERTS
$_MKDIR -p $UNKNOWN_ROOT
$_MKDIR -p $INTERMEDIATE_CERTS
$_MKDIR -p $SAS_CAS
$_MKDIR -p $DP_CAS
$_MKDIR -p $END_ENTITY_CERTS
$_MKDIR -p $SAS_EE_
$_MKDIR -p $DP_EE_
$_MKDIR -p $KEYSTORES
$_MKDIR -p $DP_JKS
$_MKDIR -p $SAS_JKS
$_MKDIR -p $CERT_CONTAINERS
$_MKDIR -p $DP_CONT
$_MKDIR -p $SAS_CONT

$_MV $ROOT.* $ROOT_CERTS
$_MV $UNKNOWN$ROOT.* $UNKNOWN_ROOT
$_MV $SAS.* $SAS_CAS
$_MV $DP.* $DP_CAS
$_MV $DP_EE.pfx $DP_CONT
$_MV *.pfx $SAS_CONT
$_MV $SAS_EE.* $SAS_RV_EE.* $SAS_CR_EE.* $SAS_UN_EE.* $SAS_EX_EE.* $SAS_EE_
$_MV $DP_EE.* $DP_EE_
$_MV $SAS_VALID_JKS $SAS_EXPIRED_JKS $SAS_REVOKED_JKS $SAS_CORRUPTED_JKS $SAS_UNKNOWN_JKS $SAS_TRUSTSTORE $SAS_JKS
$_MV $DP_KEYSTORE $DP_TRUSTSTORE $DP_JKS
$_MV index.* $DIR

RESULT=result

$_MKDIR $RESULT
$_MV $ROOT_CERTS $INTERMEDIATE_CERTS $END_ENTITY_CERTS $KEYSTORES $CERT_CONTAINERS $DIR $RESULT
