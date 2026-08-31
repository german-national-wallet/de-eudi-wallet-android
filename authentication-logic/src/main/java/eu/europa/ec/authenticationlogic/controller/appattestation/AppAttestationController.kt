package eu.europa.ec.authenticationlogic.controller.appattestation

import eu.europa.ec.authenticationlogic.controller.storage.RegistrationData
import eu.europa.ec.authenticationlogic.controller.storage.WalletRegistrationStorageController
import eu.europa.ec.authenticationlogic.jwt.JwtBuilder
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import eu.europa.ec.authenticationlogic.model.WalletWiaPopSpec
import eu.europa.ec.businesslogic.controller.log.LogController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContext
import org.sprind.wallet.authenticationlogic.provider.MdvmAuthContextProvider
import org.sprind.wallet.networklogic.common.model.ApiResult
import java.security.PrivateKey


sealed class WalletAttestationGenerationResult {
    data class Success(val walletInstanceAttestationSpec: WalletInstanceAttestationSpec) :
        WalletAttestationGenerationResult()

    data class Failure(val errorCode: String, val traceId: String?) :
        WalletAttestationGenerationResult()
}

/**
 * Controller for registering the wallet with the app attestation and generating attestation from server
 * */
interface AppAttestationController {

    /**
     * Registers the wallet by getting challenge from the server, creating instance PoP Jwt,
     * and requesting wallet instance id from the server
     * */
    fun registerWallet(): Flow<WalletRegistrationPartialState>

    /**
     * Generates attestation on server and stores it locally
     * */
    fun generateAttestation(): Flow<WalletAttestationGenerationResult>
}

