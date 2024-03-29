package ca.mcgill.science.ctf.tepid.server.models

import ca.mcgill.science.ctf.tepid.server.Configs
import org.apache.logging.log4j.LogManager
import org.jetbrains.exposed.sql.Database

interface DbConfigs {
    val db: String
    val dbUser: String
    val dbPassword: String
    val dbDriver: String
}

fun DbConfigs.connect() {
    if (db.isEmpty()) throw Configs.ConfigException("No db value found in configs")
    val log = LogManager.getLogger("DbConfigs")
    log.info("Connecting to $db with $dbUser")
    Database.connect(url = db,
            driver = dbDriver,
            user = dbUser,
            password = dbPassword)
}