package eu.europa.ec.authenticationlogic.controller.appattestation

import com.nimbusds.jwt.SignedJWT
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.authenticationlogic.model.WalletWiaPopSpec
import eu.europa.ec.businesslogic.controller.log.LogController
import org.sprind.wallet.businesslogic.extensions.runSuspendCatching
import org.sprind.wallet.networklogic.walletbackend.api.WalletApiClient
import org.sprind.wallet.networklogic.walletbackend.model.request.AttestationRequest
import org.sprind.wallet.networklogic.trace.traceId
import org.sprind.wallet.networklogic.utils.getErrorCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.networklogic.common.model.ApiResult
import okio.ByteString.Companion.toByteString
import java.security.PrivateKey

sealed class WalletAttestationGenerationFromServerResult {
    data class Success(val walletInstanceAttestationSpec: WalletInstanceAttestationSpec) :
        WalletAttestationGenerationFromServerResult()

    data class Failure(val errorCode: String, val traceId: String?) :
        WalletAttestationGenerationFromServerResult()
}

interface WalletAttestationController {

    suspend fun generateAttestation(
        /** Serialized name in comms with wallet backend: wb_wi_id */
        walletInstanceId: String,
        authChallenge: String,
        mdvmToken: String,
        mdvmAuthPrvk: PrivateKey,
        walletWiaPopSpec: WalletWiaPopSpec,
    ): Flow<WalletAttestationGenerationFromServerResult>
}

class WalletAttestationControllerImpl(
    private val apiClient: WalletApiClient,
    private val logController: LogController,
) : WalletAttestationController {
    private val logTag = javaClass.simpleName

    override suspend fun generateAttestation(
        walletInstanceId: String,
        authChallenge: String,
        mdvmToken: String,
        mdvmAuthPrvk: PrivateKey,
        walletWiaPopSpec: WalletWiaPopSpec,
    ): Flow<WalletAttestationGenerationFromServerResult> = flow {
        runSuspendCatching {
            apiClient.doubleSigningApi(
                mdvmAuthPrvk = mdvmAuthPrvk,
                wiaPrvk = walletWiaPopSpec.keyPair.private,
            ).generateAttestation(
                AttestationRequest(
                    walletInstanceAttestationPublicKey = walletWiaPopSpec.x509PublicKey.encoded
                        .toByteString()
                        .base64(),
                ),
                authChallenge = authChallenge,
                mdvmToken = mdvmToken,
                walletInstanceId = walletInstanceId,
            )
        }.onSuccess { result ->
            when (result) {
                is ApiResult.Failure -> emit(
                    WalletAttestationGenerationFromServerResult.Failure(
                        errorCode = result.error.errorCode,
                        traceId = result.error.traceId
                    )
                )

                is ApiResult.Success -> {
                    val wbWiaString = result.response.walletInstanceAttestations
                    if (wbWiaString.isNotEmpty()) {
                        val walletInstanceAttestationSpec = WalletInstanceAttestationSpec(
                            wbWiaJwt = SignedJWT.parse(wbWiaString),
                            wiWiaPopKeyPair = walletWiaPopSpec.keyPair,
                            )
                        emit(WalletAttestationGenerationFromServerResult.Success(walletInstanceAttestationSpec = walletInstanceAttestationSpec))
                    } else {
                        logController.e(logTag) { "no attestation found in the response" }
                        emit(
                            WalletAttestationGenerationFromServerResult.Failure(
                                "NO_ATTESTATION",
                                traceId = null
                            )
                        ) // highly unlikely
                    }
                }
            }
        }.onFailure { exception ->
            logController.e(logTag, exception)
            emit(
                WalletAttestationGenerationFromServerResult.Failure(
                    errorCode = exception.getErrorCode(),
                    traceId = exception.traceId()
                )
            )
        }
    }
}
