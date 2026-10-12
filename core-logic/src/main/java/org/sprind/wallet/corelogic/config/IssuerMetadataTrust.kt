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
package org.sprind.wallet.corelogic.config

import android.content.Context
import androidx.annotation.RawRes
import eu.europa.ec.eudi.openid4vci.CertificateChainTrust
import eu.europa.ec.eudi.openid4vci.IssuerMetadataPolicy
import eu.europa.ec.eudi.wallet.trust.X509CertificateChainTrust
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Requires every credential issuer to serve signed metadata (`openidvci-issuer-metadata+jwt`) whose
 * `x5c` chain ends in the flavor's [issuerMetadataTrustAnchors]. A flavor without anchors trusts no
 * issuer, so issuance fails there until anchors are bundled.
 *
 * The anchors are an interim stand-in for the ETSI LoTE: the root of the metadata `x5c` chain is
 * meant to be validated against the LoTE, which is not available yet. Do not add anchors to make a
 * new issuer pass; once the LoTE exists, replace them with a LoTE-backed `CertificateChainTrust`
 * here. Issuers send only their leaf in `x5c`, never the root, so the root must come from the
 * anchors (or the LoTE), not from the issuer.
 *
 * The anchors are read on the first check rather than while the issuer configs are built.
 * Revocation is not checked, as for reader authentication: the platform PKIX validator does not
 * fetch the CRLs these chains point to, so every chain would be rejected.
 */
internal fun requireSignedIssuerMetadata(context: Context): IssuerMetadataPolicy {
    if (issuerMetadataTrustAnchors.isEmpty()) {
        return IssuerMetadataPolicy.RequireSigned(CertificateChainTrust { false })
    }
    val trust by lazy {
        X509CertificateChainTrust(
            trustAnchors = issuerMetadataTrustAnchors.map { context.readCertificate(it) },
            checkRevocation = false,
        )
    }
    return IssuerMetadataPolicy.RequireSigned(CertificateChainTrust { chain -> trust.isTrusted(chain) })
}

private fun Context.readCertificate(@RawRes resId: Int): X509Certificate =
    resources.openRawResource(resId).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }