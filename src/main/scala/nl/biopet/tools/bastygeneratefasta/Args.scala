package nl.biopet.tools.bastygeneratefasta

import java.io.File

case class Args(inputVcf: File = null,
                outputVariants: File = null,
                outputConsensus: File = null,
                outputConsensusVariants: File = null,
                bamFile: File = null,
                snpsOnly: Boolean = false,
                sampleName: String = null,
                outputName: String = null,
                minAD: Int = 8,
                minDepth: Int = 8,
                reference: File = null)
