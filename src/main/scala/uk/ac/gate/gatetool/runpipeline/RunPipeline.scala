
import gate._
import gate.util.ExtensionFileFilter
import gate.util.persistence.PersistenceManager
import gate.creole.AbstractController
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Level
import gate.util.Benchmark
import java.util._
import java.io.File
import org.rogach.scallop._
import org.rogach.scallop.exceptions.Help
import scala.collection.JavaConverters._
import scala.util.control._

// NOTE: this program sets the following java properties before invoking the 
// controllers:
// runPipeline.inDirPath  - full path of the input directory 
// runPipeline.inDirName - just the name without any slashes of the directory
// runPipeline.outDirPath
// runPipeline.outDirName
// runPipeline.pipelinePath 
// runPipeline.pipelineName - pipeline filename without the extension
// runPipeline.curDirPath 
// runPipeline.curDirName

// for explicit conversion
// import scala.collection.JavaConverters._

// TODO: option processing is flawed: for some reason, making the trailArgs required does not go well
// with showing usage information and -h does not seem to work as wanted either.

package uk.ac.gate.gatetool.runpipeline {

object RunPipeline {

  def main(args: Array[String]) {

    def onError(e: Throwable, scallop: Scallop) = e match {
      case Help(_) => 
        scallop.printHelp
        sys.exit(0)
      case _ =>
        System.err.println("Error: "+e.getMessage)
        scallop.printHelp
        sys.exit(1)
    }

    
    class Conf(arguments: Seq[String], onError: (Throwable,Scallop) => Nothing) extends ScallopConf(arguments) {
      version("RunPipeline.scala called from the runPipeline.sh script.")
      banner("""Usage: runPipeline.sh [OPTIONS] pipelineFile.xgapp inDir [outDir]""")
      footer("""If outDir is not specified, the processed files will be written back into the inDir""")
      val help = opt[Boolean]("help",'h',descr="Show usage information",required=false,default=Some(false))
      // NOTE: this was -e/evaluate previously and always did set document feature runEvaluation
      val readonly = opt[Boolean]("readonly",'r',descr="Do not save the documents after processing",required=false,default=Some(false))
      val setfeature = opt[String]("setfeature",'F',descr="Set a document feature, must be in the form of featureName=value",required=false,default=Some(""))
      val abortonerror = opt[Boolean]("abortonerror",'a',descr="Abort all processing if the pipeline throws and exception",required=false,default=Some(false))
      val noAddSpaceOnUnpack = opt[Boolean]("noaddspaceonunpack",'A',descr="If specified, suppress adding space on unpacking markup (for HTML, XML input documents)",required=false,default=Some(false))
      val max = opt[Int]("max",'m',descr="Maximum number of documents to process (not counting skipped ones if -C is specified)",required=false)
      val outformat = opt[String]("outformat",'o',descr="Output format to use, either 'xml' or 'finf', default is 'finf'",required=false,default=Some("finf"))
      val config = opt[String]("config",'c',descr="Config file to use. This will override all config files in the pipeline and all sub-pipelines",required=false)
      val debug = opt[Boolean]("debug",'d',descr="Enable debugging mode",required=false,default=Some(false))
      val dobench = opt[Boolean]("dobench",'B',descr="Enable benchmarking",required=false,default=Some(false))
      val benchfile = opt[String]("benchfile",'b',descr="Name of the benchmark file to create",required=false,default=Some("gate_benchmark.txt"))
      val postpipeline = opt[String]("postpipeline",'P',descr="Pipeline file of pipeline to run after the main pipeline",required=false,default=Some(""))
      val prepipeline = opt[String]("prepipeline",'p',descr="Pipeline file of pipeline to run before the main pipeline",required=false,default=Some(""))
      val filterbyfeature = opt[String]("filterbyfeature",'f',descr="Name of a document feature for deciding if document will be written: if true document will be saved",required=false,default=Some(""))
      val logging = opt[String]("logging",'l',descr="Use the given file to configure logging",required=false)
      val continue = opt[Boolean]("continue",'C',descr="Continue and do not override existing documents",required=false,default=Some(false))
      val skiponerrors = opt[Boolean]("skiponerrors",'s',descr="If processing the document throws an exception do NOT save it",required=false,default=Some(false))
      val quiet = opt[Boolean]("quiet",'q',descr="Do not show messages per processed document",required=false,default=Some(false))
      val veryquiet = opt[Boolean]("veryquiet",'Q',descr="Do not show any messages except errors",required=false,default=Some(false))
      val pipeline = trailArg[String](required=true,descr="The pipeline file to use")
      val indir = trailArg[String](required=true,descr="Input directory")
      val outdir = trailArg[String](required=false,descr="Output directory")
      override protected def onError(e: Throwable) = onError(e, builder)
    }
    val conf = new Conf(args,onError)
    

    if(conf.pipeline.isEmpty) {
      System.err.println("Pipeline and input directory are required")
      conf.printHelp()
      System.exit(1)
    }
    if(conf.indir.isEmpty) {
      System.err.println("Input directory is required")
      conf.printHelp()
      System.exit(1)
    }
    if(conf.help()) {
      conf.printHelp()
      System.exit(0)
    }
    
    // This is a global setting so we only need to set once
    val userConfig = Gate.getUserConfig()
    userConfig.put(GateConstants.DOCUMENT_ADD_SPACE_ON_UNPACK_FEATURE_NAME,!conf.noAddSpaceOnUnpack())
    
    
    val inDir = new File(conf.indir())
    if(!inDir.exists() || !inDir.isDirectory()) {
      System.err.println("Input directory does not exist or is not a directory: "+inDir);
      System.exit(1)
    }
    val outDir = if(conf.outdir.isEmpty) { inDir } else { new File(conf.outdir()) }
    if(!outDir.exists() || !outDir.isDirectory()) {
      System.err.println("Output directory does not exist or is not a directory: "+outDir)
      System.exit(1)
    }
    val pipelineFile = new File(conf.pipeline())
    val postPipelineFile: File = if(conf.postpipeline().isEmpty) { null } else { new File(conf.postpipeline()) }
    val prePipelineFile: File = if(conf.prepipeline().isEmpty) { null } else { new File(conf.prepipeline()) }
    
    val debug = conf.debug()
    val nobench = !conf.dobench()
    
    var quiet = conf.quiet()
    var veryquiet = conf.veryquiet()
    
    var filterbyfeature = conf.filterbyfeature()
    
    var setfeature = conf.setfeature()
    var setfeatureName = "" 
    var setfeatureValue = ""
    if(!setfeature.isEmpty()) {
      if(!setfeature.contains("=")) {
        System.err.println("docfeature parameter does not contain an equals sign: "+setfeature)
        System.exit(1)
      }
      val tmp1 = setfeature.split("=",2)
      setfeatureName = tmp1(0)
      setfeatureValue = tmp1(1)
    }
    
    if(!filterbyfeature.isEmpty && conf.readonly()) {
      System.err.println("WARNING: setting a filter feature together with read-only mode does not make sense, filter feature ignored!")
    }
    
    if(veryquiet) { quiet = true }
      
    if(!veryquiet) {
      println("Pipeline:    "+pipelineFile)
      println("InDir:       "+inDir)
      println("OutDir:      "+outDir)
    }
    
    if(!conf.config.isEmpty) {
      if(!(new File(conf.config()).exists())) { 
        System.err.println("ERROR: Configuration file does not exist: "+conf.config())
        System.exit(1)
      }
      System.setProperty("at.ofai.gate.modularpipelines.configFile",conf.config()) 
    }
    
    if(!conf.logging.isEmpty) {
      var lprops = new File(new File("."),conf.logging())
      if(lprops.exists()) {
        lprops = lprops.getCanonicalFile()
        val lpropsUrl = lprops.toURI().toURL()
        System.setProperty("log4j.configuration",lprops.toURI().toURL().toString())
        System.err.println("Log4j property file used: "+lpropsUrl)
      } else {
        System.err.println("Warning: option -l specified but no file "+conf.logging()+" found in the current directory")
      }
    }
    
    val gatehome = System.getenv().get("GATE_HOME")
    if(gatehome == null) {
      System.err.println("ERROR: Environment variable GATE_HOME is not set!")
      System.exit(1);
    }
      
    if(!nobench) {
      System.getProperties().put("gate.enable.benchmark","true")
    }

    var logFileName = conf.benchfile()
    if(debug && !nobench) {
      if(!veryquiet) {
        System.err.println("Using benchmark file: "+logFileName)
      }
    }
    System.out.println("Starting up GATE ....");
    Gate.setGateHome(new File(gatehome))
    
    Gate.setNetConnected(false);
    Gate.setLocalWebServer(false);

    Gate.runInSandbox(true)
    Gate.init()
    if(!nobench) {
      Benchmark.setBenchmarkingEnabled(true)
    }
    System.out.println("GATE startup completed.");

    gate.Utils.loadPlugin("Format_FastInfoset")
    // now we have to do it like this, but we should change this again as soon
    // as an easier way, based on mime types is added to GATE.
    var docExporter:DocumentExporter = null
    if(conf.outformat() == "finf") {
      docExporter = Gate.getCreoleRegister()
                     .get("gate.corpora.FastInfosetExporter")
                     .getInstantiations().iterator().next()
                     .asInstanceOf[DocumentExporter]
    }

    if(!nobench) {
      val logger = Benchmark.logger
      logger.removeAllAppenders()
      val logFile = new File(logFileName)
      val appender = new FileAppender(new PatternLayout("%m%n"),logFile.getAbsolutePath(),true);
      appender.setName("gate-benchmark");
      logger.addAppender(appender)
      logger.setAdditivity(false)
      logger.setLevel(Level.DEBUG)
    }

    val corpus = Factory.newCorpus("TmpCorpus");

    // set the java properties for this run
    System.setProperty("runPipeline.inDirPath",inDir.getCanonicalPath())
    System.setProperty("runPipeline.inDirName",inDir.getName())
    System.setProperty("runPipeline.outDirPath",outDir.getCanonicalPath())
    System.setProperty("runPipeline.outDirName",outDir.getName())
    System.setProperty("runPipeline.pipelinePath",pipelineFile.getCanonicalPath())
    System.setProperty("runPipeline.pipelineName",stripExtension(pipelineFile.getName()))
    val curDir = new File(".")
    System.setProperty("runPipeline.curDirPath",curDir.getCanonicalPath())
    System.setProperty("runPipeline.curDirName",curDir.getName())
    
    

    var preController: CorpusController = null
    if(prePipelineFile != null) {
      if(!veryquiet) {
        System.out.println("Loading preprocessing pipeline "+prePipelineFile.getCanonicalPath()+"...");
      }
      preController = PersistenceManager.loadObjectFromFile(prePipelineFile).asInstanceOf[CorpusController]
      preController.setCorpus(corpus)
      preController.asInstanceOf[AbstractController].setControllerCallbacksEnabled(false)
      preController.asInstanceOf[AbstractController].invokeControllerExecutionStarted()  
    }

    if(!veryquiet) {
      System.out.println("Loading pipeline "+pipelineFile.getCanonicalPath()+"...");
    }
    val controller = PersistenceManager.loadObjectFromFile(pipelineFile).asInstanceOf[CorpusController]
    controller.setCorpus(corpus)
    
    controller.asInstanceOf[AbstractController].setControllerCallbacksEnabled(false);
    controller.asInstanceOf[AbstractController].invokeControllerExecutionStarted();

    var postController: CorpusController = null
    if(postPipelineFile != null) {
      if(!veryquiet) {
        System.out.println("Loading postrocessing pipeline "+postPipelineFile.getCanonicalPath()+"...");
      }
      postController = PersistenceManager.loadObjectFromFile(postPipelineFile).asInstanceOf[CorpusController]
      postController.setCorpus(corpus)
      postController.asInstanceOf[AbstractController].setControllerCallbacksEnabled(false)
      postController.asInstanceOf[AbstractController].invokeControllerExecutionStarted()  
    }
    
    var nerrors = 0
    var nprocessed = 0
    var nskipped = 0
    var nsaved = 0
    var benchmarkId = ""
    
    var extFilter = """.+""".r
    
    var theError: Throwable = null
    
    val loop = new Breaks
    
    loop.breakable { 
    
    inDir.listFiles.filter(!_.isDirectory).filter(f => extFilter.findFirstIn(f.getName).isDefined).sorted.foreach { docFile =>
    
      val docFileName = docFile.getName();
      // if the -C / continue parameter is given, we first check if the document exists in the output directory
      // and if yes, we skip the document
      var skip = false;
      if(conf.continue()) {
        val outFileName = if(docExporter == null) {
          docFileName.replaceAll("\\.[^.]+$",".xml")
        } else {
          docFileName.replaceAll("\\.[^.]+$",".finf")
        }
        if(new File(outDir,outFileName).exists()) {
          if(!quiet) {
            System.out.println("NOT Processing "+docFileName+", already in the output directory ...");
          }
          nskipped += 1
          skip = true;
        }
      }
      
      
      if(!skip) {
      
      nprocessed += 1
      
      if(!conf.max.isEmpty && conf.max() < nprocessed) {
        // do not process this document
        // we should really bail out of the loop here, but at the moment we just
        // go on and do nothing ...
      } else {
      
      if(!quiet) {
        System.out.println("Processing "+docFileName+"...");
      }
    
      var hadError = false
    
      val fm = Factory.newFeatureMap();
      fm.put(Document.DOCUMENT_MARKUP_AWARE_PARAMETER_NAME, true.asInstanceOf[Object])
      fm.put("sourceUrl", docFile.toURI().toURL())
      fm.put(Document.DOCUMENT_ENCODING_PARAMETER_NAME, "UTF-8")
      //fm.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, "text/html")
      val doc = gate.Factory.createResource("gate.corpora.DocumentImpl", fm).asInstanceOf[Document]
      if(debug) { System.err.println("Document created: "+doc.getName()) }
      if(!setfeatureName.isEmpty) {
        doc.getFeatures.put(setfeatureName,setfeatureValue)
      }
      
      corpus.add(doc)
      if(preController != null) {
        if(debug) { System.err.println("Running pre controller ...") }
        preController.execute()
      }
      if(!nobench) {
        benchmarkId = "DUMMYPIPELINE"
        try {
          if(debug) { System.err.println("Running with benchmarking: "+doc.getName()) }
          //if(controller.isInstanceOf[ConditionalSerialAnalyserController]) {
	        //controller.asInstanceOf[ConditionalSerialAnalyserController].setDocument(doc)
          //}
          Benchmark.executeWithBenchmarking(controller,benchmarkId,this,null)
          if(debug) { System.err.println("Processed without errrors with benchmarking: "+doc.getName()) }
        } catch {
          case e: Exception => {
            System.err.println("ERROR when processing document "+docFile)
            e.printStackTrace(System.err)
            nerrors += 1
            hadError = true
            theError = e 
          }
        }
      } else { // benchmarking
        try {
          if(debug) { System.err.println("Running with NO benchmarking: "+doc.getName()) }
          //if(controller.isInstanceOf[ConditionalSerialAnalyserController]) {
          //  controller.asInstanceOf[ConditionalSerialAnalyserController].setDocument(doc)
          //}
          controller.execute()
          if(debug) { System.err.println("Processed without errrors with NO benchmarking: "+doc.getName()) }
        } catch  {
          case e: Exception => {
            System.err.println("ERROR when processing document "+docFile)
            e.printStackTrace(System.err)
            nerrors += 1
            hadError = true
            theError = e
          }
        }
      }
      if(postController != null) {
        if(debug) { System.err.println("Running post controller...") }
        postController.execute()
      }
      var start:Long = 0
      if(!nobench) start = Benchmark.startPoint()
      if(!conf.readonly()) {
        if(conf.skiponerrors() && hadError) {
          if(debug) { System.err.println("Not saving document because there was an error: "+doc.getName()) }
        } else {
          // if filterfeature is specified, only actually save if the feature is set to 
          // one of the followign: Boolean true, String "true" 
          var saveIt = true
          if(!filterbyfeature.isEmpty) {
            val ffeature = doc.getFeatures().get(filterbyfeature)
            //System.err.println("DEBUG: checking feature "+filterbyfeature+" is "+ffeature)
            if(ffeature != null) {
              if(ffeature.isInstanceOf[String] && !ffeature.asInstanceOf[String].toLowerCase.equals("true")) {
                saveIt = false
              } else if(ffeature.isInstanceOf[Boolean] && !ffeature.asInstanceOf[Boolean]) {
                saveIt = false
              }
            }
          } // if filterfeature is not empty
          if(saveIt) {
        var outFile:File = null
        if(docExporter == null) {
          val outDocFileName = docFileName.replaceAll("\\.[^.]+$",".xml")
          outFile = new File(outDir,outDocFileName)
          if(debug) { System.err.println("Saving document as XML: "+doc.getName()) }
          gate.corpora.DocumentStaxUtils.writeDocument(doc,outFile)
          nsaved += 1
        } else {
          val outDocFileName = docFileName.replaceAll("\\.[^.]+$",".finf")
          outFile = new File(outDir,outDocFileName)
          if(debug) { System.err.println("Saving document as FINF: "+doc.getName()) }
          docExporter.export(doc,outFile, Factory.newFeatureMap());
          nsaved += 1
        }
        if(!quiet) {
          System.out.println("Processed "+docFileName+" written to "+outFile);
        }
          } else  { // if saveIt
                    if(!quiet) {
          System.out.println("Processed "+docFileName+" not saved because of document feature");
        }

        }
        }
      } else {
        if(!quiet) {
          System.out.println("Processed "+docFileName+" not saved because of readonly flag");
        }
      }
      if(debug) { System.err.println("Unloading document from corpus: "+doc.getName()) }
      corpus.unloadDocument(doc)
      if(debug) { System.err.println("Deleting document: "+doc.getName()) }
      Factory.deleteResource(doc)
      if(!nobench) benchmarkCheckpoint(start, "documentSaved")
      
      // TODO: we should bail out of the loop here if the abortonerror flag
      // is set and we indeed had an exception
      if(!conf.abortonerror.isEmpty && conf.abortonerror() && hadError) {
        System.err.println("Aborting processing because -a was specified");
        loop.break;
      }
      
      
      } // if max ...
      
      } // if(!skip)
      
      
    } // loop over files
    
    } // loop.breakable
    
    if(theError == null) {
      if(preController != null) {
        preController.asInstanceOf[AbstractController].invokeControllerExecutionFinished()
      }
      controller.asInstanceOf[AbstractController].invokeControllerExecutionFinished();
      if(postController != null) {
        postController.asInstanceOf[AbstractController].invokeControllerExecutionFinished()
      }      
    } else {
      if(preController != null) {
        preController.asInstanceOf[AbstractController].invokeControllerExecutionAborted(theError)
      }
      controller.asInstanceOf[AbstractController].invokeControllerExecutionAborted(theError);
      if(postController != null) {
        postController.asInstanceOf[AbstractController].invokeControllerExecutionAborted(theError)
      }      
    }

    

    if(debug) { System.err.println("Finished processing"); }
    if(!nobench) {
      if(debug) { System.out.println("Removing Benchmark appender"); }
      Benchmark.logger.removeAppender("gate-benchmark");
    }
    if(debug) { System.err.println("Removing corpus"); }
    Factory.deleteResource(corpus)
    if(debug) { System.err.println("Removing controller"); }
    Factory.deleteResource(controller)
    
    // Delete any remaining resources to avoid that the script does not terminate
    if(debug) { System.out.println("Getting resources for cleaning up ...."); }
    var listOfListOfResources = new ArrayList[List[Resource]]();
    listOfListOfResources.add(
      Gate.getCreoleRegister().getAllInstances("gate.LanguageResource"));
    listOfListOfResources.add(
      Gate.getCreoleRegister().getAllInstances("gate.ProcessingResource"));
    listOfListOfResources.add(
      Gate.getCreoleRegister().getAllInstances("gate.Controller"));
    listOfListOfResources.asScala.foreach { resources => 
      resources.asScala.foreach { aResource => 
        try { 
          if(debug) { System.err.println("Trying to remove resource "+aResource.getName()); }
          Factory.deleteResource(aResource);
        } catch {
          case e:Throwable => {
            System.err.println("Some problems occurred when cleaning up the resources.", e);
          }
        }
      } // foreach resources
    } // foreach listOfListOfResources

    if(!veryquiet) {
      System.out.println("Processing completed, processed: "+nprocessed+", errors: "+nerrors+", skipped: "+nskipped+", saved: "+nsaved)
    }
  
} // main

def benchmarkCheckpoint(startTime: Long, name: String):Unit = {
  if(Benchmark.isBenchmarkingEnabled()) { 
    Benchmark.checkPointWithDuration(
              Benchmark.startPoint()-startTime, 
              Benchmark.createBenchmarkId(name, "runPipeline"),
              this,null); 
  }
}
 

def stripExtension(filename: String): String = {
  val pos = filename.lastIndexOf(".")
  if(pos<0) filename else filename.substring(0,pos)
}

} // object RunPipeline

} // package
