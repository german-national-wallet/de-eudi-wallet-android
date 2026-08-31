package eu.europa.ec.authenticationlogic.jwt

import java.time.Instant

class InstantProvider {
    fun getCurrentInstant(): Instant = Instant.now()
}