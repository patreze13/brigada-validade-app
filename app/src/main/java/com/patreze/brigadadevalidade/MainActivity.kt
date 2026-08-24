package com.patreze.brigadadevalidade

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import android.content.ContentValues
import android.provider.MediaStore

class MainActivity : Activity() {

    private lateinit var codigoAtual: String
    private lateinit var nomeProduto: EditText
    private lateinit var quantidade: EditText
    private lateinit var validade: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        criarTabelaCatalogo()
        criarTabelaProdutos()
        mostrarTelaInicial()
    }

    private fun criarTabelaCatalogo() {
        val db = openOrCreateDatabase(
            "validade.db",
            MODE_PRIVATE,
            null
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalogo_produtos (
                codigo_barras TEXT PRIMARY KEY,
                produto TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.close()
    }

    private fun criarTabelaProdutos() {
        val db = openOrCreateDatabase(
            "validade.db",
            MODE_PRIVATE,
            null
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS produtos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo_barras TEXT NOT NULL,
                produto TEXT NOT NULL,
                quantidade INTEGER NOT NULL,
                validade TEXT NOT NULL,
                brigada_60 TEXT DEFAULT 'PENDENTE',
                brigada_30 TEXT DEFAULT 'PENDENTE',
                brigada_10 TEXT DEFAULT 'PENDENTE',
                situacao_geral TEXT DEFAULT 'ATIVO',
                criado_em TEXT
            )
            """.trimIndent()
        )

        db.close()
    }

    private fun mostrarTelaInicial() {

        val layout = LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.gravity =
            Gravity.CENTER

        layout.setPadding(
            40,
            40,
            40,
            40
        )

        val titulo = TextView(this)

        titulo.text =
            "BRIGADA DE VALIDADE"

        titulo.textSize =
            26f

        titulo.gravity =
            Gravity.CENTER

        layout.addView(titulo)

        adicionarBotao(
            layout,
            "ESCANEAR PRODUTO"
        ) {
            abrirScanner()
        }

        adicionarBotao(
            layout,
            "PRODUTOS CADASTRADOS"
        ) {
            mostrarProdutosCadastrados()
        }

        adicionarBotao(
            layout,
            "BRIGADA 60 DIAS"
        ) {
            mostrarBrigada(60)
        }

        adicionarBotao(
            layout,
            "BRIGADA 30 DIAS"
        ) {
            mostrarBrigada(30)
        }

        status = TextView(this)

        status.text =
            ""

        status.textSize =
            16f

        status.gravity =
            Gravity.CENTER

        layout.addView(status)

        setContentView(layout)
    }

    private fun adicionarBotao(
        layout: LinearLayout,
        texto: String,
        acao: () -> Unit
    ) {

        val botao =
            Button(this)

        botao.text =
            texto

        val parametros =
            LinearLayout.LayoutParams(
                -1,
                -2
            )

        parametros.setMargins(
            0,
            12,
            0,
            12
        )

        layout.addView(
            botao,
            parametros
        )

        botao.setOnClickListener {
            acao()
        }
    }

    private fun abrirScanner() {

        val options =
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()

        val scanner =
            GmsBarcodeScanning.getClient(
                this,
                options
            )

        scanner.startScan()
            .addOnSuccessListener { barcode ->

                codigoAtual =
                    barcode.rawValue ?: ""

                if (
                    codigoAtual.isBlank()
                ) {

                    mostrarCadastro("")

                } else {

                    procurarProdutoLocal(
                        codigoAtual
                    )
                }
            }
            .addOnCanceledListener {

                status.text =
                    "Leitura cancelada"
            }
            .addOnFailureListener {

                status.text =
                    "Erro ao ler código"
            }
    }

    private fun procurarProdutoLocal(
        codigo: String
    ) {

        status.text =
            "Procurando produto..."

        Executors.newSingleThreadExecutor()
            .execute {

                var nomeEncontrado =
                    ""

                try {

                    val db =
                        openOrCreateDatabase(
                            "validade.db",
                            MODE_PRIVATE,
                            null
                        )

                    val cursor =
                        db.rawQuery(
                            """
                            SELECT produto
                            FROM catalogo_produtos
                            WHERE codigo_barras = ?
                            LIMIT 1
                            """.trimIndent(),
                            arrayOf(codigo)
                        )

                    if (
                        cursor.moveToFirst()
                    ) {

                        nomeEncontrado =
                            cursor.getString(0)
                    }

                    cursor.close()
                    db.close()

                } catch (_: Exception) {
                }

                runOnUiThread {

                    if (
                        nomeEncontrado.isNotBlank()
                    ) {

                        status.text =
                            "Produto encontrado no catálogo"

                        mostrarCadastro(
                            nomeEncontrado
                        )

                    } else {

                        consultarKodebar(
                            codigo
                        )
                    }
                }
            }
    }

    private fun consultarKodebar(
        codigo: String
    ) {

        runOnUiThread {

            status.text =
                "Consultando produto..."
        }

        Executors.newSingleThreadExecutor()
            .execute {

                try {

                    val url =
                        URL(
                            "https://kodebar.korvensistemas.com.br/gtin/lookup?gtin=$codigo"
                        )

                    val conexao =
                        url.openConnection()
                            as HttpURLConnection

                    conexao.requestMethod =
                        "GET"

                    conexao.connectTimeout =
                        15000

                    conexao.readTimeout =
                        15000

                    conexao.setRequestProperty(
                        "X-API-Key",
                        BuildConfig.KODEBAR_API_KEY
                    )

                    val respostaHttp =
                        conexao.responseCode

                    if (
                        respostaHttp ==
                        HttpURLConnection.HTTP_OK
                    ) {

                        val resposta =
                            conexao.inputStream
                                .bufferedReader()
                                .use {
                                    it.readText()
                                }

                        conexao.disconnect()

                        val json =
                            JSONObject(
                                resposta
                            )

                        val nome =
                            json.optString(
                                "nome",
                                ""
                            )

                        val marca =
                            json.optString(
                                "marca",
                                ""
                            )

                        val nomeFinal =
                            when {

                                nome.isNotBlank() &&
                                    marca.isNotBlank() ->
                                    "$nome - $marca"

                                nome.isNotBlank() ->
                                    nome

                                else ->
                                    ""
                            }

                        if (
                            nomeFinal.isNotBlank()
                        ) {

                            runOnUiThread {

                                mostrarCadastro(
                                    nomeFinal
                                )
                            }

                        } else {

                            consultarOpenFoodFacts(
                                codigo
                            )
                        }

                    } else {

                        conexao.disconnect()

                        consultarOpenFoodFacts(
                            codigo
                        )
                    }

                } catch (_: Exception) {

                    consultarOpenFoodFacts(
                        codigo
                    )
                }
            }
    }

    private fun consultarOpenFoodFacts(
        codigo: String
    ) {

        runOnUiThread {

            status.text =
                "Tentando segunda fonte..."
        }

        Executors.newSingleThreadExecutor()
            .execute {

                try {

                    val url =
                        URL(
                            "https://world.openfoodfacts.org/api/v3/product/$codigo" +
                                ".json?fields=code,product_name,product_name_pt,brands,quantity"
                        )

                    val conexao =
                        url.openConnection()
                            as HttpURLConnection

                    conexao.requestMethod =
                        "GET"

                    conexao.connectTimeout =
                        15000

                    conexao.readTimeout =
                        15000

                    conexao.setRequestProperty(
                        "User-Agent",
                        "BrigadaDeValidade/0.6 (Android)"
                    )

                    val respostaHttp =
                        conexao.responseCode

                    if (
                        respostaHttp ==
                        HttpURLConnection.HTTP_OK
                    ) {

                        val resposta =
                            conexao.inputStream
                                .bufferedReader()
                                .use {
                                    it.readText()
                                }

                        conexao.disconnect()

                        val json =
                            JSONObject(
                                resposta
                            )

                        if (
                            json.optInt(
                                "status",
                                0
                            ) == 1
                        ) {

                            val produto =
                                json.optJSONObject(
                                    "product"
                                )

                            var nome =
                                ""

                            if (
                                produto != null
                            ) {

                                nome =
                                    produto.optString(
                                        "product_name_pt",
                                        ""
                                    )

                                if (
                                    nome.isBlank()
                                ) {

                                    nome =
                                        produto.optString(
                                            "product_name",
                                            ""
                                        )
                                }

                                val marca =
                                    produto.optString(
                                        "brands",
                                        ""
                                    )

                                if (
                                    nome.isNotBlank() &&
                                    marca.isNotBlank()
                                ) {

                                    nome =
                                        "$nome - $marca"
                                }
                            }

                            runOnUiThread {

                                mostrarCadastro(
                                    nome
                                )
                            }

                        } else {

                            runOnUiThread {

                                mostrarCadastro("")
                            }
                        }

                    } else {

                        conexao.disconnect()

                        runOnUiThread {

                            mostrarCadastro("")
                        }
                    }

                } catch (_: Exception) {

                    runOnUiThread {

                        mostrarCadastro("")
                    }
                }
            }
    }

    private fun mostrarCadastro(
        nomeEncontrado: String
    ) {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            40,
            30,
            40,
            30
        )

        val titulo =
            TextView(this)

        titulo.text =
            if (
                nomeEncontrado.isNotBlank()
            ) {

                "PRODUTO ENCONTRADO"

            } else {

                "PRODUTO NÃO ENCONTRADO"
            }

        titulo.textSize =
            22f

        layout.addView(titulo)

        val codigo =
            TextView(this)

        codigo.text =
            "Código: $codigoAtual"

        codigo.textSize =
            16f

        layout.addView(codigo)

        nomeProduto =
            EditText(this)

        nomeProduto.hint =
            "Nome do produto"

        nomeProduto.setText(
            nomeEncontrado
        )

        layout.addView(
            nomeProduto
        )

        quantidade =
            EditText(this)

        quantidade.hint =
            "Quantidade"

        quantidade.inputType =
            2

        layout.addView(
            quantidade
        )

        validade =
            EditText(this)

        validade.hint =
            "Validade (DD/MM/AAAA)"

        validade.inputType =
            2

        validade.filters =
            arrayOf(
                InputFilter.LengthFilter(
                    10
                )
            )

        validade.addTextChangedListener(
            object : TextWatcher {

                private var alterando =
                    false

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {}

                override fun afterTextChanged(
                    s: Editable?
                ) {

                    if (
                        alterando ||
                        s == null
                    ) {
                        return
                    }

                    val numeros =
                        s.toString()
                            .replace(
                                "/",
                                ""
                            )

                    if (
                        numeros.length > 8
                    ) {
                        return
                    }

                    val formatado =
                        StringBuilder()

                    for (
                        i in numeros.indices
                    ) {

                        if (
                            i == 2 ||
                            i == 4
                        ) {

                            formatado.append(
                                "/"
                            )
                        }

                        formatado.append(
                            numeros[i]
                        )
                    }

                    val novoTexto =
                        formatado.toString()

                    if (
                        novoTexto !=
                        s.toString()
                    ) {

                        alterando = true

                        validade.setText(
                            novoTexto
                        )

                        validade.setSelection(
                            novoTexto.length
                        )

                        alterando = false
                    }
                }
            }
        )

        layout.addView(
            validade
        )

        val salvar =
            Button(this)

        salvar.text =
            "SALVAR PRODUTO"

        salvar.setOnClickListener {

            salvarProduto()
        }

        layout.addView(
            salvar
        )

        val voltar =
            Button(this)

        voltar.text =
            "VOLTAR"

        voltar.setOnClickListener {

            mostrarTelaInicial()
        }

        layout.addView(
            voltar
        )

        setContentView(
            layout
        )
    }

    private fun salvarProduto() {

        val nome =
            nomeProduto.text
                .toString()
                .trim()

        val qtdTexto =
            quantidade.text
                .toString()
                .trim()

        val validadeTexto =
            validade.text
                .toString()
                .trim()

        if (
            nome.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Digite o nome do produto",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val quantidadeInt =
            qtdTexto.toIntOrNull()

        if (
            quantidadeInt == null ||
            quantidadeInt <= 0
        ) {

            Toast.makeText(
                this,
                "Quantidade inválida",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val validadeFormatada =
            converterData(
                validadeTexto
            )

        if (
            validadeFormatada == null
        ) {

            Toast.makeText(
                this,
                "Data inválida. Use DD/MM/AAAA",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        salvarSQLite(
            codigoAtual,
            nome,
            quantidadeInt,
            validadeFormatada
        )
    }

    private fun converterData(
        data: String
    ): String? {

        return try {

            val entrada =
                SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.US
                )

            entrada.isLenient =
                false

            val dataConvertida =
                entrada.parse(data)

            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(
                dataConvertida!!
            )

        } catch (_: Exception) {

            null
        }
    }

    private fun salvarSQLite(
        codigo: String,
        nome: String,
        quantidade: Int,
        validade: String
    ) {

        val db =
            openOrCreateDatabase(
                "validade.db",
                MODE_PRIVATE,
                null
            )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS produtos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo_barras TEXT NOT NULL,
                produto TEXT NOT NULL,
                quantidade INTEGER NOT NULL,
                validade TEXT NOT NULL,
                brigada_60 TEXT DEFAULT 'PENDENTE',
                brigada_30 TEXT DEFAULT 'PENDENTE',
                brigada_10 TEXT DEFAULT 'PENDENTE',
                situacao_geral TEXT DEFAULT 'ATIVO',
                criado_em TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalogo_produtos (
                codigo_barras TEXT PRIMARY KEY,
                produto TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT OR REPLACE INTO catalogo_produtos (
                codigo_barras,
                produto
            )
            VALUES (?, ?)
            """.trimIndent(),
            arrayOf(
                codigo,
                nome
            )
        )

        db.execSQL(
            """
            INSERT INTO produtos (
                codigo_barras,
                produto,
                quantidade,
                validade,
                criado_em
            )
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                codigo,
                nome,
                quantidade,
                validade,
                Date().toString()
            )
        )

        db.close()

        Toast.makeText(
            this,
            "Produto cadastrado com sucesso",
            Toast.LENGTH_LONG
        ).show()

        mostrarTelaInicial()
    }

    private fun mostrarProdutosCadastrados() {

        val layout =
            criarLayoutInterno(
                "PRODUTOS CADASTRADOS"
            )

        val db =
            openOrCreateDatabase(
                "validade.db",
                MODE_PRIVATE,
                null
            )

        val cursor =
            db.rawQuery(
                """
                SELECT
                    codigo_barras,
                    produto
                FROM catalogo_produtos
                ORDER BY produto COLLATE NOCASE ASC
                """.trimIndent(),
                null
            )

        if (!cursor.moveToFirst()) {

            adicionarTexto(
                layout,
                "Nenhum produto cadastrado."
            )

        } else {

            do {

                val codigo =
                    cursor.getString(0)

                val produto =
                    cursor.getString(1)

                val botao =
                    Button(this)

                botao.text =
                    "$produto\nEAN: $codigo"

                botao.setOnClickListener {

                    mostrarHistoricoProduto(
                        codigo,
                        produto
                    )
                }

                layout.addView(
                    botao
                )

            } while (
                cursor.moveToNext()
            )
        }

        cursor.close()
        db.close()

        adicionarBotaoVoltar(
            layout
        )

        setContentView(
            layout
        )
    }

    private fun mostrarHistoricoProduto(
        codigo: String,
        produto: String
    ) {

        val layout =
            criarLayoutInterno(
                produto
            )

        adicionarTexto(
            layout,
            "Código de barras: $codigo"
        )

        val db =
            openOrCreateDatabase(
                "validade.db",
                MODE_PRIVATE,
                null
            )

        val cursor =
            db.rawQuery(
                """
                SELECT
                    quantidade,
                    validade
                FROM produtos
                WHERE codigo_barras = ?
                ORDER BY validade ASC
                """.trimIndent(),
                arrayOf(codigo)
            )

        if (!cursor.moveToFirst()) {

            adicionarTexto(
                layout,
                "Nenhum registro de validade."
            )

        } else {

            do {

                val quantidade =
                    cursor.getInt(0)

                val validade =
                    cursor.getString(1)

                adicionarTexto(
                    layout,
                    "Quantidade: $quantidade\nValidade: ${formatarDataBanco(validade)}"
                )

            } while (
                cursor.moveToNext()
            )
        }

        cursor.close()
        db.close()

        adicionarBotaoVoltar(
            layout
        )

        setContentView(
            layout
        )
    }

    private fun mostrarBrigada(
        diasMaximos: Int
    ) {

        val titulo =
            "BRIGADA $diasMaximos DIAS"

        val layout =
            criarLayoutInterno(
                titulo
            )

        val registros =
            buscarBrigada(
                diasMaximos
            )

        if (
            registros.isEmpty()
        ) {

            adicionarTexto(
                layout,
                "Nenhum produto dentro deste prazo."
            )

        } else {

            for (
                registro in registros
            ) {

                adicionarTexto(
                    layout,
                    registro.texto
                )
            }

            val exportar =
                Button(this)

            exportar.text =
                "EXPORTAR PARA EXCEL"

            exportar.setOnClickListener {

                exportarBrigadaExcel(
                    diasMaximos,
                    registros
                )
            }

            layout.addView(
                exportar
            )
        }

        adicionarBotaoVoltar(
            layout
        )

        setContentView(
            layout
        )
    }

    data class RegistroBrigada(
        val produto: String,
        val codigo: String,
        val quantidade: Int,
        val validade: String,
        val diasRestantes: Long
    ) {

        val texto: String
            get() =
                "$produto\n" +
                    "Código: $codigo\n" +
                    "Quantidade: $quantidade\n" +
                    "Validade: ${formatarDataExterna(validade)}\n" +
                    "Dias para vencer: $diasRestantes"
    }

    private fun buscarBrigada(
        diasMaximos: Int
    ): List<RegistroBrigada> {

        val lista =
            mutableListOf<RegistroBrigada>()

        val hoje =
            Calendar.getInstance()

        hoje.set(
            Calendar.HOUR_OF_DAY,
            0
        )

        hoje.set(
            Calendar.MINUTE,
            0
        )

        hoje.set(
            Calendar.SECOND,
            0
        )

        hoje.set(
            Calendar.MILLISECOND,
            0
        )

        val limite =
            Calendar.getInstance()

        limite.timeInMillis =
            hoje.timeInMillis

        limite.add(
            Calendar.DAY_OF_YEAR,
            diasMaximos
        )

        val db =
            openOrCreateDatabase(
                "validade.db",
                MODE_PRIVATE,
                null
            )

        val cursor =
            db.rawQuery(
                """
                SELECT
                    produto,
                    codigo_barras,
                    quantidade,
                    validade
                FROM produtos
                WHERE situacao_geral = 'ATIVO'
                ORDER BY validade ASC
                """.trimIndent(),
                null
            )

        if (
            cursor.moveToFirst()
        ) {

            do {

                val produto =
                    cursor.getString(0)

                val codigo =
                    cursor.getString(1)

                val quantidade =
                    cursor.getInt(2)

                val validade =
                    cursor.getString(3)

                try {

                    val data =
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                        ).parse(
                            validade
                        )

                    if (data != null) {

                        val vencimento =
                            Calendar.getInstance()

                        vencimento.time =
                            data

                        vencimento.set(
                            Calendar.HOUR_OF_DAY,
                            0
                        )

                        vencimento.set(
                            Calendar.MINUTE,
                            0
                        )

                        vencimento.set(
                            Calendar.SECOND,
                            0
                        )

                        vencimento.set(
                            Calendar.MILLISECOND,
                            0
                        )

                        if (
                            !vencimento.before(
                                hoje
                            ) &&
                            !vencimento.after(
                                limite
                            )
                        ) {

                            val diferenca =
                                (
                                    vencimento.timeInMillis -
                                        hoje.timeInMillis
                                    ) /
                                    (
                                        24L *
                                            60L *
                                            60L *
                                            1000L
                                    )

                            lista.add(
                                RegistroBrigada(
                                    produto,
                                    codigo,
                                    quantidade,
                                    validade,
                                    diferenca
                                )
                            )
                        }
                    }

                } catch (_: Exception) {
                }

            } while (
                cursor.moveToNext()
            )
        }

        cursor.close()
        db.close()

        return lista.sortedBy {
            it.diasRestantes
        }
    }

    private fun formatarDataExterna(
        data: String
    ): String {

        return try {

            SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                ).parse(data)!!
            )

        } catch (e: Exception) {

            data
        }
    }

    private fun exportarBrigadaExcel(
        diasMaximos: Int,
        registros: List<RegistroBrigada>
    ) {

        Executors.newSingleThreadExecutor().execute {

            try {

                val nomeArquivo =
                    "Brigada_${diasMaximos}_dias_" +
                        SimpleDateFormat(
                            "yyyyMMdd_HHmm",
                            Locale.getDefault()
                        ).format(Date()) +
                        ".csv"

                val csv = StringBuilder()

                csv.append("BRIGADA ${diasMaximos} DIAS\n")
                csv.append(
                    "Gerado em:;" +
                        SimpleDateFormat(
                            "dd/MM/yyyy HH:mm",
                            Locale.getDefault()
                        ).format(Date()) +
                        "\n\n"
                )

                csv.append(
                    "Produto;Código de barras;Quantidade;Validade;Dias para vencer\n"
                )

                for (registro in registros) {

                    csv.append(
                        "\"${registro.produto.replace("\"", "\"\"")}\";"
                    )

                    csv.append(
                        "\"${registro.codigo}\";"
                    )

                    csv.append(
                        "${registro.quantidade};"
                    )

                    csv.append(
                        "\"${formatarDataExterna(registro.validade)}\";"
                    )

                    csv.append(
                        "${registro.diasRestantes}\n"
                    )
                }

                val bytes =
                    ("\uFEFF" + csv.toString())
                        .toByteArray(Charsets.UTF_8)

                if (
                    android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.Q
                ) {

                    val values =
                        ContentValues().apply {

                            put(
                                MediaStore.Downloads.DISPLAY_NAME,
                                nomeArquivo
                            )

                            put(
                                MediaStore.Downloads.MIME_TYPE,
                                "text/csv"
                            )

                            put(
                                MediaStore.Downloads.RELATIVE_PATH,
                                "Download"
                            )
                        }

                    val uri =
                        contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                        )

                    if (uri == null) {
                        throw Exception(
                            "Não foi possível criar o arquivo."
                        )
                    }

                    contentResolver
                        .openOutputStream(uri)
                        .use { saida ->

                            if (saida == null) {
                                throw Exception(
                                    "Não foi possível gravar o arquivo."
                                )
                            }

                            saida.write(bytes)
                        }

                    runOnUiThread {
                        compartilharArquivo(
                            uri,
                            nomeArquivo
                        )
                    }

                } else {

                    runOnUiThread {

                        Toast.makeText(
                            this,
                            "A exportação requer Android 10 ou superior.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "Erro ao gerar arquivo: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun compartilharArquivo(
        uri: android.net.Uri,
        nomeArquivo: String
    ) {

        val intent =
            Intent(
                Intent.ACTION_SEND
            )

        intent.type =
            "text/csv"

        intent.putExtra(
            Intent.EXTRA_STREAM,
            uri
        )

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        startActivity(
            Intent.createChooser(
                intent,
                "Enviar $nomeArquivo"
            )
        )
    }

    private fun criarLayoutInterno(
        tituloTexto: String
    ): LinearLayout {

        val layout =
            LinearLayout(this)

        layout.orientation =
            LinearLayout.VERTICAL

        layout.setPadding(
            30,
            30,
            30,
            30
        )

        val titulo =
            TextView(this)

        titulo.text =
            tituloTexto

        titulo.textSize =
            24f

        titulo.gravity =
            Gravity.CENTER

        layout.addView(
            titulo
        )

        return layout
    }

    private fun adicionarTexto(
        layout: LinearLayout,
        texto: String
    ) {

        val campo =
            TextView(this)

        campo.text =
            texto

        campo.textSize =
            16f

        campo.setPadding(
            10,
            15,
            10,
            15
        )

        layout.addView(
            campo
        )
    }

    private fun adicionarBotaoVoltar(
        layout: LinearLayout
    ) {

        val voltar =
            Button(this)

        voltar.text =
            "VOLTAR"

        voltar.setOnClickListener {

            mostrarTelaInicial()
        }

        layout.addView(
            voltar
        )
    }

    private fun formatarDataBanco(
        data: String
    ): String {

        return try {

            val entrada =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                )

            val convertida =
                entrada.parse(data)

            SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(
                convertida!!
            )

        } catch (_: Exception) {

            data
        }
    }

    private fun formatarDataExterna(
        data: String
    ): String {

        return formatarDataBanco(
            data
        )
    }
}
