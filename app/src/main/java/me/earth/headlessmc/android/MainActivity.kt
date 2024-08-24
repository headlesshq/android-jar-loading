package me.earth.headlessmc.android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.earth.headlessmc.android.ui.theme.HMCAndroidTheme
import me.earth.headlessmc.api.process.ReadablePrintStream
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = PipedOutputStream()
        val inPipe = PipedInputStream(input)

        Log.i("hmc", "Intializing")
        val output = PipedInputStream()
        val outPipe = PipedOutputStream(output)

        Thread {
            HmcAndroidLauncher().launch(applicationContext, inPipe, outPipe)
        }.start()

        setContent {
            HMCAndroidTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConsoleView(PrintStream(input), output, PrintStream(outPipe))
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ConsoleView(hmcInput: PrintStream, hmcOutput: InputStream, hmcOutputPrintStream: PrintStream) {
    var outputText by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var wasMax by remember { mutableStateOf(true) }
    var scrollerRan by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val shouldAutoScroll = remember {
        derivedStateOf {
            scrollState.value == scrollState.maxValue
        }
    }

    LaunchedEffect(hmcOutput) {
        coroutineScope.launch(Dispatchers.IO) {
            Log.i("hmc-out", "Started Output Coroutine")
            val reader = hmcOutput.bufferedReader()
            try {
                var readLine: String? = reader.readLine()
                while (readLine != null) {
                    val line = readLine
                    Log.i("hmc-out", line)
                    coroutineScope.launch(Dispatchers.Main) {
                        wasMax = shouldAutoScroll.value || !scrollerRan && wasMax
                        if (outputText.length > 100_000) {
                            outputText = "$line\n"
                        } else {
                            outputText += "$line\n"
                        }

                        scrollerRan = false
                    }

                    readLine = reader.readLine()
                }
            } catch (e: IOException) {
                Log.e("hmc", "Error " + e.message)
                coroutineScope.launch(Dispatchers.Main) {
                    outputText += "HeadlessMc Thread ended (${e.message})\n"
                }
            } finally {
                Log.e("hmc", "Closing reader!")
                reader.close()
            }
        }
    }

    LaunchedEffect(outputText) {
        coroutineScope.launch(Dispatchers.Main) {
            if (wasMax) {
                scrollState.scrollTo(scrollState.maxValue)
            }

            scrollerRan = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .imePadding()
    ) {
        Column (
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = outputText,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .horizontalScroll(horizontalScrollState)
            )

            BasicTextField(
                value = inputText,
                textStyle = LocalTextStyle.current.merge(TextStyle(color = LocalContentColor.current)), // WTF WHY DO I HAVE TO DO THIS????
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray)
                    .padding(8.dp),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        coroutineScope.launch(Dispatchers.Main) {
                            if ("scroll".equals(inputText.text, ignoreCase = false)) {
                                scrollState.scrollTo(scrollState.maxValue)
                            } else if ("clear".equals(inputText.text, ignoreCase = false)) {
                                outputText = ""
                            } else {
                                hmcOutputPrintStream.println(inputText.text)
                                hmcInput.println(inputText.text)
                            }

                            inputText = TextFieldValue("")
                            keyboardController?.hide()
                        }
                    }
                )
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HMCAndroidTheme {
        ConsoleView(ReadablePrintStream(), PipedInputStream(), System.out)
    }
}
