package com.example.calculator
import java.util.Stack
import android.view.View
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.math.BigDecimal
import java.math.RoundingMode

class MainActivity : AppCompatActivity() {
    private lateinit var display: TextView
    private var expression: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        display = findViewById(R.id.display)

        if (savedInstanceState != null) {
            expression = savedInstanceState.getString("expression") ?: ""
            display.text = expression
        }


        display.setOnDoubleClickListener {
            copyToClipboard()
        }

        // Numéros
        val numberButtons = listOf(
            findViewById<Button>(R.id.button0), findViewById<Button>(R.id.button1),
            findViewById<Button>(R.id.button2), findViewById<Button>(R.id.button3),
            findViewById<Button>(R.id.button4), findViewById<Button>(R.id.button5),
            findViewById<Button>(R.id.button6), findViewById<Button>(R.id.button7),
            findViewById<Button>(R.id.button8), findViewById<Button>(R.id.button9)
        )
        for ((index, button) in numberButtons.withIndex()) {
            button.setOnClickListener { appendToExpression(index.toString()) }
        }

        // Opérations
        findViewById<Button>(R.id.buttonAdd).setOnClickListener { appendToExpression("+") }
        findViewById<Button>(R.id.buttonSubtract).setOnClickListener { appendToExpression("-") }
        findViewById<Button>(R.id.buttonMultiply).setOnClickListener { appendToExpression("*") }
        findViewById<Button>(R.id.buttonDivide).setOnClickListener { appendToExpression("/") }
        findViewById<Button>(R.id.buttonModulo).setOnClickListener { appendToExpression("%") }


        // Bouton égal
        findViewById<Button>(R.id.buttonEquals).setOnClickListener { evaluate() }

        // Effacement
        findViewById<Button>(R.id.buttonClear).setOnClickListener { clearAll() }
        findViewById<Button>(R.id.buttonDelete).setOnClickListener { deleteLastChar() }

        // Bouton de négation
        findViewById<Button>(R.id.buttonNegate).setOnClickListener { negateLastNumber() }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("expression", expression)
    }

    private fun copyToClipboard() {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("result", display.text)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(this, "Résultat copié !", Toast.LENGTH_SHORT).show()
    }

    private fun appendToExpression(value: String) {

        if (value.isOperator() && expression.lastOrNull()?.toString()?.isOperator() == true) {
            return
        }
        if (expression.isEmpty() && value.isOperator() && value != "-") {
            return
        }

        expression += value
        display.text = expression
    }

    private fun deleteLastChar() {
        if (expression.isNotEmpty()) {
            val lastChar = expression.last()
            expression = expression.dropLast(1)

            if (lastChar.isDigit() && expression.lastOrNull() == '-') {
                expression = expression.dropLast(1)
            }
            display.text = expression
        }
    }
    private fun clearAll() {
        expression = ""
        display.text = ""
    }

    private fun evaluate() {
        try {
            val result = evaluateExpression(expression)
            expression = formatExpression(result)
            display.text = expression
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur : Expression invalide", Toast.LENGTH_SHORT).show()
        }
    }

    private fun evaluateExpression(expr: String): String {
        val tokens = tokenizeExpression(expr)
        val values = Stack<BigDecimal>()
        val operators = Stack<Char>()

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.isNumber() -> {
                    values.push(BigDecimal(token))
                }
                token.isOperator() -> {
                    if (token == "-" && (i == 0 || tokens[i-1].isOperator())) {
                        if (i + 1 < tokens.size && tokens[i+1].isNumber()) {
                            values.push(BigDecimal(tokens[i+1]).negate())
                            i++
                        }
                    } else {
                        while (operators.isNotEmpty() &&
                            precedence(operators.peek()) >= precedence(token[0])) {
                            val b = values.pop()
                            val a = values.pop()
                            values.push(applyOperator(operators.pop(), a, b))
                        }
                        operators.push(token[0])
                    }
                }
            }
            i++
        }

        while (operators.isNotEmpty()) {
            val b = values.pop()
            val a = values.pop()
            values.push(applyOperator(operators.pop(), a, b))
        }

        return formatExpression(values.pop().toString())
    }

    private fun formatExpression(expr: String): String {
        return try {
            val number = BigDecimal(expr)
            if (number.scale() > 0 && number.stripTrailingZeros().scale() <= 0) {
                // Convertir en entier si aucune décimale significative n'existe
                number.toBigInteger().toString()
            } else {
                // Sinon, arrondir à 2 décimales
                number.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            }
        } catch (e: NumberFormatException) {
            expr // Si ce n'est pas un nombre, retourner tel quel
        }
    }

    private fun negateLastNumber() {
        if (expression.isNotEmpty()) {
            val lastOperatorIndex = findLastOperator(expression)
            val startIndex = if (lastOperatorIndex == -1) 0 else lastOperatorIndex + 1

            val lastPart = expression.substring(startIndex)

            val minusCount = lastPart.takeWhile { it == '-' }.count()

            val numberPart = lastPart.dropWhile { it == '-' }

            val newNumber = if (minusCount % 2 == 0) {
                "-$numberPart"
            } else {
                numberPart
            }

            expression = expression.substring(0, startIndex) + newNumber
            display.text = expression
        }
    }


    private fun findLastOperator(expr: String): Int {
        for (i in expr.length - 1 downTo 0) {
            if (expr[i] in "+-*/") {
                // Vérifie si c'est un opérateur et non un signe unaire
                if (i > 0 && expr[i-1].isDigit()) {
                    return i
                }
            }
        }
        return -1
    }
    private fun tokenizeExpression(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var currentNumber = StringBuilder()
        var i = 0

        while (i < expr.length) {
            val c = expr[i]
            when {
                c.isDigit() || c == '.' -> {
                    currentNumber.append(c)
                }
                c in "+-*/%" -> {
                    if (currentNumber.isNotEmpty()) {
                        tokens.add(currentNumber.toString())
                        currentNumber.clear()
                    }
                    tokens.add(c.toString())
                }
            }
            i++
        }

        if (currentNumber.isNotEmpty()) {
            tokens.add(currentNumber.toString())
        }

        return tokens
    }

    private fun applyOperator(op: Char, a: BigDecimal, b: BigDecimal): BigDecimal {
        return when (op) {
            '+' -> a.add(b)
            '-' -> a.subtract(b)
            '*' -> a.multiply(b)
            '/' -> a.divide(b, 10, RoundingMode.HALF_UP)
            '%' -> a.remainder(b)
            else -> throw IllegalArgumentException("Opérateur non supporté: $op")
        }
    }

    private fun precedence(op: Char): Int {
        return when (op) {
            '+', '-' -> 1
            '*', '/', '%' -> 2
            else -> -1
        }
    }

    private fun String.isNumber(): Boolean =
        this.toBigDecimalOrNull() != null

    private fun String.isOperator(): Boolean = this in "+-*/%"

    private inline fun View.setOnDoubleClickListener(crossinline listener: () -> Unit) {
        var lastClickTime: Long = 0
        setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 300) {
                listener()
            }
            lastClickTime = currentTime
        }
    }
}