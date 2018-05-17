package ca.mcgill.science.ctf.tepid.server.internal

import ca.mcgill.science.ctf.tepid.server.Tepid
import ca.mcgill.science.ctf.tepid.server.models.DbConfigs
import java.util.concurrent.atomic.AtomicBoolean

object Test {

    private val isSetUp = AtomicBoolean(false)

    fun setup() {
        if (isSetUp.getAndSet(true)) return
        Tepid.configure {
            dbConfigs = object : DbConfigs {
                override val db: String
                    get() = TODO("not implemented")
                override val dbUser: String
                    get() = TODO("not implemented")
                override val dbPassword: String
                    get() = TODO("not implemented")
                override val dbDriver: String
                    get() = TODO("not implemented")

            }
        }
    }

}