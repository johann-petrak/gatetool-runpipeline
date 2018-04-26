#!/bin/bash

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

POM="${ROOTDIR}/pom.xml" 

## Before passing all the parameters on to the command, check if there are any
## which need to go before the class name.
## For now we check for parameters -X* -D*
## TODO: is there a way to add additional jars to the classpath on the fly
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
      	echo 'option -cp not supported for now!'
      	exit 1
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

if [ "${JAVA_OPTS}" != "" ]
then
  echo 'WARNING setting MAVEN_OPTS from JAVA_OPTS:' $JAVA_OPTS
  vmparams=( "${JAVA_OPTS}" "${vmparams[@]}" )
fi

export MAVEN_OPTS="${vmparams[@]}"

echo INFO final MAVEN_OPTS is $MAVEN_OPTS
# echo DEBUG final vmparms is "${vmparams[@]}"

## for now use environment variable RUNPIPELINE_LOG_PREFIX 
## to store the log and benchmark files with some other prefix than "run-"
prefix="${RUNPIPELINE_LOG_PREFIX:-run}"
timestamp=`date +%Y%m%d%H%M%S`
if [[ $havelogs == 1 ]]
then
  benchfile=./logs/${prefix}-${timestamp}-benchmark.txt
  # echo log file is ./logs/${prefix}-${timestamp}-log.txt
  # echo benchmark file is  $benchfile 
  prparams=(  "-b $benchfile" "${prparams[@]}" )  
  echo INFO final parms are '>'${prparams[@]}'<'
  /usr/bin/time -o ./logs/${prefix}-${timestamp}-time.txt mvn -f $POM -q exec:java -Dexec.mainClass=uk.ac.gate.gatetool.runpipeline.RunPipeline \"-Dexec.args=${prparams[@]}\" |& tee -a ./logs/${prefix}-${timestamp}-log.txt
  echo INFO log file is ./logs/${prefix}-${timestamp}-log.txt
  echo INFO benchmark file is  $benchfile 
else 
  echo INFO final parms are ${prparams[@]}
  mvn -f $POM -q exec:java -Dexec.mainClass=uk.ac.gate.gatetool.runpipeline.RunPipeline \"-Dexec.args=${prparams[@]}\" 
fi  

