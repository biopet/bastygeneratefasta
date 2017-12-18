package nl.biopet.tools.bastygeneratefasta

import java.io.File

import nl.biopet.utils.tool.AbstractOptParser

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

class ArgsParser(toolCommand: ToolCommand[Args])
    extends AbstractOptParser[Args](toolCommand) {
  opt[File]('V', "inputVcf") unbounded () valueName "<file>" action { (x, c) =>
    c.copy(inputVcf = x)
  } text "vcf file, needed for outputVariants and outputConsensusVariants" validate {
    x =>
      if (x.exists) success else failure("File does not exist: " + x)
  }
  opt[File]("bamFile") unbounded () valueName "<file>" action { (x, c) =>
    c.copy(bamFile = x)
  } text "bam file, needed for outputConsensus and outputConsensusVariants" validate {
    x =>
      if (x.exists) success else failure("File does not exist: " + x)
  }
  opt[File]("outputVariants") maxOccurs 1 unbounded () valueName "<file>" action {
    (x, c) =>
      c.copy(outputVariants = x)
  } text "fasta with only variants from vcf file"
  opt[File]("outputConsensus") maxOccurs 1 unbounded () valueName "<file>" action {
    (x, c) =>
      c.copy(outputConsensus = x)
  } text "Consensus fasta from bam, always reference bases else 'N'"
  opt[File]("outputConsensusVariants") maxOccurs 1 unbounded () valueName "<file>" action {
    (x, c) =>
      c.copy(outputConsensusVariants = x)
  } text "Consensus fasta from bam with variants from vcf file, always reference bases else 'N'"
  opt[Unit]("snpsOnly") unbounded () action { (_, c) =>
    c.copy(snpsOnly = true)
  } text "Only use snps from vcf file"
  opt[String]("sampleName") unbounded () action { (x, c) =>
    c.copy(sampleName = x)
  } text "Sample name in vcf file"
  opt[String]("outputName") required () unbounded () action { (x, c) =>
    c.copy(outputName = x)
  } text "Output name in fasta file header"
  opt[Int]("minAD") unbounded () action { (x, c) =>
    c.copy(minAD = x)
  } text "min AD value in vcf file for sample. Defaults to: 8"
  opt[Int]("minDepth") unbounded () action { (x, c) =>
    c.copy(minDepth = x)
  } text "min depth in bam file. Defaults to: 8"
  opt[File]("reference") unbounded () action { (x, c) =>
    c.copy(reference = x)
  } text "Indexed reference fasta file" validate { x =>
    if (x.exists) success else failure("File does not exist: " + x)
  }

  checkConfig { c =>
    {
      val err: ListBuffer[String] = ListBuffer()
      if (c.outputConsensus != null || c.outputConsensusVariants != null) {
        if (c.reference == null)
          err.add("No reference supplied")
        else {
          val index = new File(c.reference.getAbsolutePath + ".fai")
          if (!index.exists) err.add("Reference does not have index")
        }
        if (c.outputConsensusVariants != null && c.inputVcf == null)
          err.add(
            "To write outputVariants input vcf is required, please use --inputVcf option")
        if (c.sampleName != null && c.bamFile == null)
          err.add(
            "To write Consensus input bam file is required, please use --bamFile option")
      }
      if (c.outputVariants != null && c.inputVcf == null)
        err.add(
          "To write outputVariants input vcf is required, please use --inputVcf option")
      if (c.outputVariants == null && c.outputConsensus == null && c.outputConsensusVariants == null)
        err.add("No output file selected")
      if (err.isEmpty) success
      else failure(err.mkString("", "\nError: ", "\n"))
    }
  }
}
