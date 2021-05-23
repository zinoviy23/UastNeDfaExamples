import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.export.ggsave
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.letsPlot
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.readLines

val basePath = Path("./comparisionWithPlatform")

fun main() {
    val results = (basePath / "lastResults.txt").readLines()
    val headers = results[0].split("&", """\\""").map { it.trim() }.dropLast(1)
    results.drop(1)
        .map { it.split("&", """\\""").dropLast(1).map { it.trim() } }
        .associate { it.first() to it.drop(1) }
        .mapValues { (_, values) ->
            values.map { it.removeSuffix(" ms").toDoubleOrNull() }
        }
        .forEach { (testName, values) ->
            saveCharts(testName, headers.zip(values).toMap())
        }
}

private fun saveCharts(testName: String, results: Map<String, Double?>) {
    val myPlot = letsPlot() + geomBar(
        fill = "blue",
        alpha = .3,
        size = 2.0,
        stat = Stat.identity
    ) {
        x = results.keys
        y = results.values.map { it ?: 0 }
    } + xlab("Algorithm") + ylab("Time ms") + ggtitle(testName)

    ggsave(myPlot, "$testName.png", path = "./comparisionWithPlatform/charts")
}