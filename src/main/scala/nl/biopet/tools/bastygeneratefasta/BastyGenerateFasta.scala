package nl.biopet.tools.bastygeneratefasta

import java.io.PrintWriter
import java.io.File
import htsjdk.samtools.SamReaderFactory
import htsjdk.samtools.reference.IndexedFastaSequenceFile
import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.VCFFileReader

import nl.biopet.utils.tool.ToolCommand
import nl.biopet.utils.ngs.vcf._

import scala.collection.JavaConversions._

object BastyGenerateFasta extends ToolCommand[Args] {
  def emptyArgs: Args = Args()
  def argsParser = new ArgsParser(this)
  protected implicit var cmdArgs: Args = _
  private val chunkSize = 100000

  def main(args: Array[String]): Unit = {

    cmdArgs = cmdArrayToArgs(args)

    //Check if files exist if defined
    for { file <- List(cmdArgs.inputVcf, cmdArgs.bamFile, cmdArgs.reference) } if (file.isDefined) {
      require(file.get.exists(), s"File does not exist: ${file.get.getName}")
    }
    if (cmdArgs.reference.isDefined) {
      val index = new File(cmdArgs.reference.get.getAbsolutePath + ".fai")
      require(
        index.exists(),
        s"Reference does not have index. Path does not exist: ${index.getAbsolutePath}")
    }
    logger.info("Start")

    bastyGenerateFasta(cmdArgs)

    logger.info("Done")
  }

  def bastyGenerateFasta(cmdArgs: Args): Unit = {
    if (cmdArgs.outputVariants.isDefined) {
      writeVariantsOnly()
    }
    if (cmdArgs.outputConsensus.isDefined || cmdArgs.outputConsensusVariants.isDefined) {
      writeConsensus()
    }

    //FIXME: what to do if outputcConsensus is set, but not outputConsensusVariants (and vice versa)?
  }

  protected def writeConsensus() {
    //FIXME: preferably split this up in functions, so that they can be unit tested
    val referenceFile = new IndexedFastaSequenceFile(cmdArgs.reference.get)
    val referenceDict = referenceFile.getSequenceDictionary

    for (chr <- referenceDict.getSequences) {
      val chunks =
        (for (chunk <- (0 to (chr.getSequenceLength / chunkSize)).par) yield {
          val chrName = chr.getSequenceName
          val begin = chunk * chunkSize + 1
          val end = {
            val e = (chunk + 1) * chunkSize
            if (e > chr.getSequenceLength) chr.getSequenceLength else e
          }

          logger.info(
            "begin on: chrName: " + chrName + "  begin: " + begin + "  end: " + end)

          val referenceSequence =
            referenceFile.getSubsequenceAt(chrName, begin, end)

          val variants: Map[(Int, Int), VariantContext] =
            if (cmdArgs.inputVcf != null) {
              val reader = new VCFFileReader(cmdArgs.inputVcf.get, true)
              (for (variant <- reader.query(chrName, begin, end)
                    if !cmdArgs.snpsOnly || variant.isSNP)
                yield {
                  (variant.getStart, variant.getEnd) -> variant
                }).toMap
            } else Map()

          val coverage: Array[Int] = Array.fill(end - begin + 1)(0)
          if (cmdArgs.bamFile != null) {
            val inputSam =
              SamReaderFactory.makeDefault.open(cmdArgs.bamFile.get)
            for (r <- inputSam.query(chr.getSequenceName, begin, end, false)) {
              val s =
                if (r.getAlignmentStart < begin) begin else r.getAlignmentStart
              val e = if (r.getAlignmentEnd > end) end else r.getAlignmentEnd
              for (t <- s to e) coverage(t - begin) += 1
            }
          } else {
            for (t <- coverage.indices) coverage(t) = cmdArgs.minDepth
          }

          val consensus = for (t <- coverage.indices) yield {
            if (coverage(t) >= cmdArgs.minDepth)
              referenceSequence.getBases()(t).toChar
            else 'N'
          }

          val buffer: StringBuilder = new StringBuilder()
          if (cmdArgs.outputConsensusVariants != null) {
            var consensusPos = 0
            while (consensusPos < consensus.size) {
              val genomePos = consensusPos + begin
              val variant = variants.find(a =>
                a._1._1 >= genomePos && a._1._2 <= genomePos)
              if (variant.isDefined) {
                logger.info(variant.get._2)
                val stripPrefix =
                  if (variant.get._1._1 < begin) begin - variant.get._1._1
                  else 0
                val stripSuffix =
                  if (variant.get._1._2 > end) variant.get._1._2 - end else 0
                val allele = getMaxAllele(variant.get._2)
                consensusPos += variant.get._2.getReference.getBases.length
                buffer.append(
                  allele.substring(stripPrefix, allele.length - stripSuffix))
              } else {
                buffer.append(consensus(consensusPos))
                consensusPos += 1
              }
            }
          }

          chunk -> (consensus.mkString.toUpperCase, buffer
            .toString()
            .toUpperCase)
        }).toMap
      if (cmdArgs.outputConsensus != null) {
        val writer = new PrintWriter(cmdArgs.outputConsensus.get)
        writer.println(">" + cmdArgs.outputName)
        for (c <- chunks.keySet.toList.sortWith(_ < _)) {
          writer.print(chunks(c)._1)
        }
        writer.println()
        writer.close()
      }
      if (cmdArgs.outputConsensusVariants != null) {
        val writer = new PrintWriter(cmdArgs.outputConsensusVariants.get)
        writer.println(">" + cmdArgs.outputName)
        for (c <- chunks.keySet.toList.sortWith(_ < _)) {
          writer.print(chunks(c)._2)
        }
        writer.println()
        writer.close()
      }
    }
  }

