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

package eu.europa.ec.uilogic.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import org.sprind.wallet.analyticslogic.controller.Telemetry
import eu.europa.ec.uilogic.config.ConfigUILogic
import eu.europa.ec.uilogic.config.ConfigUILogicImpl
import eu.europa.ec.uilogic.navigation.RouterHost
import eu.europa.ec.uilogic.navigation.RouterHostImpl
import eu.europa.ec.uilogic.serializer.UiSerializer
import eu.europa.ec.uilogic.serializer.UiSerializerImpl
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.io.File

private const val MAX_IMAGE_DOWNLOAD_SIZE_BYTES = 10L * 1024 * 1024 // 10 MB
private const val MAX_IMAGE_STORAGE_SIZE_BYTES = 1024L * 1024 * 1024 // 1 GB


@Module
@ComponentScan("eu.europa.ec.uilogic")
class LogicUiModule

@Single
fun provideRouterHost(
    configUILogic: ConfigUILogic,
    telemetry: Telemetry,
): RouterHost = RouterHostImpl(configUILogic, telemetry)

@Factory
fun provideUiSerializer(): UiSerializer = UiSerializerImpl()

@Single
fun provideConfigUILogic(): ConfigUILogic = ConfigUILogicImpl()

/**
 * Provides a [ImageLoader] configured for credential image loading.
 *
 * The loader uses a disk cache with a maximum storage size of 1 GB and enforces
 * a 10 MB per-image download limit via [ImageDownloadPoliciesInterceptor],
 * which also strips `Cache-Control: max-age` / `Expires` headers so the disk
 * cache policy alone controls image retention.
 *
 * @param context The Android context used to access file storage for the disk cache.
 * @return A configured [ImageLoader] instance.
 * @throws java.io.IOException If a downloaded image exceeds the 10 MB size limit.
 */
@Single
fun provideImageLoader(context: Context): ImageLoader {
    val okHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(ImageDownloadPoliciesInterceptor(MAX_IMAGE_DOWNLOAD_SIZE_BYTES))
        .build()

    return ImageLoader.Builder(context)
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.filesDir, "credential_images"))
                .maxSizeBytes(MAX_IMAGE_STORAGE_SIZE_BYTES)
                .build()
        }
        .components {
            add(SvgDecoder.Factory())
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
        }
        .build()
}