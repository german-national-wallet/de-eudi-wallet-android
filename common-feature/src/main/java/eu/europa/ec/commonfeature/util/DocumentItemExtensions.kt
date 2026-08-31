package eu.europa.ec.commonfeature.util

import eu.europa.ec.commonfeature.ui.document_details.domain.DocumentItem

/**
 * Flattens SD-JWT document items that contain nested, multi-line values into
 * individually addressable fields.
 *
 * This function keeps all original [DocumentItem]s and additionally extracts
 * nested key–value pairs from items whose `value` is expressed as multiple
 * lines in the form:
 *
 * ```
 * key1: value1
 * key2: value2
 * ```
 *
 * For each extracted pair, a new [DocumentItem] is created using dot-notation
 * to represent the hierarchy:
 *
 * ```
 * <parent>.<childKey>
 * ```
 *
 * ### Example
 * Given a `DocumentItem`:
 * ```
 * elementIdentifier = "address"
 * value =
 *   locality: KÖLN
 *   postal_code: 51147
 *   street_address: HEIDESTRAẞE 17
 * ```
 *
 * This function will produce additional items with identifiers:
 * - `address.locality`
 * - `address.postal_code`
 * - `address.street_address`
 *
 * The original `address` item is preserved in the result.
 *
 * This flattening enables simple and safe access to nested values
 * (e.g. `address.locality`, `place_of_birth.locality`,
 * `age_equal_or_over.18`) when building UI models.
 *
 * @param items The list of [DocumentItem]s to flatten.
 * @return A new list containing the original items plus any flattened nested items.
 */
fun List<DocumentItem>.flattenNestedItems(): List<DocumentItem> {
    val flattened = mutableListOf<DocumentItem>()

    for (item in this) {
        flattened += item // keep original we need them for not nested values like given_name

        // Split into lines, try to parse "key: value" per line
        val lines = item.value
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // If it's not multi-line (or not key:value), skip
        val kvPairs = lines.mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (key.isBlank()) null else key to value
        }

        if (kvPairs.isEmpty()) continue

        // Create nested items: "<parent>.<childKey>"
        kvPairs.forEach { (childKey, childValue) ->
            flattened += DocumentItem(
                elementIdentifier = "${item.elementIdentifier}.$childKey",
                value = childValue,
                readableName = "${item.readableName}.$childKey",
                docId = item.docId
            )
        }
    }

    return flattened
}