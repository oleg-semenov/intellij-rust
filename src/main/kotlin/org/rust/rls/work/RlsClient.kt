package org.rust.rls.work

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key

private fun log(data: String) {
    println(data)
}


data class LspPublishDiagnosticsParams(
    val uri: String,
    val diagnostics: List<LspDiagnostic>
)

data class LspDiagnostic(
    val range: LspRange,
    val severity: Int,
    val code: String,
    val message: String
)

data class LspRange(
    val start: LspPosition,
    val end: LspPosition
)

data class LspPosition(
    val line: Int,
    val character: Int
)

data class LspVersionedTextDocumentIdentifier(
    val uri: String,
    val version: Long
)

data class LspTextDocumentContentChangeEvent(
    val range: LspRange?,
    val rangeLength: Int?,
    val text: String
)

data class LspDidChangeTextDocumentParams(
    val textDocument: LspVersionedTextDocumentIdentifier,
    val contentChanges: List<LspTextDocumentContentChangeEvent>
)

private val GSON = Gson()

interface RlsProtocolListener {
    fun onPublishDiagnostics(params: LspPublishDiagnosticsParams) {

    }
}

/**
 * Handles communication with RLS. All methods should be called from a single thread.
 */
class RlsProtocol(
    cmd: GeneralCommandLine,
    private val listener: RlsProtocolListener
) {
    val process: OSProcessHandler


    init {
        process = ContentLengthDelimitedProcessHandler(cmd) { frame ->
            val json = checkNotNull(extractJson(frame)) {
                "RLS produced malformed JSON"
            }
            log("Recv $json")

            val method = json["method"]?.asString
            if (method != null) {
                val params = json["params"]!!
                dispatch(method, params)
            }
        }

        process.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>?) {
                if (outputType == ProcessOutputTypes.STDERR) {
                    log("Err ${event.text}")
                }
            }
        })
    }

    fun callInitialize(projectPath: String) {
        call("initialize", """{"processId": 92, "rootPath": "$projectPath"}""")
    }

    fun notifyTextDocumentDidChange(path: String, version: Long, newContent: String) {
        val params = LspDidChangeTextDocumentParams(
            LspVersionedTextDocumentIdentifier("file://$path", version),
            listOf(LspTextDocumentContentChangeEvent(
                null,
                null,
                newContent
            ))
        )
        call("textDocument/didChange", GSON.toJson(params))
    }

    private inline fun <reified T : Any> handle(params: JsonElement, f: (T) -> Unit) {
        val arg: T = GSON.fromJson(params, T::class.java)
        f(arg)
    }

    private fun dispatch(method: String, params: JsonElement) {

        when (method) {
            "textDocument/publishDiagnostics" -> handle<LspPublishDiagnosticsParams>(params, { listener.onPublishDiagnostics(it) })
        }
    }

    private var requestN: Int = 0
    private fun call(method: String, paramsJson: String) {
        write("""{ "jsonrpc": "2.0", "id": ${requestN++}, "method": "$method", "params": $paramsJson } """)
    }

    private fun extractJson(line: String): JsonObject? {
        if (!line.startsWith('{')) return null
        return JsonParser().parse(line).asJsonObject
    }

    private fun write(data: String) {
        val body = (data).toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
        process.processInput?.apply {
            write(header)
            write(body)
            flush()
            log("Send $data")
        }
    }
}
