package org.example

import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.Rectangle2D
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultCaret
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlin.concurrent.thread

/**
 * Code for a simple terminal emulator that creates a new java swing window.
 * The terminal has one big text area that accepts commands from the user and displays the results.
 */
class Console : JFrame() {

    val textArea = JTextArea("")
    val scrollPane = JScrollPane(textArea)
    var currentDirectory = ""
    var commandStartIndex = 0
    var process: Process? = null
    var threadJob: Thread? = null

    init {
        // Setup the window
        setFocusable(true)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        title = "Terminal"
        isVisible = true
        size = Dimension(1200, 800)
        scrollPane.border = EmptyBorder(5, 5, 5, 5)
        currentDirectory = System.getProperty("user.dir")
        // Setup the text area
        textArea.foreground = Color(255, 255, 255)
        textArea.background = Color(0, 0, 0)
        scrollPane.background = Color(0, 0, 0)
        textArea.font = Font("dialog", NORMAL, 16)
        textArea.caret = TerminalCaret()
        // Setup the input listeners
        textArea.addKeyListener(ConsoleInput(this))
        textArea.addCaretListener(CaretInput(this))
        clearTerminal()
        add(scrollPane)
    }

    fun executeCommand() {
        // Can't have two processes running at once
        if (process != null) return
        val commandText = (textArea.text.substring(commandStartIndex))
        if (commandText.isBlank()) return

        // Start running the command
        // We don't know if the command the user will run changes the current working directory
        // so we add our own pwd command to it afterward to figure it out.
        val command = ProcessBuilder(
            mutableListOf("sh", "-c").apply { add("$commandText&& pwd"); })
        command.directory(File(currentDirectory))
        process = command.start()

        // We want to wait for the command to complete in a new thread
        threadJob = thread {
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process!!.errorStream))

            val result: Int
            try {
                result = process!!.waitFor()
            } catch (e: InterruptedException) {
                // We were canceled by the main thread
                process = null
                return@thread
            }


            if (result != 0) {
                // There was some error running the command
                val errorText = errorReader.readText()
                textArea.text += "\n" + errorText.substring(0, errorText.lastIndexOf('\n'))
                process = null
                newLine()
                return@thread
            }

            var output = reader.readText()
            // Remove the extra newline at the end
            output = output.substring(0, output.lastIndexOf('\n'))
            // Check if pwd was the only output, if so add an extra newline for later
            if (output.indexOf('\n') == -1)
                output = "\n" + output
            else textArea.text += "\n" + output.substring(0, output.lastIndexOf('\n'))
            // The last line of the output will always be the new process working directory
            currentDirectory = output.substring(output.lastIndexOf('\n') + 1)
            newLine()
            process = null
        }
    }

    /**
     * Clears the terminal of all commands and displays the current working directory
     */
    fun clearTerminal() {
        textArea.text = "$currentDirectory$ "
        commandStartIndex = textArea.text.length
    }

    /**
     * Creates a new line with the current working directory
     */
    fun newLine() {
        textArea.text += "\n$currentDirectory$ "
        commandStartIndex = textArea.text.length
        textArea.caret.dot = commandStartIndex
    }
}

class ConsoleInput(val console: Console) : KeyAdapter() {
    // Keep track of if the left control key is held to execute special commands
    var lctrlheld = false

    /**
     * Reads in the key input to the text field before adding the character to the text.
     * Using event.consume() prevents the character from being added to the text area.
     */
    override fun keyPressed(event: KeyEvent) {
        if (event.keyCode == KeyEvent.VK_ENTER) {
            console.executeCommand()
            // Consume the event so that we don't get an extra new line
            event.consume()
        } else if (event.keyCode == KeyEvent.VK_BACK_SPACE) {
            if (console.commandStartIndex == console.textArea.text.length) {
                // We are at the start of the command and shouldn't delete any farther
                event.consume()
            }
        } else if (event.keyCode == KeyEvent.VK_CONTROL) {
            lctrlheld = true
        } else if (event.keyCode == KeyEvent.VK_L && lctrlheld) {
            // Clear the terminal
            console.clearTerminal()
            event.consume()
        } else if (event.keyCode == KeyEvent.VK_C && lctrlheld) {
            // Cancel the command
            if (console.process != null) {
                console.threadJob!!.interrupt()
                console.process!!.destroy()
            }
            else console.newLine()
            event.consume()
        } else if (event.keyCode == KeyEvent.VK_ESCAPE || event.keyCode == KeyEvent.VK_Q && lctrlheld) {
            // Quit the application
            console.dispose()
        }
    }

    override fun keyReleased(event: KeyEvent?) {
        if (event == null) return
        if (event.keyCode == KeyEvent.VK_CONTROL) {
            lctrlheld = false
        }
    }
}

class CaretInput(val console: Console) : CaretListener {
    /**
     * We want to make sure that the user can't move the caret beyond the editable command area.
     */
    override fun caretUpdate(e: CaretEvent?) {
        if (e == null) return
        // If the caret position e.dot is to the left of the editable command area console.commandStartIndex
        // then we should set the cursor to the start of the command area.
        if (e.dot < console.commandStartIndex && console.textArea.text.length >= console.commandStartIndex)
            console.textArea.caretPosition = console.commandStartIndex
    }
}


/**
 * Custom caret class to make the caret more visible.
 * Adapted from http://www.java2s.com/Code/Java/Swing-JFC/Fanciercustomcaretclass.htm
 */
class TerminalCaret : DefaultCaret() {

    init {
        blinkRate = 500
    }

    override fun damage(r: Rectangle?) {
        if (r == null) return
        // Update the position, height and width of the caret
        x = r.x
        y = r.y
        height = r.height
        if (width <= 0)
            width = component.width
        repaint()
    }

    override fun paint(g: Graphics) {
        val dot = dot
        val r: Rectangle2D
        val currentCharacter: Char
        try {
            r = component.modelToView2D(dot)
            if (r == null) return
            currentCharacter = component.getText(dot, 1)[0]
        } catch (e: BadLocationException) {
            return
        }
        // Rectangle2D has positions with type double, so round them to an int, although they should probably only be integers
        if ((x != r.x.roundToInt()) || (y != r.y.roundToInt())) {
            repaint()
            x = r.x.roundToInt()
            y = r.y.roundToInt()
            height = r.height.roundToInt()
        }
        g.color = Color.WHITE
        g.setXORMode(component.background)

        // Change the width of the caret to match the current character
        var width = g.fontMetrics.charWidth(currentCharacter)
        if (width == 0) width = 8
        if (isVisible) g.fillRect(x, y, width, height)
    }
}

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val r = Runnable {
        try {
            Console()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }
    SwingUtilities.invokeLater(r)
}