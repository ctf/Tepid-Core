package ca.mcgill.science.ctf.tepid.server.models

sealed class Validation

object Valid : Validation()
data class Invalid(val message: String) : Validation()

typealias Validator<T> = (data: T) -> Validation

/**
 * Returns a validator that goes through all provided [validators]
 */
fun <T> validate(vararg validators: Validator<T>): Validator<T> = {
    validate(it, *validators)
}

/**
 * Given [data] return the first Invalid result, or Valid if everything goes through
 */
fun <T> validate(data: T, vararg validators: Validator<T>): Validation {
    validators.forEach {
        val result = it(data)
        if (result is Invalid)
            return result
    }
    return Valid
}