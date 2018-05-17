package ca.mcgill.science.ctf.tepid.server.models

data class Error(
        val message: String,
        val timeStamp: Long = System.currentTimeMillis())