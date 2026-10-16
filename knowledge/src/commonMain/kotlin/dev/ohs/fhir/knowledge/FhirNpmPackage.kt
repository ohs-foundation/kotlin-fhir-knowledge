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

/**
 * Identifies a FHIR NPM package to install, as defined by the FHIR specification.
 *
 * See the published [FHIR NPM package specification](https://hl7.org/fhir/packages.html), and
 * [the FHIR Infrastructure page](https://confluence.hl7.org/display/FHIR/NPM+Package+Specification)
 * for further detail.
 *
 * @property name the package id, for example `hl7.fhir.r4.core`.
 * @property version the exact version to install; the package server resolves no ranges.
 * @property canonical the base URL the package's resources are published under, when it is known
 *   ahead of installing. It is recorded against the implementation guide and is not used to fetch
 *   anything.
 */
data class FhirNpmPackage(val name: String, val version: String, val canonical: String? = null)
