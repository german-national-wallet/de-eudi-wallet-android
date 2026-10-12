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

package org.sprind.wallet.corelogic.extension

import eu.europa.ec.corelogic.extension.toSelectivelyDisclosableClaimPaths
import eu.europa.ec.corelogic.model.ClaimPathDomain
import eu.europa.ec.eudi.sdjwt.vc.ClaimPathElement
import eu.europa.ec.eudi.wallet.document.format.MsoMdocClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcClaim
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcData
import eu.europa.ec.eudi.wallet.document.format.SdJwtVcFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class SelectivelyDisclosableClaimPathsTest {

    private fun claim(
        name: String,
        sd: Boolean,
        value: Any? = "v",
        children: List<SdJwtVcClaim> = emptyList(),
    ) = SdJwtVcClaim(
        pathElement = ClaimPathElement.Claim(name),
        value = value,
        rawValue = value.toString(),
        issuerMetadata = null,
        selectivelyDisclosable = sd,
        children = children,
    )

    private fun element(index: Int, sd: Boolean) = SdJwtVcClaim(
        pathElement = ClaimPathElement.ArrayElement(index),
        value = "DE",
        rawValue = "DE",
        issuerMetadata = null,
        selectivelyDisclosable = sd,
        children = emptyList(),
    )

    private val pid = listOf(
        claim("family_name", sd = true),
        claim("given_name", sd = true),
        claim(
            "address", sd = true, value = mapOf<String, Any>(), children = listOf(
                claim("street_address", sd = true),
                claim("locality", sd = true),
            )
        ),
        claim(
            "age_equal_or_over", sd = false, value = mapOf<String, Any>(), children = listOf(
                claim("18", sd = true),
                claim("21", sd = true),
            )
        ),
        claim(
            "nationalities", sd = false, value = listOf("DE"),
            children = listOf(element(0, sd = true))
        ),
        claim("iat", sd = false),
        claim("exp", sd = false),
    )

    @Test
    fun `plain claims are not counted as selectively disclosable`() {
        assertEquals(7, pid.sumOf { it.toSelectivelyDisclosableClaimPaths().size })
    }

    @Test
    fun `nested disclosable leaves are kept under a plain parent`() {
        assertEquals(
            listOf(
                ClaimPathDomain(listOf("age_equal_or_over", "18")),
                ClaimPathDomain(listOf("age_equal_or_over", "21")),
            ),
            pid[3].toSelectivelyDisclosableClaimPaths(),
        )
    }

    @Test
    fun `plain array with disclosable elements counts as one leaf`() {
        assertEquals(
            listOf(ClaimPathDomain(listOf("nationalities"))),
            pid[4].toSelectivelyDisclosableClaimPaths(),
        )
    }

    @Test
    fun `JWT and key binding claims of a real SD-JWT are not counted`() {
        val data = sdJwtVcData(listOf(disclosure("given_name", "\"ERIKA\"")))

        assertEquals(
            listOf(ClaimPathDomain(listOf("given_name"))),
            data.claims.flatMap { it.toSelectivelyDisclosableClaimPaths() },
        )
    }

    @Test
    fun `every mdoc data element counts, keyed by its namespace`() {
        val claims = listOf("org.iso.18013.5.1", "org.iso.18013.5.1.aamva").map { nameSpace ->
            MsoMdocClaim(
                nameSpace = nameSpace,
                dataElementName = "family_name",
                value = "MUSTERMANN",
                rawValue = byteArrayOf(),
                issuerMetadata = null,
            )
        }

        assertEquals(
            listOf(
                ClaimPathDomain(listOf("org.iso.18013.5.1", "family_name")),
                ClaimPathDomain(listOf("org.iso.18013.5.1.aamva", "family_name")),
            ),
            claims.flatMap { it.toSelectivelyDisclosableClaimPaths() }.distinct(),
        )
    }

    @Test
    fun `leaf count does not depend on how the issuer splits disclosures`() {
        val fields = listOf(
            "street_address" to "HEIDESTRASSE 17",
            "locality" to "KOELN",
            "postal_code" to "51147",
            "country" to "DE",
            "region" to "",
        )

        val whole = disclosure(
            "address",
            fields.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"$v\"" },
        )
        val perField = fields.map { (k, v) -> disclosure(k, "\"$v\"") }
        val container = disclosure(
            "address",
            perField.joinToString(",", "{\"_sd\":[", "]}") { "\"${digest(it)}\"" },
        )

        val oneDisclosure = sdJwtVcData(listOf(whole))
        val sixDisclosures = sdJwtVcData(perField + container)

        assertEquals(5, oneDisclosure.claims.sumOf { it.toSelectivelyDisclosableClaimPaths().size })
        assertEquals(5, sixDisclosures.claims.sumOf { it.toSelectivelyDisclosableClaimPaths().size })
    }

    private fun disclosure(name: String, jsonValue: String): String =
        base64Url("[\"salt-$name\",\"$name\",$jsonValue]".toByteArray())

    private fun digest(disclosure: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(disclosure.toByteArray()))

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /**
     * An unsigned test SD-JWT whose payload references the last of [disclosures]; the signature
     * is not checked by `unverifiedIssuanceFrom`.
     */
    private fun sdJwtVcData(disclosures: List<String>): SdJwtVcData {
        val header = """{"alg":"ES256","typ":"dc+sd-jwt"}"""
        val payload = """{"_sd":["${digest(disclosures.last())}"],"_sd_alg":"sha-256",""" +
            """"iss":"https://issuer.example","vct":"urn:eudi:pid:de:1",""" +
            """"iat":1790719200,"nbf":1790719200,"exp":1791928800,""" +
            """"cnf":{"jwk":{"kty":"EC","crv":"P-256","x":"x","y":"y"}},""" +
            """"status":{"status_list":{"uri":"https://issuer.example/status","idx":1}}}"""
        val jwt = listOf(header, payload, "signature")
            .joinToString(".") { base64Url(it.toByteArray()) }
        return SdJwtVcData(
            format = SdJwtVcFormat(vct = "urn:eudi:pid:de:1"),
            issuerMetadata = null,
            sdJwtVc = disclosures.joinToString("~", "$jwt~", "~"),
        )
    }
}
