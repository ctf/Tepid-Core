package ca.mcgill.science.ctf.tepid.server.models

sealed class Validation

object Valid : Validation()
data class Invalid(val message: String) : Validation()