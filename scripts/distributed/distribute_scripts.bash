#!/bin/bash

# Executes EdgeInference with Constellation using configurations from config.bash
function check_env() {
    local name_env_dir=$1
    if [[ -z ${!name_env_dir} ]]
    then
	echo "Environment variable $name_env_dir has not been set"
	exit 1
    fi
}

function check_env_dir() {
    local name_env_dir=$1

    check_env ${name_env_dir}

    if [[ ! -d ${!name_env_dir} ]]
    then
	echo "Environment variable $name_env_dir does not represent a directory"
	exit 1
    fi
}

check_env_dir EDGEINFERENCE_DIR
BIN_DIR=${EDGEINFERENCE_DIR}/bin

source ${BIN_DIR}/distributed/config

for ip in "${computeAddresses[@]}"
do
  echo "------ $ip ------"
  scp -r ${BIN_DIR}/distributed ${ip}:"\${EDGEINFERENCE_DIR}/bin"
  scp -r ${EDGEINFERENCE_DIR}/lib/edgeinference-constellation.jar ${ip}:"\${EDGEINFERENCE_DIR}/lib/"
  echo ""
done

echo "------ ${sourceAddress} ------"
scp -r ${BIN_DIR}/distributed ${sourceAddress}:"\${EDGEINFERENCE_DIR}/bin"
scp -r ${EDGEINFERENCE_DIR}/lib/edgeinference-constellation.jar ${sourceAddress}:"\${EDGEINFERENCE_DIR}/lib/"
echo ""

echo "------ ${targetAddress} ------"
scp -r ${BIN_DIR}/distributed ${targetAddress}:"\${EDGEINFERENCE_DIR}/bin"
scp -r ${EDGEINFERENCE_DIR}/lib/edgeinference-constellation.jar ${targetAddress}:"\${EDGEINFERENCE_DIR}/lib/"
echo ""
