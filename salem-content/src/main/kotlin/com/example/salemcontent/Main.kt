/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent

import com.example.salemcontent.pipeline.ContentPipeline
import java.io.File

/**
 * CLI entry point for the Salem content pipeline.
 *
 * Usage:
 *   ./gradlew :salem-content:run --args="~/Development/Salem"
 *   ./gradlew :salem-content:run --args="~/Development/Salem output.sql"
 */
fun main(args: Array<String>) {
    val salemRoot = File(args.getOrElse(0) {
        val home = System.getProperty("user.home")
        "$home/Development/Salem"
    }).let { if (it.path.startsWith("~")) File(System.getProperty("user.home"), it.path.removePrefix("~")) else it }

    val outputFile = File(args.getOrElse(1) { "salem_content.sql" })

    require(salemRoot.isDirectory) {
        "Salem project not found at: ${salemRoot.absolutePath}"
    }

    val pipeline = ContentPipeline(salemRoot)
    val output = pipeline.run()

    println("\n=== Summary ===")
    println("Figures:         ${output.figures.size}")
    println("Facts:           ${output.facts.size}")
    println("Timeline events: ${output.timelineEvents.size}")
    println("Primary sources: ${output.primarySources.size}")
    println("Tour POIs:       ${output.tourPois.size} (Phase 5)")
    println("Businesses:      ${output.businesses.size} (Phase 5)")
    println("Tours:           ${output.tours.size} (Phase 6)")
    println("Tour stops:      ${output.tourStops.size}")
    println("Tour legs:       ${output.tourLegs.size} (pre-baked walking polylines)")
    println("Events calendar: ${output.events.size} (Phase 9)")

    pipeline.writeSql(output, outputFile)
    println("\nDone. SQL written to: ${outputFile.absolutePath}")
}
