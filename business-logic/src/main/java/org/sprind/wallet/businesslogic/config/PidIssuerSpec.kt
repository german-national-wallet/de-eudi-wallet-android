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

package org.sprind.wallet.businesslogic.config

/** Pattern and pins suitable for passing to [okhttp3.CertificatePinner.Builder.add] */
data class OkCertificatePinnerSpec(
    /** lower-case host name or wildcard pattern such as *.example.com. */
    val pattern: String,
    /**
     * SHA-256 or SHA-1 hashes. Each pin is a hash of a certificate's Subject Public Key Info, base64-encoded and prefixed with either sha256/ or sha1/.
     */
    val pins: Set<String>,
)

data class PidIssuerSpec(
    /* VCI Issuer endpoint */
    val url: String,
    val okCertificatePinnerSpec: OkCertificatePinnerSpec,
) {
    companion object {

/*
Command line used to obtain the pins - the root and intermediate CA is the same for both hostnames:

for HOSTNAME in demo.pid-provider.bundesdruckerei.de preprod.pid-provider.bundesdruckerei.de; do
     echo "=== $HOSTNAME ==="
     rm -f /tmp/tls_cert_*
     echo | openssl s_client -connect $HOSTNAME:443 -showcerts 2>/dev/null > /tmp/tls_chain.pem
     csplit -s -f /tmp/tls_cert_ -z /tmp/tls_chain.pem '/-----BEGIN CERTIFICATE-----/' '{*}' 2>/dev/null || true
     for f in /tmp/tls_cert_*; do
       subject=$(openssl x509 -in "$f" -noout -subject 2>/dev/null | sed 's/subject=//')
       spki=$(openssl x509 -in "$f" -pubkey -noout 2>/dev/null | openssl pkey -pubin -outform der 2>/dev/null | openssl dgst -sha256 -binary | base64)
       [ -n "$subject" ] && echo "$subject" && echo "  sha256/$spki"
     done
     echo
   done

Output on 2026-05-06:
=== demo.pid-provider.bundesdruckerei.de ===
C=DE, ST=Berlin, O=Bundesdruckerei GmbH, CN=demo.pid-provider.bundesdruckerei.de
  sha256/KAXZ6bbYNqA/YpMkqwkJ6g+RjF4nNTsN4ij+dag/Fl4=
C=DE, O=D-Trust GmbH, CN=D-TRUST BR CA 2-23-1 2023
  sha256/qCFzgdYsy2/WiDGJebSIbgJzqSxZ8bAPEkUKn+bgGmc=
C=DE, O=D-Trust GmbH, CN=D-TRUST BR Root CA 2 2023
  sha256/rHb2OkbnYbWswyWXBckgy38FY9JI2NGA+TSvaAmaFfk=

=== preprod.pid-provider.bundesdruckerei.de ===
C=DE, ST=Berlin, O=Bundesdruckerei GmbH, CN=preprod.pid-provider.bundesdruckerei.de
  sha256/GXiMxsrFMbmKzuy8S+NefJ8Nxq3gu9NvQZqqQtFvr5A=
C=DE, O=D-Trust GmbH, CN=D-TRUST BR CA 2-23-1 2023
  sha256/qCFzgdYsy2/WiDGJebSIbgJzqSxZ8bAPEkUKn+bgGmc=
C=DE, O=D-Trust GmbH, CN=D-TRUST BR Root CA 2 2023
  sha256/rHb2OkbnYbWswyWXBckgy38FY9JI2NGA+TSvaAmaFfk=
 */

        val DEMO = PidIssuerSpec(
            url = "https://demo.pid-provider.bundesdruckerei.de",
            okCertificatePinnerSpec = OkCertificatePinnerSpec(
                // Unlike *.foo, **.foo matches both foo and a.b.foo
                pattern = "**.pid-provider.bundesdruckerei.de",
                // root CA from the chain from above
                pins = setOf("sha256/rHb2OkbnYbWswyWXBckgy38FY9JI2NGA+TSvaAmaFfk=")
            )
        )

        val PREPROD = PidIssuerSpec(
            url = "https://preprod.pid-provider.bundesdruckerei.de",
            okCertificatePinnerSpec = OkCertificatePinnerSpec(
                pattern = "**.pid-provider.bundesdruckerei.de",
                // root CA from the chain from above
                pins = setOf("sha256/rHb2OkbnYbWswyWXBckgy38FY9JI2NGA+TSvaAmaFfk=")
            )
        )
    }
}
