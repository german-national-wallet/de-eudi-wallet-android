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

package project.convention.logic

import com.android.build.api.dsl.VariantDimension

@Suppress("IMPLICIT_CAST_TO_ANY")
inline fun <reified ValueT : Any> VariantDimension.addConfigField(
    name: String,
    value: ValueT
) {
    val resolvedValue = when (value) {
        is String -> "\"$value\""
        else -> value
    }.toString()
    // Primitive type when there is one (int, not Integer): only primitives and String are
    // compile-time constants, which the compiler inlines and R8 then drops from BuildConfig. A
    // boxed field is read at runtime, keeps BuildConfig alive, and with it every other field's
    // value, tokens included (the app-wide -keepclassmembers rule keeps all fields).
    val type = ValueT::class.javaPrimitiveType ?: ValueT::class.java
    buildConfigField(type.simpleName, name, resolvedValue)
}