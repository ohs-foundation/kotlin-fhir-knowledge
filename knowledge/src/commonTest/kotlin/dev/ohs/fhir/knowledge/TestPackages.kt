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

import kotlin.io.encoding.Base64

// Gzipped tar archives used across the test suite, held inline as Base64 because no common source
// set can read a test resource on every target. Each well-formed archive was written by GNU tar
// 1.35 or Python's tarfile and its expected content read back with tarfile; the malformed ones are
// hand-built 512-byte headers. They are fixtures rather than something the tests build, because an
// archive assembled by the same logic under test would prove nothing.

/** android-fhir's `knowledge/testdata/okhttp_downloader/package.tgz`, byte for byte. */
internal val NPM_PACKAGE_TGZ =
  Base64.decode(
    "H4sIAJK+fGQAA+2VTU+sMBSGWc+vaFhraekXmLj23p0L48a4aJjK1IGWlI5xYvzvFhC/Zu6NC0Zzrzwb4Jy3PYeP93Au738puVQuaWSxlqWKpgchJBgDkegBUcoESwno4gOMAkwp4VxgigVAWFCEInB/gF522LReutDKOhxkq8za7tc1TtfSbUtnN82+/HAn4OX4j4Ap2OjlqaB5jrMFQaD2ulanmGcswzjPCEQZJzjliC2+u9eZ6Xl2fXLIGqP/Uy4QI3zX/8Ev7/3PGGIRYIdsauSH+398/+cf/wPwtrVmmhrheXBKPzP/A+EcpeEHIOb5/xX8df4jjihMCRZc5Dif5/9/yOj/6V3/yuj/P81/ytMP/u+CEUBTN7KPH+7/hwUAsZG1ik9A7FXrj5+/hPioy9wp12pruiQmkEAxhP226Rf8PhuuC2ms0YWsuuDK+6Y9SZLS2rJSsLB1srux177qt7gIKfAudbPS7nKo2wbFVYiFKIUI4jicX/eipWqUWSpTaNWJHgbRqhKwWw4dDXVdX2BYePQq8MrV2tjKltug6yQsSNBbSb/HjXvZA0McBCH/uHj87jc2MzMzMw1PVK6bZQASAAA="
  )

/** The same tree in each of the two pre-pax layouts that carry an over-long path. */
internal val USTAR_PREFIX_TGZ =
  Base64.decode(
    "H4sIAAAAAAACA+3U7U6DMBiG4R7K0t9SWj4Tz6aOV6hCS0pnXIznLiQzMYtT2GRxyXP9gITPwk1p2lI8NsYLetVd35Iw07IjG3Qwzop6ZyqK2UXkqCzzaa3KXH5df2IqT9KsyJIiU0wmSsmCbXJ2BbshaD8OxZKvhuAsnTjut/03qpnVX0cVteaBvA7U7qPW2ToyNpDvqDLjtqgynrbB+X1kdXf8vSzunyapQv+r0NaFhvxPSeNL77G4f6bSm5//q0ysv3/8Xm+fdU3xmq94ef+klAr9v/mxzpiuZ/UXT4Oza/Yviux0/1wd9c9VkbKNRP8z+seHpPOG+can6/J7XhH10eFUfsdfyA/jMMcdSiQi5e8MAAAAAAAAAAAAAAAAAAD+sw8ubhhsACgAAA=="
  )

/** @see USTAR_PREFIX_TGZ */
internal val GNU_LONGNAME_TGZ =
  Base64.decode(
    "H4sIAAAAAAACA+2W0WqDMBSG8yji9YxJTCLsag/Ql8jqWc2qicR0rIy9+xRa6FrarowWWs934YGcgBf/OZ/WTUnfahsofJq2a4Da8dmCiyZa7+hiZSvIyb9gA2WpxspLxXbrFsKVKKSWQktOmOBMapIocgNWfTQhSYiDUPXROzhy71z/Tqn/lL/JKmjsKwQToVlnjXeLzLoIoYXKDmdZZQPMow/rzJl2f14uzr8QQmH+N4HmNH+ZDXnOrFte6R1jwFrKbd77dRiG4tcsMM41kySZ3TL/4H08de9cH/d/Z/+N87GGcOpiTpCJ5H+5/xUTAv0/Ff8LduB/VWr0/2P7vzPzpVkAfgfQ/+j/afufl4f//wX6fxr+31T63nuHPn5U/2stj/tf8UP/M5Iw9P/V+UrHwNLntALoss0upk/pB4R+yH9ocCpokX7jqiAIgiAIgiAIgiAIgtwlP+lDAPoAKAAA"
  )

/**
 * A pax archive whose entry name fits in neither the ustar name nor its prefix field, so the real
 * path lives only in an extended header. This is the shape `npm pack` writes.
 */
internal val PAX_LONG_NAME_TGZ =
  Base64.decode(
    "H4sIAAAAAAACA+3VTWrDMBAFYK1zCpMD2JJHIzmF7LvMFUQi0r/Yie1AIPTulUMDrosJLbGL6fs2sxiEDdKbiZOVOz16t/Fllezd+tVtvbgzGRitLzXoVqkoFYpT0kaztkpIpUyaiugkRnCsaleGXxH/E8loVz/v/FLZjBda2gXFRCrLUpPxLHTd965suiZruuv+swIm4DPyyZDfaDJuLTdVWZbtetXJf3hFIf88Zv5zX26qusj7pt+t/sTvv7UFDvd2a/6Toa/3T6SZMP/HoJiivauflteHcPhz8UtV5LPfLKZWF4vpZ/kf8kIv+Te6f/4r7uTfchgXkcT8H9x5/lbk2/lDXR79O+IAAAAAAAAAAAAAAAAAMGkfWeHAzQAoAAA="
  )

