package nl.biopet.tools.bastygeneratefasta

import java.io.File

import htsjdk.variant.vcf.VCFFileReader
import nl.biopet.utils.test.tools.ToolTest
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.testng.annotations.Test

class BastyGenerateFastaTest extends ToolTest[Args] with MockitoSugar{
  def toolCommand: BastyGenerateFasta.type = BastyGenerateFasta

  import BastyGenerateFasta._

  val veppedPath: String = resourcePath("/VEP_oneline.vcf")
  val vepped = new File(veppedPath)
  val bamPath: String = resourcePath("/paired01.bam")
  val chrQPath: String = resourcePath("/chrQ.vcf.gz")
  val chrQRefPath: String = resourcePath("/fake_chrQ.fa")
  val bam = new File(resourcePath("/paired01.bam"))
  val chrQ = new File(resourcePath("/chrQ.vcf.gz"))
  val chrQRef = new File(resourcePath("/fake_chrQ.fa"))

  @Test def testMainVcf(): Unit = {
    val tmp = File.createTempFile("basty_out", ".fa")
    tmp.deleteOnExit()
    val tmppath = tmp.getAbsolutePath
    tmp.deleteOnExit()

    val arguments = Array("-V",
      chrQPath,
      "--outputVariants",
      tmppath,
      "--sampleName",
      "Sample_101",
      "--reference",
      chrQRefPath,
      "--outputName",
      "test")
    main(arguments)
  }

  @Test def testMainVcfAndBam(): Unit = {
    val tmp = File.createTempFile("basty_out", ".fa")
    tmp.deleteOnExit()
    val tmppath = tmp.getAbsolutePath
    tmp.deleteOnExit()

    val arguments = Array("-V",
      chrQPath,
      "--outputVariants",
      tmppath,
      "--bamFile",
      bamPath,
      "--sampleName",
      "Sample_101",
      "--reference",
      chrQRefPath,
      "--outputName",
      "test")
    main(arguments)
  }

  @Test def testMainVcfAndBamMore(): Unit = {
    val tmp = File.createTempFile("basty_out", ".fa")
    tmp.deleteOnExit()
    val tmppath = tmp.getAbsolutePath
    tmp.deleteOnExit()

    val arguments = Array(
      "-V",
      chrQPath,
      "--outputConsensus",
      tmppath,
      "--outputConsensusVariants",
      tmppath,
      "--bamFile",
      bamPath,
      "--sampleName",
      "Sample_101",
      "--reference",
      chrQRefPath,
      "--outputName",
      "test"
    )
    main(arguments)
  }

  @Test def testGetMaxAllele(): Unit = {
    val reader = new VCFFileReader(vepped, false)
    val record = reader.iterator().next()

    val one = mock[Args]
    when(one.sampleName) thenReturn Some("Sample_101")
    val two = mock[Args]
    when(two.sampleName) thenReturn Some("Sample_102")

    getMaxAllele(record)(one) shouldBe "C-"
    getMaxAllele(record)(two) shouldBe "CA"

  }

  @Test
  def testNoArgs(): Unit = {
    intercept[IllegalArgumentException] {
      BastyGenerateFasta.main(Array())
    }
  }
}
