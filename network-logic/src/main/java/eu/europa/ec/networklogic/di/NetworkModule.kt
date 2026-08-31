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

package eu.europa.ec.networklogic.di

import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.interceptor.HttpTelemetryInterceptor
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.networklogic.BuildConfig
import eu.europa.ec.networklogic.repository.WalletAttestationRepository
import eu.europa.ec.networklogic.repository.WalletAttestationRepositoryImpl
import org.sprind.wallet.networklogic.common.HeaderInterceptor
import org.sprind.wallet.networklogic.common.HttpMessageSigningApiFactory
import org.sprind.wallet.networklogic.walletbackend.api.WalletApi
import org.sprind.wallet.networklogic.walletbackend.api.WalletApiClient
import org.sprind.wallet.networklogic.walletbackend.api.WalletApiClientImpl
import org.sprind.wallet.networklogic.utils.NetworkLogInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single
import org.sprind.wallet.networklogic.mdvm.api.MdvmApi
import org.sprind.wallet.networklogic.mdvm.api.MdvmApiClient
import org.sprind.wallet.networklogic.mdvm.api.MdvmApiClientImpl
import org.sprind.wallet.networklogic.pushnotifications.api.PushNotificationsApi
import org.sprind.wallet.networklogic.pushnotifications.api.PushNotificationsApiClient
import org.sprind.wallet.networklogic.pushnotifications.api.PushNotificationsApiClientImpl
import org.sprind.wallet.networklogic.rwsca.api.RwscaApi
import org.sprind.wallet.networklogic.rwsca.api.RwscaApiClient
import org.sprind.wallet.networklogic.rwsca.api.RwscaApiClientImpl
import org.sprind.wallet.networklogic.trace.TraceContextInterceptor
import org.sprind.wallet.networklogic.utils.toCertificatePinner
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

@Module
@ComponentScan("eu.europa.ec.networklogic", "org.sprind.wallet.networklogic")
class LogicNetworkModule

@Single
fun provideWalletApi(retrofit: Retrofit): WalletApi = retrofit.create(WalletApi::class.java)


@Factory
fun provideConverterFactory(): GsonConverterFactory = GsonConverterFactory.create()

@Single
internal fun provideWalletApiClient(
    walletApi: WalletApi,
    signingApiFactory: HttpMessageSigningApiFactory,
): WalletApiClient = WalletApiClientImpl(
    walletApiService = walletApi,
    signingApiFactory = signingApiFactory,
)

@Factory
internal fun provideHttpMessageSigningApiFactory(
    @WalletBackend walletBackendOkHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    clock: Clock,
): HttpMessageSigningApiFactory = HttpMessageSigningApiFactory(
    baseClient = walletBackendOkHttpClient,
    baseRetrofit = baseRetrofit,
    clock = clock,
)

@Single
fun provideNetworkLogInterceptor(logController: LogController): NetworkLogInterceptor =
    NetworkLogInterceptor(logController = logController)

@Single
internal fun provideTraceContextInterceptor(telemetry: Telemetry): TraceContextInterceptor =
    TraceContextInterceptor(telemetry = telemetry)

/**
 * A base OkHttpClient instance from which other instances can derive to
 * share the same connection pool, cache, and any global interceptors, as
 * appropriate.
 */
@Single
internal fun provideBaseOkHttpClient(
    configLogic: ConfigLogic,
    httpTelemetryInterceptor: HttpTelemetryInterceptor,
    networkLogInterceptor: NetworkLogInterceptor,
    traceContextInterceptor: TraceContextInterceptor,
): OkHttpClient = OkHttpClient.Builder()
    .readTimeout(configLogic.environmentConfig.readTimeoutSeconds, TimeUnit.SECONDS)
    .connectTimeout(configLogic.environmentConfig.connectTimeoutSeconds, TimeUnit.SECONDS)
    // Added first so it stays outermost and observes failures thrown by the interceptors below,
    // including connection failures that never reach a network interceptor.
    .addInterceptor(traceContextInterceptor)
    .addInterceptor(networkLogInterceptor)
    .addNetworkInterceptor(httpTelemetryInterceptor)
    .build()

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenId4VciVp

