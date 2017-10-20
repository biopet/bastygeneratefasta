# Manual

```bash
# Minimal example for option: outputVariants (VCF based)
java -jar <BastyGenerateFasta_jar> --inputVcf myVCF.vcf \
--outputName NiceTool --outputVariants myVariants.fasta

# Minimal example for option: outputConsensus (BAM based)
java -jar <BastyGenerateFasta_jar> --bamFile myBam.bam \
--outputName NiceTool --outputConsensus myConsensus.fasta

# Minimal example for option: outputConsensusVariants
java -jar <BastyGenerateFasta_jar>  --inputVcf myVCF.vcf --bamFile myBam.bam \
--outputName NiceTool --outputConsensusVariants myConsensusVariants.fasta
```

## Output

* FASTA containing variants only
* FASTA containing all the consensus sequences based on a minimal coverage (default:8) but can be modified in the settings config
