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

## Before passing all the parameters on to the command, check if there are any
## which need to go before the program name.
## For now we check for parameters -X* -D* and -cp
vmparams=()
prparams=()
while test "$1" != "";
do
  full="$1"
  argc=${full:0:2}
  ## echo processing arg $full with prefix $argc
  if [[ "$argc" == "-D" ]]
  then
    vmparams=( "${vmparams[@]}" "$full" )
  else
    if [[ "$argc" == "-X" ]]
    then
      vmparams=( "${vmparams[@]}" $full )
    else
      if [[ "$full" == "-cp" ]] 
      then 
        shift
        cp="$1"
        ## echo FOUND cp, set to $cp
      else 
        prparams=( "${prparams[@]}" "$full" )
        ## echo adding $full to prparams is now "${prparams[@]}"
      fi
    fi
  fi
  shift
done
echo DEBUG got vmparms $vmparms AND prparms $prparms
if [ "${JAVA_OPTS}" != "" ]
then
  vmparams=( "${JAVA_OPTS}" "${vmparams[@]}" )
fi

export JAVA_OPTS="${vmparams[@]}"

echo DEBUG final JAVA_OPTS is $JAVA_OPTS
echo DEBUG final vmparms is $vmparms

## for now use environment variable RUNPIPELINE_LOG_PREFIX 
## to store the log and benchmark files with some other prefix than "run-"
prefix="${RUNPIPELINE_LOG_PREFIX:-run}"
timestamp=`date +%Y%m%d%H%M%S`
if [[ $havelogs == 1 ]]
then
  benchfile=./logs/${prefix}-${timestamp}-benchmark.txt
  echo log file is ./logs/${prefix}-${timestamp}-log.txt
  echo benchmark file is  $benchfile 
  if [[ "$cp" == "" ]] 
  then
    /usr/bin/time -o ./logs/${prefix}-${timestamp}-time.txt ${SCALA_HOME}/bin/scala -cp ${ROOTDIR}/lib/'*':${ROOTDIR}/gatetool-runpipeline.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/'*' uk.ac.gate.gatetool.runpipeline.RunPipeline -b $benchfile "${prparams[@]}" |& tee -a ./logs/${prefix}-${timestamp}-log.txt
  else
    /usr/bin/time -o ./logs/${prefix}-${timestamp}-time.txt ${SCALA_HOME}/bin/scala -cp ${cp}:${ROOTDIR}/lib/'*':${ROOTDIR}/gatetool-runpipeline.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/'*' uk.ac.gate.gatetool.runpipeline.RunPipeline -b $benchfile "${prparams[@]}" |& tee -a ./logs/${prefix}-${timestamp}-log.txt
  fi
  echo log file is ./logs/${prefix}-${timestamp}-log.txt
  echo benchmark file is  $benchfile 
else 
  if [[ "$cp" == "" ]] 
  then
    ${SCALA_HOME}/bin/scala -cp ${ROOTDIR}/lib/'*':${ROOTDIR}/gatetool-runpipeline.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/'*' uk.ac.gate.gatetool.runpipeline.RunPipeline "${prparams[@]}"
  else
    ${SCALA_HOME}/bin/scala -cp ${cp}:${ROOTDIR}/lib/'*':${ROOTDIR}/gatetool-runpipeline.jar:${GATE_HOME}/bin/gate.jar:${GATE_HOME}/lib/'*' uk.ac.gate.gatetool.runpipeline.RunPipeline "${prparams[@]}"
  fi
fi  