@Single
@OpenId4VciVp
internal fun provideOpenid4VciVpOkHttpClient(
    baseOkHttpClient: OkHttpClient,
    configLogic: ConfigLogic,
): OkHttpClient {
    return baseOkHttpClient.newBuilder()
        .certificatePinner(configLogic.environmentConfig.pidIssuerSpec.okCertificatePinnerSpec.toCertificatePinner())
        .build()
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DoNotFollowRedirects

@Single
@DoNotFollowRedirects
internal fun provideDoNotFollowRedirectsOkHttpClient(baseClient: OkHttpClient): OkHttpClient =
    baseClient.newBuilder()
        .followRedirects(false)
        .build()

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WalletBackend

/**
 * An [OkHttpClient] suitable for talking to Wallet Backend (adds required X-Auth-Token header).
 */
@Single
@WalletBackend
internal fun provideWalletBackendOkHttpClient(
    baseClient: OkHttpClient,
    configLogic: ConfigLogic,
): OkHttpClient = baseClient
    .newBuilder() // share cache, connection pool, global interceptors etc. with baseClient
    .addInterceptor(HeaderInterceptor("X-Auth-Token", configLogic.environmentConfig.WALLET_AUTH_TOKEN))
    .build()

/**
 * Retrofit instance suitable for talking to Wallet Backend.
 */
@Single
internal fun provideRetrofit(
    converterFactory: GsonConverterFactory,
    @WalletBackend walletBackendOkHttpClient: OkHttpClient,
    configLogic: ConfigLogic,
): Retrofit = Retrofit
    .Builder()
    .baseUrl(configLogic.environmentConfig.serverHostURL)
    .client(walletBackendOkHttpClient)
    .addConverterFactory(converterFactory)
    .build()

@Factory
internal fun provideMdvmApi(retrofit: Retrofit): MdvmApi =
    retrofit.create(MdvmApi::class.java)

@Factory
internal fun provideMdvmApiClient(
    mdvmApi: MdvmApi,
    @WalletBackend walletBackendOkHttpClient: OkHttpClient,
    baseRetrofit: Retrofit,
    clock: Clock,
): MdvmApiClient = MdvmApiClientImpl(
    api = mdvmApi,
    baseClient = walletBackendOkHttpClient,
    baseRetrofit = baseRetrofit,
    clock = clock,
)

@Factory
internal fun provideRwscaApi(retrofit: Retrofit): RwscaApi =
    retrofit.create(RwscaApi::class.java)

@Factory
internal fun provideRwscaApiClient(
    rwscaApi: RwscaApi,
    signingApiFactory: HttpMessageSigningApiFactory,
): RwscaApiClient = RwscaApiClientImpl(
    api = rwscaApi,
    signingApiFactory = signingApiFactory,
)

@Factory
internal fun providePushNotificationsApi(retrofit: Retrofit): PushNotificationsApi =
    retrofit.create(PushNotificationsApi::class.java)

@Factory
internal fun providePushNotificationsApiClient(
    pushNotificationsApi: PushNotificationsApi,
    signingApiFactory: HttpMessageSigningApiFactory,
): PushNotificationsApiClient = PushNotificationsApiClientImpl(
    pushNotificationsApi = pushNotificationsApi,
    signingApiFactory = signingApiFactory,
)

@OpenId4VciVp
@Single
fun provideOpenId4VciVpKtorHttpClient(
    @OpenId4VciVp okHttpClient: OkHttpClient,
): HttpClient {
    return HttpClient(engineFactory = OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(
                json = Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                },
                contentType = ContentType.Application.Json
            )
        }
    }
}

@Single
fun provideWalletAttestationRepository(
    @OpenId4VciVp httpClient: HttpClient,
): WalletAttestationRepository =
    WalletAttestationRepositoryImpl(httpClient)
