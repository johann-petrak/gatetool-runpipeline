#!/bin/bash

if [ "x${GATE_HOME}" == "x" ]
then
  echo Environment variable GATE_HOME not set
  exit 1
fi
if [ "x${SCALA_HOME}" == "x" ]
then
  echo Environment variable SCALA_HOME not set
  echo Scala must be installed and the environment variable SCALA_HOME set to the installation directory
  exit 1
fi
havelogs=1
if [[ ! -d logs ]]; then
  echo "The current directory does not contain a logs subdirectory. no logs saved" 
  havelogs=0
fi

PRG="$0"
CURDIR="`pwd`"
# need this for relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`"/$link"
  fi
done
SCRIPTDIR=`dirname "$PRG"`
SCRIPTDIR=`cd "$SCRIPTDIR"; pwd -P`
ROOTDIR=`cd "$SCRIPTDIR/.."; pwd -P`

## for now use environment variable RUNPIPELINE_LOG_PREFIX 
## to store the log and benchmark files with some other prefix than "run-"
prefix="${RUNPIPELINE_LOG_PREFIX:-run}"
timestamp=`date +%Y%m%d%H%M%S`
if [[ $havelogs == 1 ]]
then
  benchfile=./logs/${prefix}-${timestamp}-benchmark.txt
  echo log file is ./logs/${prefix}-${timestamp}-log.txt
  echo benchmark file is  $benchfile 
  /usr/bin/time -o ./logs/${prefix}-${timestamp}-time.txt ${SCALA_HOME}/bin/scala -cp ${ROOTDIR}/lib/'*':${ROOTDIR}/gatetool-runpipeline.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/'*' RunPipeline -b $benchfile "$@" |& tee -a ./logs/${prefix}-${timestamp}-log.txt
  echo log file is ./logs/${prefix}-${timestamp}-log.txt
  echo benchmark file is  $benchfile 
else 
  ${SCALA_HOME}/bin/scala -cp ${ROOTDIR}/lib/'*':${ROOTDIR}/gatetool-runpipeline.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/'*' RunPipeline "$@" 
fi  

