/*
 * Copyright (c) 2014 Biopet
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package nl.biopet.tools.bastygeneratefasta

import java.io.File

import nl.biopet.utils.tool.{AbstractOptParser, ToolCommand}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

class ArgsParser(toolCommand: ToolCommand[Args])
    extends AbstractOptParser[Args](toolCommand) {
  opt[File]('V', "inputVcf") valueName "<file>" action { (x, c) =>
    c.copy(inputVcf = Some(x))
  } text "vcf file, needed for outputVariants and outputConsensusVariants"
  opt[File]("bamFile") valueName "<file>" action { (x, c) =>
    c.copy(bamFile = Some(x))
  } text "bam file, needed for outputConsensus and outputConsensusVariants"
  opt[File]("outputVariants") maxOccurs 1 valueName "<file>" action { (x, c) =>
    c.copy(outputVariants = Some(x))
  } text "fasta with only variants from vcf file"
  opt[File]("outputConsensus") maxOccurs 1 valueName "<file>" action { (x, c) =>
    c.copy(outputConsensus = Some(x))
  } text "Consensus fasta from bam, always reference bases else 'N'"
  opt[File]("outputConsensusVariants") maxOccurs 1 valueName "<file>" action {
    (x, c) =>
      c.copy(outputConsensusVariants = Some(x))
  } text "Consensus fasta from bam with variants from vcf file, always reference bases else 'N'"
  opt[Unit]("snpsOnly") action { (_, c) =>
    c.copy(snpsOnly = true)
  } text "Only use snps from vcf file"
  opt[String]("sampleName") action { (x, c) =>
    c.copy(sampleName = Some(x))
  } text "Sample name in vcf file"
  opt[String]("outputName") required () action { (x, c) =>
    c.copy(outputName = Some(x))
  } text "Output name in fasta file header"
  opt[Int]("minAD") action { (x, c) =>
    c.copy(minAD = x)
  } text "min AD value in vcf file for sample. Defaults to: 8"
  opt[Int]("minDepth") action { (x, c) =>
    c.copy(minDepth = x)
  } text "min depth in bam file. Defaults to: 8"
  opt[File]("reference") action { (x, c) =>
    c.copy(reference = Some(x))
  } text "Indexed reference fasta file"

  checkConfig { c =>
    {
      val err: ListBuffer[String] = ListBuffer()
      if (c.outputConsensus.isDefined || c.outputConsensusVariants.isDefined) {
        if (c.reference.isEmpty)
          err.add("No reference supplied")
        if (c.outputConsensusVariants.isDefined && c.inputVcf.isEmpty)
          err.add(
            "To write outputVariants input vcf is required, please use --inputVcf option")
        if (c.sampleName.isDefined && c.bamFile.isEmpty)
          err.add(
            "To write Consensus input bam file is required, please use --bamFile option")
      }
      if (c.outputVariants.isDefined && c.inputVcf.isEmpty)
        err.add(
          "To write outputVariants input vcf is required, please use --inputVcf option")
      if (c.outputVariants.isEmpty && c.outputConsensus.isEmpty && c.outputConsensusVariants.isEmpty)
        err.add("No output file selected")
      if (err.isEmpty) success
      else failure(err.mkString("", "\nError: ", "\n"))
    }
  }
}
