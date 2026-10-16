/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ohs.fhir.knowledge

import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.terminologies.ResourceType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A knowledge artifact together with the metadata the knowledge manager indexes it by.
 *
 * See [Clinical Reasoning](https://hl7.org/fhir/R4/clinicalreasoning-module.html) for the formal
 * definition of a knowledge artifact.
 */
internal class MetadataResource(
  val resourceType: ResourceType,
  val url: String?,
  val name: String?,
  val version: String?,
  val resource: Resource,
)

/**
 * Reads [json] as a FHIR metadata resource, or returns `null` if it holds anything else.
 *
 * The document is parsed once: `url`, `name` and `version` are taken straight off the JSON object,
 * and the resource itself is deserialized from the same tree. R4 has no `MetadataResource`
 * supertype to type-check against, so [METADATA_RESOURCE_TYPES] stands in for one.
 */
internal fun Json.decodeMetadataResourceOrNull(json: String): MetadataResource? {
  val element = parseToJsonElementOrNull(json) as? JsonObject ?: return null
  val code = element.string("resourceType") ?: return null
  if (code !in METADATA_RESOURCE_TYPES) return null
  return MetadataResource(
    resourceType = ResourceType.fromCode(code),
    url = element.string("url"),
    name = element.string("name"),
    version = element.string("version"),
    resource = decodeFromJsonElement(Resource.serializer(), element),
  )
}

private fun Json.parseToJsonElementOrNull(json: String) =
  try {
    parseToJsonElement(json)
  } catch (e: SerializationException) {
    null
  }

private fun JsonObject.string(key: String) = get(key)?.jsonPrimitive?.contentOrNull

/**
 * The FHIR R4 resource types that carry knowledge-artifact metadata, matching the subclasses of
 * HAPI's `org.hl7.fhir.r4.model.MetadataResource`.
 *
 * Note that this differs from the list of resources implementing the
 * [MetadataResource interface](https://www.hl7.org/fhir/R5/metadataresource.html) in FHIR R5.
 */
private val METADATA_RESOURCE_TYPES =
  setOf(
    "ActivityDefinition",
    "CapabilityStatement",
    "ChargeItemDefinition",
    "CodeSystem",
    "CompartmentDefinition",
    "ConceptMap",
    "EffectEvidenceSynthesis",
    "EventDefinition",
    "Evidence",
    "EvidenceVariable",
    "ExampleScenario",
    "GraphDefinition",
    "ImplementationGuide",
    "Library",
    "Measure",
    "MessageDefinition",
    "NamingSystem",
    "OperationDefinition",
    "PlanDefinition",
    "Questionnaire",
    "ResearchDefinition",
    "ResearchElementDefinition",
    "RiskEvidenceSynthesis",
    "SearchParameter",
    "StructureDefinition",
    "StructureMap",
    "TerminologyCapabilities",
    "TestScript",
    "ValueSet",
  )
