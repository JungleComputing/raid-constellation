#!/bin/bash

# Executes EdgeInference with Constellation using configurations from 
# bin/distributed/config
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

check_env CONSTELLATION_PORT

# Change the path to fit your system
tmpdir=${EDGEINFERENCE_DIR}/.java-io-tmpdir

mkdir -p ${tmpdir}
rm -rf ${tmpdir}/*.so

timestamp=`date +%s`

# Get addresses of all compute nodes
source ${EDGEINFERENCE_DIR}/bin/distributed/config

nrComputeNodes=${#computeAddresses[*]}
nrNodes=$((${nrComputeNodes} + 2))

if [[ -z ${sourceAddress} ]]; then
  echo "Add a source address"
  exit 1
fi

if [[ -z ${targetAddress} ]]; then
  echo "Add a target Address"
  exit 1
fi

if [[ ${nrComputeNodes} -eq 0 ]]; then
  echo "Add at least one computeNode in config.bash"
  exit 1
fi

className="nl.zakarias.constellation.edgeinference.EdgeInference"
poolName="constellation.pool.$timestamp"
args="$@"

# Client timeout duration in seconds (use -1 for keep open)
clientTimeout=30

# --- Start Server ---
x-terminal-emulator -e "${EDGEINFERENCE_DIR}/bin/distributed/constellation-server"

# --- Start source ---
x-terminal-emulator -e ssh ${sourceAddress} "\${EDGEINFERENCE_DIR}/bin/distributed/start_source.bash ${CONSTELLATION_PORT} ${serverAddress} ${nrNodes} ${className} ${poolName} ${clientTimeout} ${args} 2>&1 | tee \${EDGEINFERENCE_DIR}/edge_inference_source.log"
# tcsh shell with no remote env set
#localDir=/home/zaklaw01/Projects/odroid-constellation/edgeinference-constellation/build/install/edgeinference-constellation
#x-terminal-emulator -e ssh self "setenv EDGEINFERENCE_DIR ${localDir}; \${EDGEINFERENCE_DIR}/bin/distributed/start_source.bash ${CONSTELLATION_PORT} ${serverAddress} ${nrNodes} ${className} ${poolName} ${clientTimeout} ${args} |& tee \${EDGEINFERENCE_DIR}/edge_inference_source.log"

# --- Start target ---
x-terminal-emulator -e ssh ${targetAddress} "\${EDGEINFERENCE_DIR}/bin/distributed/start_target.bash ${CONSTELLATION_PORT} ${serverAddress} ${nrNodes} ${className} ${poolName} ${clientTimeout} ${args} 2>&1 | tee \${EDGEINFERENCE_DIR}/edge_inference_target.log"
# tcsh shell with no remote env set
#localDir=/home/zaklaw01/Projects/odroid-constellation/edgeinference-constellation/build/install/edgeinference-constellation
#x-terminal-emulator -e ssh ${targetAddress} "setenv EDGEINFERENCE_DIR ${localDir}; \${EDGEINFERENCE_DIR}/bin/distributed/start_target.bash ${CONSTELLATION_PORT} ${serverAddress} ${nrNodes} ${className} ${poolName} ${clientTimeout} ${args} |& tee \${EDGEINFERENCE_DIR}/edge_inference_target.log"

for ip in "${computeAddresses[@]}"
do
  x-terminal-emulator -e ssh ${ip} "\${EDGEINFERENCE_DIR}/bin/distributed/start_predictor.bash ${CONSTELLATION_PORT} ${serverAddress} ${nrNodes} ${className} ${poolName} ${clientTimeout} ${args} 2>&1 | tee \${EDGEINFERENCE_DIR}/edge_inference_compute.log"
done

