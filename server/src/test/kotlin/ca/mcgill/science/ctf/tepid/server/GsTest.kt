package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.internal.resource
import ca.mcgill.science.ctf.tepid.server.utils.Gs
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class GsTest {

    companion object : WithLogging() {
        @BeforeClass
        @JvmStatic
        fun check() {
            val version = Gs.version()
            Assume.assumeTrue("Gs may not be installed", version != null)
            log.info("Version $version")
        }
    }


    /**
     * Note that test.ps is a two page pdf containing a few words
     * The first page is in colour and the second page is monochrome
     */
    @Test
    fun inkCov() {
        val coverage = Gs.inkCoverage(resource("gs/test.ps")) ?: fail("Could not get ink cov")
        log.info("test.ps: \n${coverage.joinToString("\n\t")}")
        assertEquals(2, coverage.size, "Could not get ink cov for both pages")
        assertFalse(coverage[0].monochrome, "First page should be colour")
        assertTrue(coverage[1].monochrome, "Second page should be monochrome")
        val data = Gs.coverageToInfo(coverage)
        log.info("test.ps data: $data")
        assertEquals(2, data.pages, "Should have two pages total")
        assertEquals(1, data.colourPages, "Should have one colour page")
    }

}