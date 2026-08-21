package com.example.meritrankerstudent.ui.components.richtext

object ContentNormalizer {

    /**
     * Normalizes raw response string before parsing.
     * Fixes JSON backslash escaping (\\frac -> \frac, \\( -> \(), normalizes line endings,
     * strips zero-width spaces, and preserves aligned linebreaks (\\\\).
     */
    fun normalize(input: String?): String {
        if (input.isNullOrEmpty()) return ""

        var result = input
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u200B", "")
            .replace("\uFEFF", "")

        // Normalize JSON double backslashes for math delimiters
        result = result
            .replace("\\\\(", "\\(")
            .replace("\\\\)", "\\)")
            .replace("\\\\[", "\\[")
            .replace("\\\\]", "\\]")

        // Normalize JSON double backslashes for TeX commands
        val texCommands = listOf(
            "frac", "sqrt", "pm", "mp", "times", "div", "cdot", "le", "ge", "leq", "geq", "neq", "ne", "approx",
            "implies", "iff", "therefore", "because", "to", "gets", "rightarrow", "leftarrow",
            "alpha", "beta", "gamma", "delta", "epsilon", "theta", "lambda", "mu", "pi", "sigma", "tau", "phi", "omega",
            "Delta", "Sigma", "Omega",
            "sum", "int", "lim", "begin", "end", "ce", "text", "mathrm", "mathbf", "mathit", "mathtt",
            "left", "right", "cdashline", "degree", "infty", "angle", "triangle", "parallel", "perp",
            "cup", "cap", "in", "notin", "subset", "subseteq", "empty", "emptyset"
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
