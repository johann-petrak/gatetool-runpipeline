#!/bin/bash

## This tries the following strategies to run Scala
## - if there is a GATE_HOME environment variable, we assume the GATE installation there has 
##   the usual ./bin/gate.jar and ./lib/*jar layout and use the jars for the GATE dependencies there.
##   In addition we use the scala and lib dirs from in here for those dependencies.
## - TODO: use the Maven cache 

if [ "x${GATE_HOME}" == "x" ]
then
  echo Environment variable GATE_HOME not set
  exit 1
fi

if [[ -f "${GATE_HOME}/gate.classpath" ]]
then
  gatecp=`cat "${GATE_HOME}/gate.classpath"`
else
  if [[ -d "${GATE_HOME}/lib" ]]
  then
    gatecp="${GATE_HOME}/lib/"'*'
  else
    echo Could not find $GATE_HOME/gate.classpath nor $GATE_HOME/lib
    exit 1
  fi
fi

# echo DEBUG gatecp=$gatecp

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
SCALA_HOME="$ROOTDIR/scala" 

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
# echo DEBUG got vmparms $vmparms AND prparms $prparms
if [ "${JAVA_OPTS}" != "" ]
then
  vmparams=( "${JAVA_OPTS}" "${vmparams[@]}" )
fi

export JAVA_OPTS="${vmparams[@]}"

# echo DEBUG final JAVA_OPTS is $JAVA_OPTS
# echo DEBUG final vmparms is $vmparms
# echo DEBUG GATE_HOME is $GATE_HOME


  if [[ "$cp" == "" ]] 
  then
    ${SCALA_HOME}/bin/scala -cp ${ROOTDIR}/lib/'*':$JAR:${GATE_HOME}/bin/gate.jar:$gatecp "${prparams[@]}"
  else
    ${SCALA_HOME}/bin/scala -cp ${cp}:${ROOTDIR}/lib/'*':$JAR:${GATE_HOME}/bin/gate.jar:$gatecp  "${prparams[@]}"
  fi

