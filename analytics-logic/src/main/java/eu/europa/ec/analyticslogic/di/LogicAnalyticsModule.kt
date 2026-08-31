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

package eu.europa.ec.analyticslogic.di

import android.app.Application
import org.sprind.wallet.analyticslogic.controller.Telemetry
import org.sprind.wallet.analyticslogic.controller.TelemetryConstants
import org.sprind.wallet.analyticslogic.controller.TelemetryImpl
import org.sprind.wallet.analyticslogic.interceptor.HttpTelemetryInterceptor
import eu.europa.ec.businesslogic.config.ConfigLogic
import eu.europa.ec.businesslogic.controller.log.LogController
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.agent.OpenTelemetryRumInitializer
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Attribute key for the service name.
 */
val KEY_SERVICE_NAME: AttributeKey<String> = AttributeKey.stringKey("service.name")
val KEY_SERVICE_VERSION: AttributeKey<String> = AttributeKey.stringKey("service.version")

@Module
@ComponentScan("eu.europa.ec.analyticslogic")
class LogicAnalyticsModule

@Single
fun provideOpenTelemetryRum(
    application: Application,
    logController: LogController,
    configLogic: ConfigLogic,
): OpenTelemetryRum {
    val logTag = "provideOpenTelemetryRum"
    return try {
        val serviceName = configLogic.environmentConfig.otelServiceName
        OpenTelemetryRumInitializer.initialize(
            context = application,
            configuration = {
                httpExport {
                    baseUrl = configLogic.environmentConfig.OTEL_WALLET_URL
                    baseHeaders = mapOf("X-Auth-Token" to configLogic.environmentConfig.OTEL_WALLET_AUTH_TOKEN)
                }
                resource {
                    put(KEY_SERVICE_NAME, serviceName)
                }
                diskBuffering {
                    enabled(true)
                }
            }
        ).also {
            logController.i(logTag) { "OpenTelemetry RUM initialized. Session ID: ${it.getRumSessionId()}" }
        }
    } catch (e: Exception) {
        logController.e(logTag) { "CRITICAL: Failed to initialize OpenTelemetry RUM. Analytics disabled." }
        throw IllegalStateException("Analytics initialization failed.", e)
    }
}

@Factory
fun provideHttpTraceLogInterceptor(analyticsController: Telemetry): HttpTelemetryInterceptor {
    return HttpTelemetryInterceptor(analyticsController)
}

@Single
fun provideAnalyticsController(
    rum: OpenTelemetryRum,
    logController: LogController,
    configLogic: ConfigLogic,
): Telemetry {
    val resource = Resource.create(
        Attributes.builder()
            .put(KEY_SERVICE_NAME, configLogic.environmentConfig.otelServiceName)
            .put(KEY_SERVICE_VERSION, configLogic.appVersion)
            .build()
    )
    val logExporter = OtlpHttpLogRecordExporter.builder()
        .setEndpoint(configLogic.environmentConfig.OTEL_WALLET_URL.trimEnd('/') + "/v1/logs")
        .addHeader("X-Auth-Token", configLogic.environmentConfig.OTEL_WALLET_AUTH_TOKEN)
        .build()
    val loggerProvider = SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
        .build()
    val logger: Logger = loggerProvider.get(TelemetryConstants.SCOPE_DEFAULT)
    return TelemetryImpl(rum, logController, logger)
}
