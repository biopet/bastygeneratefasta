package nl.biopet.tools.bastygeneratefasta

import java.io.PrintWriter

import htsjdk.samtools.SamReaderFactory
import htsjdk.samtools.reference.IndexedFastaSequenceFile
import htsjdk.variant.variantcontext.VariantContext
import htsjdk.variant.vcf.VCFFileReader

import nl.biopet.utils.tool.ToolCommand
import nl.biopet.utils.ngs.vcf._

import scala.collection.JavaConversions._


object BastyGenerateFasta extends ToolCommand[Args] {
  def emptyArgs: Args = Args()
  def argsParser = new ArgsParser(toolName)
  protected implicit var cmdArgs: Args = _
  private val chunkSize = 100000

  def main(args: Array[String]): Unit = {

    cmdArgs = cmdArrayToArgs(args)

    logger.info("Start")

    bastyGenerateFasta(cmdArgs)

    logger.info("Done")
  }

  def bastyGenerateFasta(cmdArgs: Args): Unit = {
    if (cmdArgs.outputVariants != null) {
      writeVariantsOnly()
    }
    if (cmdArgs.outputConsensus != null || cmdArgs.outputConsensusVariants != null) {
      writeConsensus()
    }

    //FIXME: what to do if outputcConsensus is set, but not outputConsensusVariants (and vice versa)?
  }


  protected def writeConsensus() {
    //FIXME: preferably split this up in functions, so that they can be unit tested
    val referenceFile = new IndexedFastaSequenceFile(cmdArgs.reference)
    val referenceDict = referenceFile.getSequenceDictionary

    for (chr <- referenceDict.getSequences) {
      val chunks = (for (chunk <- (0 to (chr.getSequenceLength / chunkSize)).par) yield {
        val chrName = chr.getSequenceName
        val begin = chunk * chunkSize + 1
        val end = {
          val e = (chunk + 1) * chunkSize
          if (e > chr.getSequenceLength) chr.getSequenceLength else e
        }

        logger.info("begin on: chrName: " + chrName + "  begin: " + begin + "  end: " + end)

        val referenceSequence = referenceFile.getSubsequenceAt(chrName, begin, end)

        val variants: Map[(Int, Int), VariantContext] = if (cmdArgs.inputVcf != null) {
          val reader = new VCFFileReader(cmdArgs.inputVcf, true)
          (for (variant <- reader.query(chrName, begin, end) if !cmdArgs.snpsOnly || variant.isSNP)
            yield {
              (variant.getStart, variant.getEnd) -> variant
            }).toMap
        } else Map()

        val coverage: Array[Int] = Array.fill(end - begin + 1)(0)
        if (cmdArgs.bamFile != null) {
          val inputSam = SamReaderFactory.makeDefault.open(cmdArgs.bamFile)
          for (r <- inputSam.query(chr.getSequenceName, begin, end, false)) {
            val s = if (r.getAlignmentStart < begin) begin else r.getAlignmentStart
            val e = if (r.getAlignmentEnd > end) end else r.getAlignmentEnd
            for (t <- s to e) coverage(t - begin) += 1
          }
        } else {
          for (t <- coverage.indices) coverage(t) = cmdArgs.minDepth
        }

        val consensus = for (t <- coverage.indices) yield {
          if (coverage(t) >= cmdArgs.minDepth) referenceSequence.getBases()(t).toChar
          else 'N'
        }

        val buffer: StringBuilder = new StringBuilder()
        if (cmdArgs.outputConsensusVariants != null) {
          var consensusPos = 0
          while (consensusPos < consensus.size) {
            val genomePos = consensusPos + begin
            val variant = variants.find(a => a._1._1 >= genomePos && a._1._2 <= genomePos)
            if (variant.isDefined) {
              logger.info(variant.get._2)
              val stripPrefix = if (variant.get._1._1 < begin) begin - variant.get._1._1 else 0
              val stripSuffix = if (variant.get._1._2 > end) variant.get._1._2 - end else 0
              val allele = getMaxAllele(variant.get._2)
              consensusPos += variant.get._2.getReference.getBases.length
              buffer.append(allele.substring(stripPrefix, allele.length - stripSuffix))
            } else {
              buffer.append(consensus(consensusPos))
              consensusPos += 1
            }
          }
        }

        chunk -> (consensus.mkString.toUpperCase, buffer.toString().toUpperCase)
      }).toMap
      if (cmdArgs.outputConsensus != null) {
        val writer = new PrintWriter(cmdArgs.outputConsensus)
        writer.println(">" + cmdArgs.outputName)
        for (c <- chunks.keySet.toList.sortWith(_ < _)) {
          writer.print(chunks(c)._1)
        }
        writer.println()
        writer.close()
      }
      if (cmdArgs.outputConsensusVariants != null) {
        val writer = new PrintWriter(cmdArgs.outputConsensusVariants)
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
    val writer = new PrintWriter(cmdArgs.outputVariants)
    writer.println(">" + cmdArgs.outputName)
    val vcfReader = new VCFFileReader(cmdArgs.inputVcf, false)
    for (vcfRecord <- vcfReader if !cmdArgs.snpsOnly || vcfRecord.isSNP) yield {
      writer.print(getMaxAllele(vcfRecord))
    }
    writer.println()
    writer.close()
    vcfReader.close()
  }

  // TODO: what does this do?
  // Seems to me it finds the allele in a sample with the highest AD value
  // if this allele is shorter than the largest allele, it will append '-' to the string
  protected[tools] def getMaxAllele(vcfRecord: VariantContext)(implicit cmdArgs: Args): String = {
    val maxSize = getLongestAllele(vcfRecord).getBases.length

    if (cmdArgs.sampleName == null) {
      return fillAllele(vcfRecord.getReference.getBaseString, maxSize)
    }

    val genotype = vcfRecord.getGenotype(cmdArgs.sampleName)

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
}
