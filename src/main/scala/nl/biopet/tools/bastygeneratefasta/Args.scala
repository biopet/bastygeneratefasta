package nl.biopet.tools.bastygeneratefasta

import java.io.File

case class Args(inputVcf: Option[File] = None,
                outputVariants: Option[File] = None,
                outputConsensus: Option[File] = None,
                outputConsensusVariants: Option[File] = None,
                bamFile: Option[File] = None,
                snpsOnly: Boolean = false,
                sampleName: Option[String] = None,
                outputName: Option[String] = None,
                minAD: Int = 8,
                minDepth: Int = 8,
                reference: Option[File] = None)
