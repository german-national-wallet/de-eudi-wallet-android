package eu.europa.ec.corelogic.interactor.walletattestation

import eu.europa.ec.authenticationlogic.controller.appattestation.AppAttestationController
import eu.europa.ec.authenticationlogic.controller.appattestation.WalletAttestationGenerationResult
import eu.europa.ec.authenticationlogic.model.WalletInstanceAttestationSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class WalletAttestationResult {
    data class Success(val walletInstanceAttestationSpec: WalletInstanceAttestationSpec) : WalletAttestationResult()
    data class Failure(val errorCode: String, val traceId: String?) : WalletAttestationResult()
}

interface WalletAttestationInteractor {
    /**
     * Generates attestation
     */
    suspend fun generateAttestation(): Flow<WalletAttestationResult>
}

internal class WalletAttestationInteractorImpl(
    private val appAttestationController: AppAttestationController
) : WalletAttestationInteractor {

    override suspend fun generateAttestation(): Flow<WalletAttestationResult> =
        appAttestationController.generateAttestation().map { attestationResult ->
            when (attestationResult) {
                is WalletAttestationGenerationResult.Failure ->
                    WalletAttestationResult.Failure(errorCode = attestationResult.errorCode, traceId = attestationResult.traceId)

                is WalletAttestationGenerationResult.Success ->
                    WalletAttestationResult.Success(walletInstanceAttestationSpec = attestationResult.walletInstanceAttestationSpec)
        }
    }
}