/** The single file [PAX_LONG_NAME_TGZ] carries, and its content. */
internal val PAX_LONG_ENTRY_NAME = "package/${"q".repeat(130)}.json"

internal const val PAX_LONG_ENTRY_CONTENT = """{"long":true}"""

/**
 * Entries of 0, 512 and 513 bytes, each followed by another entry, so that padding arithmetic that
 * is wrong at a block boundary desynchronises the very next header rather than passing unnoticed.
 */
internal val BLOCK_BOUNDARY_TGZ =
  Base64.decode(
    "H4sIAAAAAAACA+3W0QqCMBjF8T2K+AC1z8ygtxkywrIaboISvXsSeKNBN6mE/9/Nt/vDOcyZ/GJOdmuvLrSbs7/f1M/pTpam79sZ3vFbZCcHFWk1g9oHU0WRWinX59+YPCyTv3zIP8ky8p9Dg1Xr++9dUZaL9V9G+6/pP/3H9BTWrN//YIqp5v/7/1/2w/2XRLP/c3jEpfEhPoaqtk/qAAAAAAAAAAAA8PdedKcSEgAoAAA="
  )

/** A header whose stored checksum does not match its own bytes, as a corrupt stream would give. */
internal val BAD_CHECKSUM_TGZ =
  Base64.decode(
    "H4sIAAAAAAACAytITM5OTE/VL0nMLUgtSk3RyyrOz2OgLjAAAjMTEzANBOg0EJgjsaHihgwKBgx0AKXFJYlFQCsZRiaoVkpUsjKsZRgFo2AUjIJRMLIAAHyec1QACAAA"
  )

/**
 * A directory recorded the pre-ustar way — type flag `0`, name ending in `/` — then a file in it.
 */
internal val LEGACY_DIRECTORY_TGZ =
  Base64.decode(
    "H4sIAAAAAAACAytITM5OTE/VZ6AhMAACMxMTMA0E6DQWtrmxsQGDggEDHUBpcUliEdBKhpEJCqDxn5daXJKaopdVnJ83APFvjhr/hoamhkaj8U8PUK2Up2RlWMswCkbBKBgFo2BkAQAwgL/fAAoAAA=="
  )

/** A well-formed final entry with none of the trailing zero blocks that should close an archive. */
internal val NO_TRAILER_TGZ =
  Base64.decode(
    "H4sIAAAAAAACAytITM5OTE/Vz8/LqdTLKs7PY6A+MAACMxMTMA0E6LSBgaEpgg0SNzQ0MjRgUDBgoAMoLS5JLAJazzAyQbUSKOaVrEqKSlNrGUbBSAMAjc0IogAEAAA="
  )

/** An archive cut off in the middle of an entry, the shape a failed download leaves behind. */
internal val TRUNCATED_TGZ =
  Base64.decode(
    "H4sIAAAAAAACAytITM5OTE/VTy4t0csqzs9joAEwAAIzExMwDQRotCGyGIRtaGhgaMqgYMBAB1BaXJJYBLSeYWSCSjoAAIa7Q7xkAgAA"
  )

/** A header whose size field is not the octal the format requires. */
internal val BAD_SIZE_TGZ =
  Base64.decode(
    "H4sIAAAAAAACAytITM5OTE/VT0pM0S3OrErVyyrOz2OgLjAAAjMTEzANBHA6L79ENz+5JDFHUREmBpE3NDIzMmJQMGCgAygtLkksAlrPMApGwSgYBSMLAADvGuIlAAYAAA=="
  )

/** A hand-built archive whose single entry, `../../escaped.json`, points outside the archive. */
internal val PATH_TRAVERSAL_TGZ =
  Base64.decode(
    "H4sIAAAAAAACA9PT09fT008tTk4sSE3RyyrOz2OgOjAAAjMTEzANBOg0EBghsYHihgZmZkYMCgYMdAClxSWJRUDrGUYmqK5lGAWjYBSMglEwAgEAM4yg0QAIAAA="
  )

/** The exact content of `package/package.json` inside [NPM_PACKAGE_TGZ]. */
internal val NPM_PACKAGE_MANIFEST =
  """
  {
    "name": "test-package",
    "version": "13.3.7",
    "type": "IG",
    "canonical": "https://google.com/test-package",
    "title": "Test package",
    "fhirVersions": [
      "4.0.1"
    ],
    "dependencies": {
      "hl7.fhir.r4.core": "4.0.1",
      "hl7.terminology.r4": "5.0.0",
      "hl7.fhir.fr.core": "1.1.0"
    }
  }
  """
    .trimIndent()

/** The single file [USTAR_PREFIX_TGZ] and [GNU_LONGNAME_TGZ] carry, and its content. */
internal const val LONG_ENTRY_NAME =
  "hl7.fhir.example.implementation.guide/a-deliberately-long-intermediate-directory-name/" +
    "another-long-intermediate-directory/package/package.json"

internal const val LONG_ENTRY_CONTENT = """{"name":"deep-package","version":"1.2.3"}"""
