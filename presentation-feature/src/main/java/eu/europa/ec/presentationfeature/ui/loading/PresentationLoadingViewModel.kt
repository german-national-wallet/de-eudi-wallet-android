/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.presentationfeature.ui.loading

import android.content.Context
import androidx.lifecycle.viewModelScope
import eu.europa.ec.authenticationlogic.controller.authentication.DeviceAuthenticationResult
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.commonfeature.ui.loading.Effect
import eu.europa.ec.commonfeature.ui.loading.Event
import eu.europa.ec.commonfeature.ui.loading.LoadingViewModel
import eu.europa.ec.corelogic.di.closePresentationScope
import eu.europa.ec.corelogic.model.AuthenticationData
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingInteractor
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingObserveResponsePartialState
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingReissuePartialState
import eu.europa.ec.presentationfeature.interactor.PresentationLoadingSendRequestedDocumentPartialState
import eu.europa.ec.presentationfeature.interactor.REISSUE_TIMEOUT
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.component.content.ContentHeaderConfig
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.config.NavigationType.PushRoute
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.IssuanceScreens
import eu.europa.ec.uilogic.navigation.PresentationScreens
import eu.europa.ec.uilogic.navigation.Screen
import eu.europa.ec.corelogic.controller.IssueDocumentsPartialState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.annotation.KoinViewModel
import java.net.URI
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@KoinViewModel
class PresentationLoadingViewModel(
    private val resourceProvider: ResourceProvider,
    private val interactor: PresentationLoadingInteractor,
    private val logController: LogController,
) : LoadingViewModel() {
    private var handledTerminalState = false

    private val logTag = javaClass.simpleName

    fun resumeOpenId4VciWithAuthorization(uri: String) {
        interactor.resumeOpenId4VciWithAuthorization(uri)
        viewModelScope.launch {
            try {
                withTimeout(RESUME_ISSUANCE_TIMEOUT_MS) {
                    interactor.issuanceState.first { response ->
                        when (response) {
                            is IssueDocumentsPartialState.Failure -> {
                                setState {
                                    copy(
                                        error = ContentErrorConfig(
                                            errorSubTitle = response.errorMessage,
                                            onCancel = {
                                                setEvent(Event.DismissError)
                                                doNavigation(
                                                    NavigationType.PopTo(
                                                        getPreviousScreen()
                                                    )
                                                )
                                            }
                                        )
                                    )
                                }
                                true
                            }

                            is IssueDocumentsPartialState.Success,
                            is IssueDocumentsPartialState.DeferredSuccess,
                            is IssueDocumentsPartialState.PartialSuccess -> {
                                true
                            }

                            else -> { false }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                setState {
                    copy(
                        error = ContentErrorConfig(
                            errorSubTitle = resourceProvider.getString(R.string.issuance_interrupted_error),
                            onCancel = {
                                setEvent(Event.DismissError)
                                doNavigation(
                                    NavigationType.PopTo(
                                        getPreviousScreen()
                                    )
                                )
                            }
                        )
                    )
                }
            }
        }
    }

    override fun getHeaderConfig(): ContentHeaderConfig {
        return ContentHeaderConfig(
            description = resourceProvider.getString(R.string.loading_header_description),
        )
    }

    override fun getPreviousScreen(): Screen {
        return PresentationScreens.PresentationRequest
    }

    override fun getCallerScreen(): Screen {
        return PresentationScreens.PresentationLoading
    }

    private fun getNextScreen(): String {
        return PresentationScreens.PresentationSuccess.screenRoute
    }

    override fun getCancellableTimeout(): Duration = 5.toDuration(DurationUnit.SECONDS)

    override fun doWork(context: Context) {
        viewModelScope.launch {
            interactor.observeResponse().collect {
                when (it) {
                    is PresentationLoadingObserveResponsePartialState.Failure -> {
                        finalizeAfterPresentationEvent(
                            context = context,
                            notifyOnAuthenticationFailure = viewState.value.notifyOnAuthenticationFailure,
                            failure = it.error,
                        )
                    }

                    is PresentationLoadingObserveResponsePartialState.Success -> {
                        finalizeAfterPresentationEvent(
                            context = context,
                            notifyOnAuthenticationFailure = viewState.value.notifyOnAuthenticationFailure,
                        )
                    }

                    is PresentationLoadingObserveResponsePartialState.Redirect -> {
                        finalizeAfterPresentationEvent(
                            context = context,
                            notifyOnAuthenticationFailure = viewState.value.notifyOnAuthenticationFailure,
                            redirectUri = it.uri,
                        )
                    }

                    is PresentationLoadingObserveResponsePartialState.RequestReadyToBeSent -> {
                        sendRequestedDocuments(Event.DoWork(context))
                    }

                    is PresentationLoadingObserveResponsePartialState.UserAuthenticationRequired -> {
                        val popEffect = Effect.Navigation.PopBackStackUpTo(
                            screenRoute = PresentationScreens.PresentationRequest.screenRoute,
                            inclusive = false
                        )

                        openAuthenticationPrompt(
                            context,
                            popEffect,
                            it.authenticationData,
                            {
                                sendRequestedDocuments(Event.DoWork(context))
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun sendRequestedDocuments(event: Event) {
        when (val result = interactor.sendRequestedDocuments()) {
            is PresentationLoadingSendRequestedDocumentPartialState.Success -> { /*no op*/
            }

            is PresentationLoadingSendRequestedDocumentPartialState.Failure -> {
                setState {
                    copy(
                        error = ContentErrorConfig(
                            onRetry = { setEvent(event) },
                            errorSubTitle = result.error,
                            onCancel = {
                                setEvent(Event.DismissError)
                                doNavigation(
                                    NavigationType.PopTo(
                                        getPreviousScreen()
                                    )
                                )
                            }
                        )
                    )
                }
            }
        }
    }
    private fun openAuthenticationPrompt(
        context: Context,
        popEffect: Effect,
        authenticationDataList: List<AuthenticationData>,
        sendRequestedDocumentsAction: suspend () -> Unit,
        index: Int = 0,
    ) {
        val authenticationData = authenticationDataList[index]
        val isFinalAuthentication = index == authenticationDataList.lastIndex
        interactor.handleUserAuthentication(
            context = context,
            crypto = authenticationData.crypto,
            notifyOnAuthenticationFailure = viewState.value.notifyOnAuthenticationFailure,
            resultHandler = DeviceAuthenticationResult(
                onAuthenticationSuccess = {
                    authenticationData.onAuthenticationSuccess()
                    if (isFinalAuthentication) {
                        sendRequestedDocumentsAction()
                    } else {
                        delay(500)
                        openAuthenticationPrompt(
                            context,
                            popEffect,
                            authenticationDataList,
                            sendRequestedDocumentsAction,
                            index + 1
                        )
                    }
                },
                onAuthenticationError = { setEffect { popEffect } }
            )
        )
    }

    private fun onSuccess() {
        setState {
            copy(
                error = null
            )
        }
        doNavigation(PushRoute(getNextScreen()))
    }

    private fun onRedirect(uri: URI) {
        setState {
            copy(
                error = null
            )
        }
        cleanUpPresentation()

        val initiator = interactor.initiatorRoute
        val isDynamicIssuance = initiator == IssuanceScreens.DocumentOffer.screenRoute
            || initiator == IssuanceScreens.AddDocument.screenRoute

        doNavigation(
            NavigationType.Deeplink(
                link = uri.toString(),
                routeToPop = null
            )
        )
        if (isDynamicIssuance) {
            val initiatorScreen = when (initiator) {
                IssuanceScreens.DocumentOffer.screenRoute -> IssuanceScreens.DocumentOffer
                IssuanceScreens.AddDocument.screenRoute -> IssuanceScreens.AddDocument
                else -> null
            }
            if (initiatorScreen != null) {
                doNavigation(NavigationType.PopTo(initiatorScreen))
            } else {
                doNavigation(PushRoute(DashboardScreens.Dashboard.screenRoute))
            }
        } else {
            doNavigation(PushRoute(DashboardScreens.Dashboard.screenRoute))
        }
    }

    private suspend fun reissueIfNeeded(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
    ): String? {
        var failureMessage: String? = null
        interactor.reissueLowBatchDocuments()
            .collect { reissueState ->
                when (reissueState) {
                    is PresentationLoadingReissuePartialState.UserAuthenticationRequired -> {
                        interactor.handleUserAuthentication(
                            context = context,
                            crypto = reissueState.crypto,
                            notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                            resultHandler = reissueState.resultHandler
                        )
                    }

                    is PresentationLoadingReissuePartialState.Failure -> {
                        failureMessage = reissueState.error
                    }

                    PresentationLoadingReissuePartialState.NotNeeded,
                    PresentationLoadingReissuePartialState.Success -> Unit
                }
            }
        return failureMessage
    }

    private fun finalizeAfterPresentationEvent(
        context: Context,
        notifyOnAuthenticationFailure: Boolean,
        redirectUri: URI? = null,
        failure: String? = null,
    ) {
        // observeSentDocumentsRequest() can surface more than one terminal state (e.g. a
        // ResponseSent Success followed by a Redirect); only the first one drives the flow.
        if (handledTerminalState) return
        handledTerminalState = true

        // A failed presentation presented nothing, so there is no batch to refresh: just tear the
        // presentation down and go back.
        if (failure != null) {
            logController.d(logTag) { failure }
            cleanUpPresentation()
            doNavigation(NavigationType.PopTo(getPreviousScreen()))
            return
        }

        // The refresh runs before navigation, as on the request screen: onRedirect() below tears
        // down the presentation and clears the rWSCA PIN session that RwscaSecureArea.sign() needs,
        // and a refresh reached from here can raise its own biometric prompt, which would be broken
        // UX after the user has navigated away. A failure is only logged — it must never disrupt an
        // already-completed presentation nor send the user back.
        viewModelScope.launch {
            try {
                var reissueFailure: String? = null
                val completed = withTimeoutOrNull(REISSUE_TIMEOUT) {
                    reissueFailure = reissueIfNeeded(
                        context = context,
                        notifyOnAuthenticationFailure = notifyOnAuthenticationFailure,
                    )
                }
                if (completed == null) {
                    logController.d(logTag) { "Batch refresh timed out after $REISSUE_TIMEOUT" }
                } else {
                    reissueFailure?.let { failure -> logController.d(logTag) { failure } }
                }
            } catch (e: CancellationException) {
                // The user left via the cancel affordance mid-refresh, so nothing below will
                // navigate. Tear down here or the next presentation inherits this one's controller,
                // replayed terminal events and all.
                cleanUpPresentation()
                throw e
            } catch (e: Exception) {
                // The refresh performs a network WIA call that can throw; it must never strand the
                // already-completed presentation on the loading spinner, so navigation still runs.
                logController.d(logTag) { "Batch refresh threw: ${e.localizedMessage}" }
            }

            if (redirectUri != null) {
                onRedirect(redirectUri)
            } else {
                onSuccess()
            }
        }
    }

    private fun cleanUpPresentation() {
        interactor.stopPresentation()
        closePresentationScope()
    }
}

private const val RESUME_ISSUANCE_TIMEOUT_MS = 5_000L
