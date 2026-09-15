/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.authenticationlogic.testing

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.Certificate

/**
 * Arbitrary fixed bytes used as the default encoded form of [makeFakeCertificate].
 */
val fakeCertBytes = "fake certificate encoded form".encodeUtf8()

/**
 * Returns a minimal fake [Certificate] whose [Certificate.getEncoded] returns [encoded]
 * and whose [Certificate.getPublicKey] returns the supplied [publicKey].
 *
 * A real X.509 certificate is too onerous to construct in a unit test; this stub is
 * sufficient wherever tests only need a [Certificate] to be present in an attestation chain.
 */
fun makeFakeCertificate(
    publicKey: PublicKey,
    encoded: ByteString = fakeCertBytes,
): Certificate = object : Certificate("X.509") {
    override fun getEncoded() = encoded.toByteArray()
    override fun verify(key: PublicKey?) = Unit
    override fun verify(key: PublicKey?, sigProvider: String?) = Unit
    override fun toString() = "FakeCertificate"
    override fun getPublicKey() = publicKey
}

/**
 * Generates a new, arbitrary EC key pair on the P-256 curve, suitable for use in tests.
 */
fun generateEcKeyPairForTests(): KeyPair =
    KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