class AppAttestationControllerImpl(
    private val walletRegistrationController: WalletRegistrationController,
    private val jwtBuilder: JwtBuilder,
    private val walletRegistrationStorageController: WalletRegistrationStorageController,
    private val mdvmAuthContextProvider: MdvmAuthContextProvider,
    private val walletAttestationController: WalletAttestationController,
    private val logController: LogController,
) : AppAttestationController {
    private val logTag = javaClass.simpleName

    override fun registerWallet(): Flow<WalletRegistrationPartialState> = flow {
        walletRegistrationController.getChallenge()
            .collect { challengeResult ->
                when (challengeResult) {
                    is RegistrationChallengePartialState.Failure -> {
                        logController.d(logTag) { "registerWallet, challenge error : ${challengeResult.errorCode}" }
                        emit(
                            WalletRegistrationPartialState.Failure(
                                errorCode = challengeResult.errorCode,
                                traceId = challengeResult.traceId
                            )
                        )
                    }

                    is RegistrationChallengePartialState.Success -> {
                        when (val mdvmAuthContextResult = mdvmAuthContextProvider.getMdvmAuthContext()) {
                            is ApiResult.Failure -> {
                                emit(
                                    WalletRegistrationPartialState.Failure(
                                        errorCode = mdvmAuthContextResult.error.code,
                                        traceId = mdvmAuthContextResult.error.traceId
                                    )
                                )
                            }

                            is ApiResult.Success -> {
                                requestAndSaveWalletInstanceId(
                                    authChallenge = challengeResult.challenge,
                                    mdvmAuthContext = mdvmAuthContextResult.response,
                                ).collect { instanceRequestResult ->
                                    when (instanceRequestResult) {
                                        is WalletInstancePartialState.Success ->
                                            emit(WalletRegistrationPartialState.Success)

                                        is WalletInstancePartialState.Failure ->
                                            emit(
                                                WalletRegistrationPartialState.Failure(
                                                    errorCode = instanceRequestResult.errorCode,
                                                    traceId = instanceRequestResult.traceId
                                                )
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
    private fun requestAndSaveWalletInstanceId(
        authChallenge: String,
        mdvmAuthContext: MdvmAuthContext,
    ): Flow<WalletInstancePartialState> =
        flow {
            walletRegistrationController.getWalletInstanceId(
                authChallenge = authChallenge,
                mdvmToken = mdvmAuthContext.mdvmToken,
                mdvmAuthPrvk = mdvmAuthContext.mdvmAuthPrvk,
            ).collect {
                when (it) {
                    is WalletInstancePartialState.Success ->
                        walletRegistrationStorageController.saveWalletRegistration(
                            RegistrationData(
                                walletInstanceId = it.walletInstanceId,
                                walletInstanceRevocationCode = it.walletInstanceRevocationCode,
                            )
                        )

                    is WalletInstancePartialState.Failure -> logController.d(logTag) { "requestAndSaveWalletInstanceId, walletRegistrationController.getWalletInstanceId returns error" }
                }
                emit(it)
            }
        }

    override fun generateAttestation(): Flow<WalletAttestationGenerationResult> =
        flow {
            val registrationData = walletRegistrationStorageController.getWalletRegistration()
            if (registrationData == null) {
                logController.e(logTag) { "generateAttestation , registration data from storage is null" }
                emit(
                    WalletAttestationGenerationResult.Failure(
                        errorCode = "WB_ACCOUNT_UNKNOWN",
                        traceId = null
                    )
                )
                return@flow
            }

            walletRegistrationController.getChallenge()
                .collect { result ->
                    when (result) {
                        is RegistrationChallengePartialState.Failure -> {
                            logController.e(logTag) { "generateAttestation , challenge error from server ${result.errorCode}" }
                            emit(
                                WalletAttestationGenerationResult.Failure(
                                    errorCode = result.errorCode,
                                    traceId = result.traceId
                                )
                            )
                        }

                        is RegistrationChallengePartialState.Success -> {
                            val mdvmAuthContext = when (val mdvmAuthContextResult =
                                mdvmAuthContextProvider.getMdvmAuthContext()) {
                                is ApiResult.Failure -> {
                                    emit(
                                        WalletAttestationGenerationResult.Failure(
                                            errorCode = mdvmAuthContextResult.error.code,
                                            traceId = mdvmAuthContextResult.error.traceId
                                        )
                                    )
                                    return@collect
                                }

                                is ApiResult.Success -> mdvmAuthContextResult.response
                            }

                            val attestation = prepareAttestation(
                                challenge = result.challenge,
                                walletInstanceId = registrationData.walletInstanceId,
                                mdvmAuthContext = mdvmAuthContext,
                            )

                            generateWalletInstanceAttestation(attestation).collect(::emit)
                        }
                    }
                }
        }

    private fun prepareAttestation(
        challenge: String,
        walletInstanceId: String,
        mdvmAuthContext: MdvmAuthContext,
    ): Attestation {
        val walletWiaPopSpec = jwtBuilder.createOAuthClientAttestationJwt(challenge)

        return Attestation(
            walletInstanceId = walletInstanceId,
            authChallenge = challenge,
            mdvmToken = mdvmAuthContext.mdvmToken,
            mdvmAuthPrvk = mdvmAuthContext.mdvmAuthPrvk,
            walletWiaPopSpec = walletWiaPopSpec,
        )
    }

    private fun generateWalletInstanceAttestation(
        attestation: Attestation,
    ): Flow<WalletAttestationGenerationResult> = flow {
        walletAttestationController.generateAttestation(
            walletInstanceId = attestation.walletInstanceId,
            authChallenge = attestation.authChallenge,
            mdvmToken = attestation.mdvmToken,
            mdvmAuthPrvk = attestation.mdvmAuthPrvk,
            walletWiaPopSpec = attestation.walletWiaPopSpec,
        ).collect {
            when (it) {
                is WalletAttestationGenerationFromServerResult.Failure -> {
                    logController.e(logTag) { "generateWalletInstanceAttestation , attestation error ${it.errorCode}" }
                    emit(
                        WalletAttestationGenerationResult.Failure(
                            errorCode = it.errorCode,
                            traceId = it.traceId
                        )
                    )
                }

                is WalletAttestationGenerationFromServerResult.Success -> {
                    logController.d(logTag) { "generateWalletInstanceAttestation , attestation ${it.walletInstanceAttestationSpec}" }
                    emit(WalletAttestationGenerationResult.Success(it.walletInstanceAttestationSpec))
                }
            }
        }
    }
}

private data class Attestation(
    /** Serialized name in comms with wallet backend: wb_wi_id */
    val walletInstanceId: String,
    val authChallenge: String,
    val mdvmToken: String,
    val mdvmAuthPrvk: PrivateKey,
    /** spec (JWT + signing key) for element of wi_wia_pop_list */
    val walletWiaPopSpec: WalletWiaPopSpec,
)
