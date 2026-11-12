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

package org.sprind.wallet.issuancefeature.ui.document.details

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import eu.europa.ec.issuancefeature.ui.document.details.CredentialDataList
import eu.europa.ec.issuancefeature.ui.document.details.CredentialDataListTestTags
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.testlogic.base.TestApplication
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.sprind.wallet.commonfeature.ui.credentialdata.CredentialDataItem

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApplication::class)
class CredentialDataListTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val items = listOf(
        CredentialDataItem.Attribute("given_name", "Given name(s)", "ERIKA"),
        CredentialDataItem.Attribute("title", "Doctoral degree", "-", isMissing = true),
        CredentialDataItem.Heading("address", "Address"),
        CredentialDataItem.Attribute("address.locality", "City", "KÖLN"),
    )

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun setContent(isPid: Boolean) = composeRule.setContent {
        PreviewTheme { CredentialDataList(items = items, isPid = isPid) }
    }

    private fun isHeading(text: String) =
        hasText(text).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

    private fun hasMergedText(vararg text: String) = SemanticsMatcher("merged text ${text.toList()}") { node ->
        node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.map { it.text } == text.toList()
    }

    @Test
    fun `the PID shows the personal data title and banner`() {
        setContent(isPid = true)

        composeRule.onNode(isHeading(string(R.string.pid_inspection_pid_personal_data_title))).assertExists()
        composeRule.onNode(hasText(string(R.string.pid_inspection_pid_personal_data_banner))).assertExists()
    }

    @Test
    fun `the banner is read as its text alone, its icon being decorative`() {
        setContent(isPid = true)

        composeRule.onNode(hasMergedText(string(R.string.pid_inspection_pid_personal_data_banner)))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun `an EAA shows the detailed data title and banner`() {
        setContent(isPid = false)

        composeRule.onNode(isHeading(string(R.string.eaa_inspection_eaa_detailed_data_title))).assertExists()
        composeRule.onNode(hasText(string(R.string.eaa_inspection_eaa_detailed_data_banner))).assertExists()
    }

    @Test
    fun `a section is announced as a heading`() {
        setContent(isPid = true)

        composeRule.onNode(isHeading("Address")).assertExists()
    }

    @Test
    fun `an attribute is read as one node of label and value`() {
        setContent(isPid = true)

        composeRule.onNode(hasMergedText("Given name(s)", "ERIKA")).assertExists()
    }

    @Test
    fun `a missing claim is read as no value instead of its dash`() {
        setContent(isPid = true)

        val noValue = string(R.string.__variable_text_credential_data_var_label_empty_value_a11y)
        composeRule.onNode(hasMergedText("Doctoral degree", noValue)).assertExists()
        composeRule.onNode(hasText("-")).assertDoesNotExist()
    }

    @Test
    fun `the list counts every data row and indexes them in order`() {
        setContent(isPid = true)

        composeRule.onNodeWithTag(CredentialDataListTestTags.LIST).assert(
            SemanticsMatcher("row count ${items.size}") { it.config[SemanticsProperties.CollectionInfo].rowCount == items.size }
        )
        composeRule.onNode(hasMergedText("City", "KÖLN")).assert(
            SemanticsMatcher("row index 3") { it.config[SemanticsProperties.CollectionItemInfo].rowIndex == 3 }
        )
    }
}