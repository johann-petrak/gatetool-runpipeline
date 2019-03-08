# GATE tool runPipeline

This provides a flexible way of running a GATE pipeline on a set of documents in a directory
and save the result either back to the original files, or save them in a new directory or
do not save anything at all and just run the pipeline for some side-effect (e.g. training a
machine learning model, calculating corpus statistics, ...).


## Requirements

* GATE 8.5 (this does NOT work with GATE 8.4.x or earlier any more!)
* Linux, MacOS or a linux-like OS that supports bash scripting
* Java version 8
* Maven

Make sure that GATE is installed and that the environment variable `GATE_HOME` points to
the directory that contains the `./bin` subdirectory. 

If you have installed GATE by cloning the 
git `gate-core` directory, make sure the following was done:
* in gate-core: `mvn install`
* in gate-core/distro: `mvn compile`
* `GATE_HOME` is set to the full path of the `distro` directory

If you have installed GATE by downloading and unpacking the zip file of
a pre-built distribution, make sure:
* `GATE_HOME` is set to the full path of the directory that was created by unpacking the zip file

## Compiling

Before the first use compile by running the command 
* `mvn package`

## Usage

The `bin/runPipeline.sh` script can be invoked from anywhere, either put the
`bin` directory on your binary search path or invoke the script using the
full path name.

Run the `runPipeline.sh` script without parameters to obtain usage information:

`runPipeline.sh`

The command takes either two or three positional arguments:
* `pipelineFile`: the path to a gapp/xgapp file of the GATE pipeline to run on the documents
* `inDir`: the path to a directory which contains the documents to process
* `outDir`: optional path to the output directory. If option -r is not specified, then the processed documents are saved
  into this directory, otherwise they are written back into the input directory.

If the `outDir` parameter is not specified, and option -r is not specified, then documents are saved back into the
`inDir` directory. The file name of the output files depends on the format to save (currently this is the basename
of the input file with extension ".finf" for FastInfoset format or ".xml" for GATE format). If the input file has
the same name, the original file is overriden without warning!

Command line options:
* `-a. --abortonerror`: all processing is terminated as soon as the pipeline throws an exception which is not caught
  inside the pipeline
* `-b, --benchfile <arg>`: this sets the name of the benchmark file to create. If the current directory contains a directory
  `logs` then this is automatically specified to create a file inside the logs directory. This is only relevant is
  the option `-B` is specified as well.
* `-c, --config <arg>`: globally override the config file for all ParametrizedCorpusController pipelines (see the ModularPipelines
  plugin https://github.com/johann-petrak/gateplugin-ModularPipelines)
* `-C, --continue`: continue from a previous run that was interrupted or aborted. If this is specified, documents which
  already exist in the output directory will be skipped.
* `-d, --debug`: enable debugging mode and show more information about what the program does
* `-B, --dobench`: enable benchmarking
* `-f, --filterbyfeature <arg>`: the name of a document feature which is then used to decide if a document is written or not.
  The document is only saved if the value of the feature is set to `true` (as a String or Boolean).
* `-l, --logging <arg>`: the properties file to use to configure logging
* `-m, --max <arg>`: Maximum number of documents to process (not counting skipped ones if -C is also specified)
* `-A, --noaddspaceonunpack`: If specified loads documents in HTML or XML format such that no space is added on unpacking the markup
* `-o, --outformat <arg>`: Output format to use, either `xml` for GATE XML format or `finf` for FastInfoset format, default is `finf`
* `-P, --postpipeline <arg>`: Another optional pipeline to run after the main pipeline
* `-p, --prepipeline <arg>`: Another optional pipeline to run before the main pipeline
* `-q, --quiet`: Suppress messages about processed documents
* `-r, --readonly`: Do not save any documents after processing, processing is done for some other side-effect (e.g.
  training an ML model, sending documents to Mimir etc.)
* `-F, --setfeatire <arg>`: set a feature on the output documents, argument mast be of the format `name=value`.
* `-s, --skiponerrors <arg>`: If processing a document throws an error, do not save it
* `-Q, --veryquiet`: show no messages except errors
* `-h, --help`: show usage information
* `    --version`: show the program version

If the current directory contains a `logs` subdirectory then log-files, timing files
and any benchmarking files will be placed there. If no such directory exists, then
no logging/timing/benchmarking is carried out.

In addition to the above parameters the following influences how `runPipeline.sh` runs:
* `JAVA_OPTS` if this environment variable is set, the settings are getting passed on to Maven and ultimately the program
* `-D..` and `-X..`  style parameters are passed on to Maven, not the program which is probably what you want. 
