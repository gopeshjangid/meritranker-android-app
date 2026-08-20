package com.example.meritrankerstudent.ui.components.richtext

object ContentNormalizer {

    /**
     * Normalizes raw response string before parsing.
     * Fixes JSON backslash escaping (\\frac -> \frac), normalizes line endings,
     * strips zero-width spaces, and preserves aligned linebreaks (\\\\).
     */
    fun normalize(input: String?): String {
        if (input.isNullOrEmpty()) return ""

        var result = input
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u200B", "")
            .replace("\uFEFF", "")

        // Normalize JSON double backslashes for common TeX commands while preserving \\ inside aligned blocks
        // Fix \\frac, \\sqrt, \\pm, \\times, \\div, \\le, \\ge, \\neq, \\approx, \\alpha, \\beta, \\theta, \\pi, \\sum, \\begin, \\end, \\ce
        val texCommands = listOf(
            "frac", "sqrt", "pm", "times", "div", "le", "ge", "neq", "approx",
            "alpha", "beta", "gamma", "delta", "theta", "pi", "sigma", "omega", "Delta",
            "sum", "int", "lim", "begin", "end", "ce", "text", "left", "right", "cdashline"
        )

        for (cmd in texCommands) {
            result = result.replace("\\\\$cmd", "\\$cmd")
        }

        // Normalize Unicode minus and multiplication
        result = result
            .replace("−", "-")
            .replace("✕", "×")

        return result
    }
}
