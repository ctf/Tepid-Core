package ca.mcgill.science.ctf.tepid.server

import ca.allanwang.kit.logger.WithLogging
import ca.mcgill.science.ctf.tepid.server.internal.resource
import ca.mcgill.science.ctf.tepid.server.internal.testPs
import ca.mcgill.science.ctf.tepid.server.utils.Gs
import ca.mcgill.science.ctf.tepid.server.utils.PsData
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
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
        val coverage = Gs.inkCoverage(testPs) ?: fail("Could not get ink cov")
        log.info("${testPs.name}: \n${coverage.joinToString("\n\t")}")
        assertEquals(2, coverage.size, "Could not get ink cov for both pages")
        assertFalse(coverage[0].monochrome, "First page should be colour")
        assertTrue(coverage[1].monochrome, "Second page should be monochrome")
        val data = Gs.coverageToInfo(coverage)
        log.info("${testPs.name} data: $data")
        assertEquals(2, data.pages, "Should have two pages total")
        assertEquals(1, data.colourPages, "Should have one colour page")
    }

    /**
     * [GsInfo] is provided if a file's name is of the format:
     *
     * [...]_[colour]_[pages].ps
     *
     * Pages is mandatory
     */
    private val File.gsInfo: PsData?
        get() {
            val parts = nameWithoutExtension.split("_")
            if (parts.size < 3) return null
            val pages = parts[parts.size - 1].toIntOrNull() ?: return null
            val colour = parts[parts.size - 2].toIntOrNull() ?: 0
            return PsData(pages, colour)
        }

    /**
     * Used to test extra ps files
     * gs/extras is gitignored
     */
    @Test
    fun extraTests() {
        val gsDir = resource("ps/extras")
        if (!gsDir.isDirectory) {
            log.info("Skipping gs test; no files found")
            return
        }

        gsDir.listFiles { _, name -> name.endsWith(".ps") }.forEach {
            val lines = Gs.gs(it) ?: fail("Failed to get gs info for ${it.absolutePath}")
            println()
            log.info("Tested ${it.name}")
            val coverage = Gs.inkCoverage(lines)
            log.info("Coverage:\n${coverage.joinToString("\n\t")}")
            val psInfo = Gs.coverageToInfo(coverage)
            val fileInfo = it.gsInfo ?: return@forEach log.info("Resulting info: $psInfo")
            assertEquals(fileInfo, psInfo, "GS info mismatch for ${it.absolutePath}")
            log.info("Matches supplied info: $fileInfo")
        }
    }
}