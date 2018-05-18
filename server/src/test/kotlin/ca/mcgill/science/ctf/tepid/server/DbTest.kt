package ca.mcgill.science.ctf.tepid.server

import ca.mcgill.science.ctf.tepid.server.internal.TestConfigs
import ca.mcgill.science.ctf.tepid.server.models.PrintJob
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.io.File

class DbTest {

    init {
        TestConfigs.setup()
    }

    @Test
    fun test() {
        transaction {
            PrintJobs.create(PrintJob("test", "test", "test", File(".")))
        }
    }

}