package nl.biopet.tools.bastygeneratefasta

import nl.biopet.test.BiopetTest
import org.testng.annotations.Test

object BastyGenerateFastaTest extends BiopetTest {
  @Test
  def testNoArgs(): Unit = {
    intercept[IllegalArgumentException] {
      BastyGenerateFasta.main(Array())
    }
  }
}
