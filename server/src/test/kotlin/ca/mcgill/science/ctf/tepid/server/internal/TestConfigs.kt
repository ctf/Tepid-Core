package ca.mcgill.science.ctf.tepid.server.internal

import ca.mcgill.science.ctf.tepid.server.Tepid
import ca.mcgill.science.ctf.tepid.server.models.DbConfigs
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicBoolean


object TestConfigs {

    private val isSetUp = AtomicBoolean(false)

    private val testQuota = Regex("test([0-9]+)")

    /**
     * Call before each method
     * Initializes everything and resets the databases
     */
    fun setup() {
        if (isSetUp.getAndSet(true)) return
        val source = JdbcDataSource().apply {
            setURL("jdbc:h2:~/tepid-test")
            user = "tepid"
            password = "test"
        }
        Tepid.configure {
            dbConfigs = object : DbConfigs {
                override val db: String = source.getUrl()
                override val dbUser: String = source.user
                override val dbPassword: String = source.password
                override val dbDriver: String = "org.h2.Driver"
            }

            baseQuota = {
                testQuota.matchEntire(it)
                        ?.groupValues
                        ?.get(1)
                        ?.toInt() ?: 1000
            }
        }
        testTransaction { }
    }

}