package com.obdvis.android.domain.health

data class DriveCycleStep(
    val title: String,
    val detail: String,
    val monitors: Set<String>,
)

val DRIVE_CYCLE_STEPS: List<DriveCycleStep> = listOf(
    DriveCycleStep(
        "Cold start",
        "Start the engine cold (below 30 °C / 86 °F) without pre-warming.",
        setOf("Secondary Air", "O2 Heater", "Heated Catalyst", "Evap System"),
    ),
    DriveCycleStep(
        "Idle 2–3 min",
        "Idle in park or neutral until the engine reaches normal operating temperature.",
        setOf("Secondary Air", "O2 Heater"),
    ),
    DriveCycleStep(
        "Check fuel level",
        "Ensure the fuel tank is between 15 % and 85 % full before continuing.",
        setOf("Evap System"),
    ),
    DriveCycleStep(
        "City driving — 5 min",
        "Drive at 25–45 km/h (15–30 mph) with gentle accelerations and release-throttle decelerations.",
        setOf("Evap System", "O2 Sensor", "EGR System"),
    ),
    DriveCycleStep(
        "Run A/C",
        "Turn on the air conditioning and run it for several minutes while driving.",
        setOf("A/C System"),
    ),
    DriveCycleStep(
        "Accelerate to highway speed",
        "Accelerate smoothly to 88 km/h (55 mph).",
        setOf("Catalyst", "O2 Sensor"),
    ),
    DriveCycleStep(
        "Steady highway cruise — 5 min",
        "Maintain 88–97 km/h (55–60 mph) at a light, steady throttle for at least 5 minutes.",
        setOf("Catalyst", "Heated Catalyst", "EGR System"),
    ),
    DriveCycleStep(
        "Highway deceleration",
        "Release the throttle completely and coast down to 40 km/h (25 mph) — avoid braking.",
        setOf("Catalyst", "EGR System"),
    ),
    DriveCycleStep(
        "Repeat highway cruise — 5 min",
        "Accelerate back to 88 km/h (55 mph) and cruise for another 5 minutes.",
        setOf("Catalyst", "O2 Sensor"),
    ),
)

fun driveCycleStepsFor(incompleteMonitors: List<String>): List<DriveCycleStep> {
    if (incompleteMonitors.isEmpty()) return emptyList()
    val names = incompleteMonitors.toSet()
    return DRIVE_CYCLE_STEPS.filter { step -> step.monitors.any { it in names } }
}