  protected[tools] def writeVariantsOnly() {
    val writer = new PrintWriter(cmdArgs.outputVariants.get)
    writer.println(">" + cmdArgs.outputName)
    val vcfReader = new VCFFileReader(cmdArgs.inputVcf.get, false)
    for (vcfRecord <- vcfReader if !cmdArgs.snpsOnly || vcfRecord.isSNP)
      yield {
        writer.print(getMaxAllele(vcfRecord))
      }
    writer.println()
    writer.close()
    vcfReader.close()
  }

  // TODO: what does this do?
  // Seems to me it finds the allele in a sample with the highest AD value
  // if this allele is shorter than the largest allele, it will append '-' to the string
  protected[tools] def getMaxAllele(vcfRecord: VariantContext)(
      implicit cmdArgs: Args): String = {
    val maxSize = getLongestAllele(vcfRecord).getBases.length

    if (cmdArgs.sampleName == null) {
      return fillAllele(vcfRecord.getReference.getBaseString, maxSize)
    }

    val genotype = vcfRecord.getGenotype(cmdArgs.sampleName.get)

    if (genotype == null) {
      return fillAllele("", maxSize)
    }

    val AD =
      if (genotype.hasAD) genotype.getAD
      else Array.fill(vcfRecord.getAlleles.size())(cmdArgs.minAD)

    if (AD == null) {
      return fillAllele("", maxSize)
    }

    val maxADid = AD.zipWithIndex.maxBy(_._1)._2

    if (AD(maxADid) < cmdArgs.minAD) {
      return fillAllele("", maxSize)
    }

    fillAllele(vcfRecord.getAlleles()(maxADid).getBaseString, maxSize)
  }

  def descriptionText: String =
    """
      |This tool generates Fasta files out of variant (SNP) alignments or full alignments (consensus).
      |It can be very useful to produce the right input needed for follow up tools,
      |for example phylogenetic tree building.
    """.stripMargin

  def manualText: String =
    """
      |
    """.stripMargin
  def exampleText: String =
    s"""
       |Minimal example for option: `--outputVariants` (VCF based)
       |${example("--inputVcf",
                  "myVCF.vcf",
                  "--outputName",
                  "NiceTool",
                  "--outputVariants",
                  "myVariants.fasta")}
       |
       |Minimal example for option: `--outputConsensus` (BAM based)
       |${example("--bamFile",
                  "myBam.bam",
                  "--outputName",
                  "NiceTool",
                  "--outputConsensus",
                  "myConsensus.fasta",
                  "--reference",
                  "reference.fa")}
       |
       |Minimal example for option: outputConsensusVariants
       |${example(
         "--inputVcf",
         "myVCF.vcf",
         "--bamFile",
         "myBam.bam",
         "--outputName",
         "NiceTool",
         "--outputConsensusVariants",
         "myConsensusVariants.fasta",
         "--reference",
         "reference.fa"
       )}
       |
     """.stripMargin
}
