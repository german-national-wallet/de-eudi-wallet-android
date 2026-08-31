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
package org.sprind.wallet.flags.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Qualifier
import org.koin.core.annotation.Single
import org.sprind.wallet.flags.FeatureFlagConfig
import org.sprind.wallet.flags.FeatureFlagManager
import org.sprind.wallet.flags.FeatureFlagManagerImpl
import org.sprind.wallet.flags.FeatureFlagStorage
import org.sprind.wallet.flags.FeatureFlagUpdateService
import org.sprind.wallet.flags.FeatureFlagUpdateServiceImpl
import org.sprind.wallet.flags.api.FeatureFlagApiKeyInterceptor
import org.sprind.wallet.flags.api.FeatureFlagApi
import org.sprind.wallet.flags.api.FeatureFlagApiClient
import org.sprind.wallet.flags.api.FeatureFlagApiClientImpl
import org.sprind.wallet.flags.models.flagFormatter
import retrofit2.Retrofit

@Module
@ComponentScan("org.sprind.wallet")
class FeatureFlagModule

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeatureFlag

@Single
fun provideFeatureFlagManager(featureFlagStorage: FeatureFlagStorage): FeatureFlagManager = FeatureFlagManagerImpl(featureFlagStorage)

@Single
fun provideFeatureFlagApi(@FeatureFlag retrofit: Retrofit): FeatureFlagApi = retrofit.create(FeatureFlagApi::class.java)

@FeatureFlag
@Single
fun provideFeatureFlagRetrofit(
    featureFlagConfig: FeatureFlagConfig,
    @FeatureFlag okHttpClient: OkHttpClient,
): Retrofit =
    Retrofit.Builder()
        .baseUrl(featureFlagConfig.featureFlagApiBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(
            flagFormatter.asConverterFactory("application/json".toMediaType())
        )
        .build()

@Single
fun provideFeatureFlagApiClient(featureFlagApi: FeatureFlagApi): FeatureFlagApiClient = FeatureFlagApiClientImpl(featureFlagApi)

@FeatureFlag
@Factory
fun provideFeatureFlagLoggingInterceptor(
    featureFlagConfig: FeatureFlagConfig,
): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (featureFlagConfig.isHttpLoggingEnabled) {
        HttpLoggingInterceptor.Level.BODY
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
}

@FeatureFlag
@Single
fun provideFeatureFlagOkHttpClient(
    baseClient: OkHttpClient,
    @FeatureFlag httpLoggingInterceptor: HttpLoggingInterceptor,
): OkHttpClient = baseClient
    // Share the baseClient's Cache, connection pool, timeouts.
    .newBuilder()
    // Add the ?apiKey=... query parameter to request URLs.
    .addInterceptor(FeatureFlagApiKeyInterceptor())
    .addInterceptor(httpLoggingInterceptor)
    .build()

@Single
fun provideFeatureFlagUpdateService(
    featureFlagStorage: FeatureFlagStorage,
    featureFlagApiClient: FeatureFlagApiClient
): FeatureFlagUpdateService =
    FeatureFlagUpdateServiceImpl(featureFlagStorage, featureFlagApiClient)

