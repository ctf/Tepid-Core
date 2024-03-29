package ca.mcgill.science.ctf.tepid.server.utils

import java.io.File
import java.io.InputStream
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

internal object Utils {
    internal val secureRandom = SecureRandom()
}

/**
 * Copies the given [input] to the file, and ensure that
 * the input stream is closed
 *
 * By default, we will clear the current file before the copy
 */
fun File.copyFrom(input: InputStream,
                  vararg options: CopyOption = arrayOf(StandardCopyOption.REPLACE_EXISTING)) {
    input.use {
        Files.copy(it, toPath(), *options)
    }
}

// Exception from which all other tepid exceptions inherit
open class TepidException(override val message: String) : RuntimeException(message)