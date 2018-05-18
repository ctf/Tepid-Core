package ca.mcgill.science.ctf.tepid.server

import ca.mcgill.science.ctf.tepid.server.internal.TestConfigs
import ca.mcgill.science.ctf.tepid.server.internal.testTransaction
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class PrintJobDbTest {

    init {
        TestConfigs.setup()
    }

    @Test
    fun createAndFetch() = testTransaction {
        PrintJobs.create(PrintJob("testId",
                "testName",
                "testUser",
                Configs.tmpDir.resolve("testFile.ps")))

        val data = PrintJobs["testId"]?.let {
            with(PrintJobs) {
                assertTrue(System.currentTimeMillis() - it[created] < 1000, "Created timestamp is not accurate")
                listOf(
                        "testId" to it[id],
                        "testName" to it[name],
                        "testUser" to it[shortUser],
                        Configs.tmpDir.resolve("testFile.ps").absolutePath to it[file],
                        -1L to it[received],
                        -1L to it[processed],
                        -1L to it[printed],
                        -1L to it[failed],
                        null to it[error]
                )
            }
        } ?: fail("Could not find print job 'testId'")

        data.forEachIndexed { index, (expected, result) ->
            assertEquals(expected, result, "PrintJob match failed at index $index")
        }
    }

    @Test
    fun totalQuotaUsed() = testTransaction {

        val testUser = "test20"

        fun assertQuotaEquals(expected: Int, message: String) =
                assertEquals(expected, PrintJobs.getTotalQuotaUsed(testUser), message)

        assertQuotaEquals(0, "User with no print jobs should have 0 quota used")

        // add printed job 1

        val job1 = PrintJob("job1Id", "job1", testUser, Configs.tmpDir.resolve("job1.ps"))
        PrintJobs.create(job1)
        PrintJobs.update(job1) {
            it[printed] = System.currentTimeMillis()
            it[quotaCost] = 35
        }

        // add non printed job 2

        assertQuotaEquals(35, "Quota used did not update after printed job")

        val job2 = PrintJob("job2Id", "job2", testUser, Configs.tmpDir.resolve("job2.ps"))
        PrintJobs.create(job2)
        PrintJobs.update(job2) {
            it[processed] = System.currentTimeMillis()
            it[quotaCost] = 10
        }

        assertQuotaEquals(35, "Quota used should not change if print job that isn't printed is added")

        // print job 2

        PrintJobs.update(job2) {
            it[printed] = System.currentTimeMillis()
        }

        assertQuotaEquals(45, "Quota used should be 35 + 10")

        // refund job 2

        PrintJobs.update(job2) {
            it[refunded] = true
        }

        assertQuotaEquals(35, "Quota used should not include refunded jobs")

        // switch user for job 1

        PrintJobs.update(job1) {
            it[shortUser] = "test30"
        }

        assertQuotaEquals(0, "Quota used should not include jobs from other users")
    }

}