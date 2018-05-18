package ca.mcgill.science.ctf.tepid.server.internal

import ca.mcgill.science.ctf.tepid.server.Tepid
import ca.mcgill.science.ctf.tepid.server.models.DbConfigs
import ca.mcgill.science.ctf.tepid.server.tables.PrintJobs
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.drop
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.atomic.AtomicBoolean


object TestConfigs {

    private val isSetUp = AtomicBoolean(false)

    fun setup() {
        if (isSetUp.getAndSet(true)) return
        val source = JdbcDataSource().apply {
            setURL("jdbc:h2:~/tepid-test");
            user = "tepid";
            password = "test";
        }
        Tepid.configure {
            dbConfigs = object : DbConfigs {
                override val db: String = source.getUrl()
                override val dbUser: String = source.user
                override val dbPassword: String = source.password
                override val dbDriver: String = "org.h2.Driver"
            }

            baseQuota = { 1000 }
        }
        transaction {
            drop(PrintJobs)
            create(PrintJobs)
        }
    }

    fun clear() = transaction {
        drop(PrintJobs)
    }

}