package ca.mcgill.science.ctf.tepid.server.models

import java.io.File

sealed class PrintResponse

data class PrintJob(
        /**
         * Unique id for the job
         */
        val id: String,
        /**
         * Name of the print job
         * Does not have to be unique
         */
        val name: String,
        /**
         * Unique identifier for the destination
         */
        val destination: String,
        /**
         * Unique identifier for the user who is printing
         */
        val shortUser: String,
        /**
         * Processed postscript file
         */
        val file: File) : PrintResponse()

data class PrintError(
        val message: String,
        val timeStamp: Long = System.currentTimeMillis()):PrintResponse()