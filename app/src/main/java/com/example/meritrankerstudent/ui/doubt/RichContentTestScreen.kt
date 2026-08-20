package com.example.meritrankerstudent.ui.doubt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meritrankerstudent.ui.components.richtext.EducationalContentRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichContentTestScreen(
    onBack: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val testMarkdown = """
        # General Science & Mathematics Review
        
        ## 1. Quadratic Equations
        The standard quadratic equation is:
        $$ x^2 - 5x + 6 = 0 $$
        
        The roots are calculated using the quadratic formula:
        $$ x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} $$
        
        ## 2. Ethanol Chemical Reaction
        Combustion of Ethanol in presence of Oxygen:
        \ce{C2H5OH + 3O2 -> 2CO2 + 3H2O}
        
        Ethanol structural shorthand: C2H5OH or CH3CH2OH.
        
        ## 3. History Timeline
        ```timeline
        1857 | Revolt of 1857 | First war of Indian Independence
        1885 | INC Founded | Indian National Congress formed in Bombay
        1947 | Independence Day | India attained freedom on August 15
        ```
        
        ## 4. Reasoning Flowchart
        ```mermaid
        flowchart TD
        A[Identify Given Problem] --> B[Apply Formula]
        B --> C[Calculate Values]
        C --> D[Final Solution]
        ```
        
        ## 5. Sample Code
        ```kotlin
        fun solveQuadratic(a: Double, b: Double, c: Double): Pair<Double, Double> {
            val d = Math.sqrt(b * b - 4 * a * c)
            return Pair((-b + d) / (2 * a), (-b - d) / (2 * a))
        }
        ```
        
        ## 6. Comparison Table
        | Topic | Formula / Concept | Example |
        | --- | --- | --- |
        | Percentage | (Part / Whole) * 100 | (45/60) * 100 = 75% |
        | Quadratic | x = (-b ± √(b² - 4ac)) / 2a | x² - 5x + 6 = 0 |
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rich Content Test Harness", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EducationalContentRenderer(
                content = testMarkdown,
                isStreaming = false
            )
        }
    }
}
