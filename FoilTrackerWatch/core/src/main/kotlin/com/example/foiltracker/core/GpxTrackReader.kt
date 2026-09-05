package com.example.foiltracker.core

import java.io.File
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

object GpxTrackReader {

    fun read(
        file: File
    ): List<TrackPoint> {

        require(file.exists()) {
            "GPX file does not exist: ${file.absolutePath}"
        }

        val factory =
            DocumentBuilderFactory.newInstance().apply {
                /*
                 * We only read local GPX files. Disable external
                 * entity processing.
                 */
                try {
                    setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true
                    )
                } catch (_: Exception) {
                }

                try {
                    setFeature(
                        "http://xml.org/sax/features/external-general-entities",
                        false
                    )
                } catch (_: Exception) {
                }

                try {
                    setFeature(
                        "http://xml.org/sax/features/external-parameter-entities",
                        false
                    )
                } catch (_: Exception) {
                }

                try {
                    setFeature(
                        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                        false
                    )
                } catch (_: Exception) {
                }

                isNamespaceAware = true
                // isXIncludeAware = false
                isExpandEntityReferences = false
            }

        val document =
            factory
                .newDocumentBuilder()
                .parse(file)

        /*
         * GPX 1.1 normally uses a default namespace:
         *
         *   xmlns="http://www.topografix.com/GPX/1/1"
         *
         * Try namespace-aware lookup first.
         */
        val trackPoints =
            document.getElementsByTagNameNS(
                "http://www.topografix.com/GPX/1/1",
                "trkpt"
            )

        /*
         * Some GPX files may not have the standard namespace.
         *
         * Fall back to a plain tag lookup.
         */
        val nodes =
            if (trackPoints.length > 0) {
                trackPoints
            } else {
                document.getElementsByTagName("trkpt")
            }

        if (nodes.length == 0) {
            return emptyList()
        }

        return buildList {

            for (index in 0 until nodes.length) {

                val node =
                    nodes.item(index)

                val latitude =
                    node.attributes
                        ?.getNamedItem("lat")
                        ?.nodeValue
                        ?.toDoubleOrNull()
                        ?: error(
                            "GPX point ${index + 1} has no valid lat"
                        )

                val longitude =
                    node.attributes
                        ?.getNamedItem("lon")
                        ?.nodeValue
                        ?.toDoubleOrNull()
                        ?: error(
                            "GPX point ${index + 1} has no valid lon"
                        )

                val time =
                    findChildElement(
                        node,
                        "time"
                    )?.textContent
                        ?.trim()
                        ?: error(
                            "GPX point ${index + 1} has no <time>"
                        )

                val timeMs =
                    try {
                        Instant
                            .parse(time)
                            .toEpochMilli()
                    } catch (e: Exception) {
                        throw IllegalArgumentException(
                            "GPX point ${index + 1} " +
                                "has invalid time: $time",
                            e
                        )
                    }

                add(
                    TrackPoint(
                        latitude = latitude,
                        longitude = longitude,
                        timeMs = timeMs
                    )
                )
            }
        }
    }

    private fun findChildElement(
        node: org.w3c.dom.Node,
        localName: String
    ): org.w3c.dom.Node? {

        val children =
            node.childNodes

        for (index in 0 until children.length) {

            val child =
                children.item(index)

            /*
             * Depending on parser configuration, either
             * localName or nodeName may contain the useful value.
             */
            val childLocalName =
                child.localName

            if (childLocalName == localName) {
                return child
            }

            if (child.nodeName == localName) {
                return child
            }

            if (child.nodeName.endsWith(":$localName")) {
                return child
            }
        }

        return null
    }
}
