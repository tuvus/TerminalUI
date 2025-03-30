package org.example

import java.awt.Color
import java.awt.Dimension
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
        scrollPane.border = EmptyBorder(2, 2, 2, 2)
        currentDirectory = System.getProperty("user.dir")
        textArea.text = "$currentDirectory$ "
        commandStartIndex = textArea.text.length
        textArea.foreground = Color(255, 255, 255)
        textArea.background = Color(0, 0, 0)
        scrollPane.background = Color(0, 0, 0)
        textArea.addKeyListener(ConsoleInput(this))
        add(scrollPane)
    }

    fun executeCommand() {
        val command = ProcessBuilder(textArea.text.substring(commandStartIndex))
        command.directory(File(currentDirectory))
        val process = command.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        process.waitFor()

        textArea.text += "\n" + reader.readText()
        newLine()
    }

    fun newLine() {
        textArea.text += "\n$currentDirectory$ "
        commandStartIndex = textArea.text.length
    }
}

class ConsoleInput(val console: Console) : KeyAdapter() {
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
        }
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