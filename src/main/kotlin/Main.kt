package org.example

import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
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


class Console : JFrame() {

    val textArea = JTextArea("sadfasfd")
    val scrollPane = JScrollPane(textArea)
    var currentDirectory = ""
    var commandStartIndex = 0

    init {
        setFocusable(true)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        title = "CSE"
        isVisible = true
        size = Dimension(1200, 800)
        scrollPane.border = EmptyBorder(5, 5, 5, 5)
        currentDirectory = System.getProperty("user.dir")
        textArea.foreground = Color(255, 255, 255)
        textArea.background = Color(0, 0, 0)
        scrollPane.background = Color(0, 0, 0)
        textArea.addKeyListener(ConsoleInput(this))
        textArea.addCaretListener(CaretInput(this))
        textArea.font = Font("dialog", NORMAL, 16)
        clearTerminal()
        add(scrollPane)
    }

    fun executeCommand() {
        val command = ProcessBuilder(
            mutableListOf("sh", "-c").apply { add((textArea.text.substring(commandStartIndex)) + "&& pwd"); })
        command.directory(File(currentDirectory))
        val process = command.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        if (process.waitFor() != 0) {
            var errorText = errorReader.readText()
            textArea.text += "\n" + errorText.substring(0, errorText.lastIndexOf('\n'))
            newLine()
            return
        }

        var output = reader.readText()
        // Remove the extra newline at the end
        output = output.substring(0, output.lastIndexOf('\n'))
        // Check if pwd was the only output, if so add an extra newline for later
        if (output.indexOf('\n') == -1)
            output = "\n" + output
        else textArea.text += "\n" + output.substring(0, output.lastIndexOf('\n'))
        currentDirectory = output.substring(output.lastIndexOf('\n') + 1)
        newLine()
    }

    fun clearTerminal() {
        textArea.text = "$currentDirectory$ "
        commandStartIndex = textArea.text.length
    }

    fun newLine() {
        textArea.text += "\n$currentDirectory$ "
        commandStartIndex = textArea.text.length
    }
}

class ConsoleInput(val console: Console) : KeyAdapter() {
    var lctrlheld = false
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
            console.clearTerminal()
            event.consume()
        } else if (event.keyCode == KeyEvent.VK_C && lctrlheld) {
            console.newLine()
            event.consume()
        } else if (event.keyCode == KeyEvent.VK_ESCAPE) {
            console.dispose()
        } else if (event.keyCode == KeyEvent.VK_Q && lctrlheld) {
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
    override fun caretUpdate(e: CaretEvent?) {
        if (e == null) return
        if (e.dot < console.commandStartIndex && console.textArea.text.length >= console.commandStartIndex)
            console.textArea.caretPosition = console.commandStartIndex
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