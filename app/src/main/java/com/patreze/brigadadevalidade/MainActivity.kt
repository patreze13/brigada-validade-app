package com.patreze.brigadadevalidade

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var codigoAtual: String
    private lateinit var nomeProduto: EditText
    private lateinit var quantidade: EditText
    private lateinit var validade: EditText
    private lateinit var status: TextView

    // Paleta de Cores Dark Mode
    private val corFundoApp = Color.parseColor("#121212")
    private val corCardFundo = Color.parseColor("#1E1E1E")
    private val corBordaBranca = Color.parseColor("#FFFFFF")
    private val corTextoPrincipal = Color.parseColor("#FFFFFF")
    private val corTextoSecundario = Color.parseColor("#B0BEC5")
    private val corCriticaVermelha = Color.parseColor("#FF5252")
    private val corCriticaAmarela = Color.parseColor("#FFB300")
    private val corCriticaVerde = Color.parseColor("#69F0AE")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        criarTabelaCatalogo()
        criarTabelaProdutos()

        mostrarTelaInicial()
    }

    private fun criarTabelaCatalogo() {
        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
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
        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
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
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(corFundoApp)
            setPadding(40, 60, 40, 60)
        }

        val titulo = TextView(this).apply {
            text = "BRIGADA DE VALIDADE"
            textSize = 26f
            setTextColor(corTextoPrincipal)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val paramsTitulo = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 50
        }
        layout.addView(titulo, paramsTitulo)

        adicionarBotaoPrincipal(layout, "ESCANEAR PRODUTO") {
            abrirScanner()
        }

        adicionarBotaoPrincipal(layout, "PRODUTOS CADASTRADOS") {
            mostrarProdutosCadastrados()
        }

        adicionarBotaoPrincipal(layout, "BRIGADA 60 DIAS") {
            mostrarBrigada(60)
        }

        adicionarBotaoPrincipal(layout, "BRIGADA 30 DIAS") {
            mostrarBrigada(30)
        }

        status = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(corTextoSecundario)
            gravity = Gravity.CENTER
        }

        val paramsStatus = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 30
        }
        layout.addView(status, paramsStatus)

        setContentView(layout)
    }

    private fun adicionarBotaoPrincipal(
        layout: LinearLayout,
        texto: String,
        acao: () -> Unit
    ) {
        val botao = Button(this).apply {
            text = texto
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(corBordaBranca)
                cornerRadius = 16f
            }
            setOnClickListener { acao() }
        }

        val parametros = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 15, 0, 15)
        }

        layout.addView(botao, parametros)
    }

    private fun abrirScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                codigoAtual = barcode.rawValue ?: ""
                if (codigoAtual.isBlank()) {
                    mostrarCadastro("")
                } else {
                    consultarProduto(codigoAtual)
                }
            }
            .addOnCanceledListener {
                if (::status.isInitialized) {
                    status.text = "Leitura cancelada"
                }
            }
            .addOnFailureListener {
                if (::status.isInitialized) {
                    status.text = "Erro ao ler código"
                }
            }
    }

    private fun consultarProduto(codigo: String) {
        status.text = "Consultando produto..."
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            val nomeCatalogo = consultarCatalogoLocal(codigo)
            if (nomeCatalogo.isNotBlank()) {
                runOnUiThread {
                    mostrarCadastro(nomeCatalogo)
                }
                executor.shutdown()
                return@execute
            }

            try {
                val url = URL("https://kodebar.korvensistemas.com.br/gtin/lookup?gtin=$codigo")
                val conexao = url.openConnection() as HttpURLConnection
                conexao.requestMethod = "GET"
                conexao.connectTimeout = 15000
                conexao.readTimeout = 15000
                conexao.setRequestProperty("X-API-Key", BuildConfig.KODEBAR_API_KEY)

                val codigoHttp = conexao.responseCode

                if (codigoHttp == HttpURLConnection.HTTP_OK) {
                    val resposta = conexao.inputStream.bufferedReader().use { it.readText() }
                    conexao.disconnect()

                    val json = JSONObject(resposta)
                    val nome = json.optString("nome", "")
                    val marca = json.optString("marca", "")

                    val nomeFinal = when {
                        nome.isNotBlank() && marca.isNotBlank() -> "$nome - $marca"
                        nome.isNotBlank() -> nome
                        else -> ""
                    }

                    if (nomeFinal.isNotBlank()) {
                        salvarCatalogo(codigo, nomeFinal)
                        runOnUiThread {
                            mostrarCadastro(nomeFinal)
                        }
                    } else {
                        consultarOpenFoodFacts(codigo)
                    }
                } else {
                    conexao.disconnect()
                    consultarOpenFoodFacts(codigo)
                }
            } catch (_: Exception) {
                consultarOpenFoodFacts(codigo)
            }
        }
    }

    private fun consultarCatalogoLocal(codigo: String): String {
        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
        val cursor = db.rawQuery(
            """
            SELECT produto FROM catalogo_produtos
            WHERE codigo_barras = ? LIMIT 1
            """.trimIndent(),
            arrayOf(codigo)
        )

        var resultado = ""
        if (cursor.moveToFirst()) {
            resultado = cursor.getString(0)
        }

        cursor.close()
        db.close()
        return resultado
    }

    private fun salvarCatalogo(codigo: String, produto: String) {
        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
        db.execSQL(
            """
            INSERT OR REPLACE INTO catalogo_produtos (codigo_barras, produto)
            VALUES (?, ?)
            """.trimIndent(),
            arrayOf(codigo, produto)
        )
        db.close()
    }

    private fun consultarOpenFoodFacts(codigo: String) {
        runOnUiThread {
            status.text = "Produto não encontrado automaticamente. Informe o nome."
        }

        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL(
                    "https://world.openfoodfacts.org/api/v3/product/" +
                        "$codigo.json?fields=code,product_name,product_name_pt,brands,quantity"
                )
                val conexao = url.openConnection() as HttpURLConnection
                conexao.requestMethod = "GET"
                conexao.connectTimeout = 15000
                conexao.readTimeout = 15000
                conexao.setRequestProperty("User-Agent", "BrigadaDeValidade/0.5 (Android)")

                val codigoHttp = conexao.responseCode
                if (codigoHttp == HttpURLConnection.HTTP_OK) {
                    val resposta = conexao.inputStream.bufferedReader().use { it.readText() }
                    conexao.disconnect()

                    val json = JSONObject(resposta)
                    var nome = ""

                    if (json.optInt("status", 0) == 1) {
                        val produto = json.optJSONObject("product")
                        if (produto != null) {
                            nome = produto.optString("product_name_pt", "")
                            if (nome.isBlank()) {
                                nome = produto.optString("product_name", "")
                            }
                            val marca = produto.optString("brands", "")
                            if (nome.isNotBlank() && marca.isNotBlank()) {
                                nome = "$nome - $marca"
                            }
                        }
                    }

                    if (nome.isNotBlank()) {
                        salvarCatalogo(codigo, nome)
                    }

                    runOnUiThread {
                        mostrarCadastro(nome)
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

    private fun mostrarCadastro(nomeEncontrado: String) {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(corFundoApp)
            setPadding(30, 40, 30, 30)
        }

        val titulo = TextView(this).apply {
            text = if (nomeEncontrado.isNotBlank()) "PRODUTO ENCONTRADO" else "NOVO PRODUTO"
            textSize = 22f
            setTextColor(corTextoPrincipal)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val paramsTitulo = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 25 }
        raiz.addView(titulo, paramsTitulo)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(corCardFundo)
                setStroke(3, corBordaBranca)
                cornerRadius = 16f
            }
            setPadding(30, 30, 30, 30)
        }

        val codigo = TextView(this).apply {
            text = "Código: $codigoAtual"
            textSize = 15f
            setTextColor(corTextoSecundario)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(codigo)

        nomeProduto = criarCampoTexto("Nome do produto", nomeEncontrado, 1)
        card.addView(nomeProduto)

        quantidade = criarCampoTexto("Quantidade", "", 2)
        card.addView(quantidade)

        validade = criarCampoTexto("Validade (DD/MM/AAAA)", "", 2).apply {
            filters = arrayOf(InputFilter.LengthFilter(10))
        }
        configurarMascaraData()
        card.addView(validade)

        val paramsCard = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 25 }
        raiz.addView(card, paramsCard)

        val salvar = Button(this).apply {
            text = "SALVAR PRODUTO"
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(corBordaBranca)
                cornerRadius = 14f
            }
            setOnClickListener { salvarProduto() }
        }
        val paramsSalvar = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 15 }
        raiz.addView(salvar, paramsSalvar)

        adicionarBotaoVoltar(raiz)
        setContentView(raiz)
    }

    private fun criarCampoTexto(dica: String, textoInicial: String, tipoInput: Int): EditText {
        return EditText(this).apply {
            hint = dica
            setHintTextColor(Color.parseColor("#757575"))
            setTextColor(corTextoPrincipal)
            setText(textoInicial)
            inputType = tipoInput
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                setStroke(2, Color.parseColor("#444444"))
                cornerRadius = 10f
            }
            setPadding(24, 24, 24, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 18
            }
            layoutParams = params
        }
    }

    private fun configurarMascaraData() {
        validade.addTextChangedListener(object : TextWatcher {
            private var alterando = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (alterando || s == null) {
                    return
                }

                val numeros = s.toString().replace("/", "").filter { it.isDigit() }
                if (numeros.length > 8) {
                    return
                }

                val formatado = StringBuilder()
                for (i in numeros.indices) {
                    if (i == 2 || i == 4) {
                        formatado.append("/")
                    }
                    formatado.append(numeros[i])
                }

                val novoTexto = formatado.toString()
                if (novoTexto != s.toString()) {
                    alterando = true
                    validade.setText(novoTexto)
                    validade.setSelection(novoTexto.length)
                    alterando = false
                }
            }
        })
    }

    private fun salvarProduto() {
        val nome = nomeProduto.text.toString().trim()
        val quantidadeTexto = quantidade.text.toString().trim()
        val validadeTexto = validade.text.toString().trim()

        if (nome.isEmpty()) {
            Toast.makeText(this, "Digite o nome do produto", Toast.LENGTH_SHORT).show()
            return
        }

        val quantidadeInt = quantidadeTexto.toIntOrNull()
        if (quantidadeInt == null || quantidadeInt <= 0) {
            Toast.makeText(this, "Quantidade inválida", Toast.LENGTH_SHORT).show()
            return
        }

        val validadeFormatada = converterData(validadeTexto)
        if (validadeFormatada == null) {
            Toast.makeText(this, "Data inválida. Use DD/MM/AAAA", Toast.LENGTH_SHORT).show()
            return
        }

        salvarSQLite(codigoAtual, nome, quantidadeInt, validadeFormatada)
    }

    private fun converterData(data: String): String? {
        return try {
            val entrada = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            entrada.isLenient = false
            val convertida = entrada.parse(data) ?: return null
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(convertida)
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
        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
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
            arrayOf(codigo, nome, quantidade, validade, Date().toString())
        )

        salvarCatalogo(codigo, nome)
        db.close()

        Toast.makeText(this, "Produto cadastrado com sucesso", Toast.LENGTH_LONG).show()
        mostrarTelaInicial()
    }

    private fun mostrarProdutosCadastrados() {
        val tela = criarEstruturaRolavel("PRODUTOS CADASTRADOS")
        val containerCards = tela.second

        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
        val cursor = db.rawQuery(
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

        if (!cursor.moveToFirst()) {
            val vazio = TextView(this).apply {
                text = "Nenhum produto cadastrado."
                setTextColor(corTextoSecundario)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            containerCards.addView(vazio)
        } else {
            do {
                val produto = cursor.getString(0)
                val codigo = cursor.getString(1)
                val qtd = cursor.getInt(2)
                val validade = cursor.getString(3)

                adicionarCardProduto(
                    containerCards,
                    produto,
                    codigo,
                    qtd,
                    formatarDataBanco(validade),
                    null
                )
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        adicionarBotaoVoltar(containerCards)
        setContentView(tela.first)
    }

    private data class RegistroBrigada(
        val produto: String,
        val codigo: String,
        val quantidade: Int,
        val validade: String,
        val diasRestantes: Long
    )

    private fun mostrarBrigada(diasMaximos: Int) {
        val tela = criarEstruturaRolavel("BRIGADA $diasMaximos DIAS")
        val containerCards = tela.second
        val registros = buscarBrigada(diasMaximos)

        if (registros.isEmpty()) {
            val vazio = TextView(this).apply {
                text = "Nenhum produto dentro deste prazo."
                setTextColor(corTextoSecundario)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            containerCards.addView(vazio)
        } else {
            for (registro in registros) {
                adicionarCardProduto(
                    containerCards,
                    registro.produto,
                    registro.codigo,
                    registro.quantidade,
                    formatarDataBanco(registro.validade),
                    registro.diasRestantes
                )
            }

            val exportar = Button(this).apply {
                text = "EXPORTAR PARA EXCEL (CSV)"
                textSize = 16f
                setTextColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(corBordaBranca)
                    cornerRadius = 14f
                }
                setOnClickListener {
                    exportarBrigadaExcel(diasMaximos, registros)
                }
            }
            val paramsExportar = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 15
                bottomMargin = 15
            }
            containerCards.addView(exportar, paramsExportar)
        }

        adicionarBotaoVoltar(containerCards)
        setContentView(tela.first)
    }

    private fun buscarBrigada(diasMaximos: Int): List<RegistroBrigada> {
        val lista = mutableListOf<RegistroBrigada>()

        val hoje = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val limite = Calendar.getInstance().apply {
            timeInMillis = hoje.timeInMillis
            add(Calendar.DAY_OF_YEAR, diasMaximos)
        }

        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
        val cursor = db.rawQuery(
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

        if (cursor.moveToFirst()) {
            do {
                val produto = cursor.getString(0)
                val codigo = cursor.getString(1)
                val quantidade = cursor.getInt(2)
                val validade = cursor.getString(3)

                try {
                    val data = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(validade)
                    if (data != null) {
                        val vencimento = Calendar.getInstance().apply {
                            time = data
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        if (!vencimento.before(hoje) && !vencimento.after(limite)) {
                            val diferenca = (vencimento.timeInMillis - hoje.timeInMillis) / (24L * 60L * 60L * 1000L)
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
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return lista.sortedBy { it.diasRestantes }
    }

    private fun adicionarCardProduto(
        container: LinearLayout,
        nome: String,
        codigo: String,
        quantidade: Int,
        validadeTexto: String,
        diasRestantes: Long?
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(corCardFundo)
                setStroke(2, corBordaBranca)
                cornerRadius = 14f
            }
            setPadding(30, 24, 30, 24)
        }

        val txtNome = TextView(this).apply {
            text = nome
            textSize = 17f
            setTextColor(corTextoPrincipal)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(txtNome)

        val txtCodigo = TextView(this).apply {
            text = "Código: $codigo  |  Qtd: $quantidade"
            textSize = 14f
            setTextColor(corTextoSecundario)
            setPadding(0, 6, 0, 4)
        }
        card.addView(txtCodigo)

        val txtValidade = TextView(this).apply {
            text = "Validade: $validadeTexto"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD

            if (diasRestantes != null) {
                val corAlerta = when {
                    diasRestantes <= 10 -> corCriticaVermelha
                    diasRestantes <= 30 -> corCriticaAmarela
                    else -> corCriticaVerde
                }
                setTextColor(corAlerta)
                text = "Validade: $validadeTexto ($diasRestantes dias restantes)"
            } else {
                setTextColor(corTextoPrincipal)
            }
        }
        card.addView(txtValidade)

        val paramsCard = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 10, 0, 14)
        }

        container.addView(card, paramsCard)
    }

    private fun criarEstruturaRolavel(tituloTexto: String): Pair<LinearLayout, LinearLayout> {
        val layoutRaiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(corFundoApp)
            setPadding(30, 40, 30, 20)
        }

        val titulo = TextView(this).apply {
            text = tituloTexto
            textSize = 22f
            setTextColor(corTextoPrincipal)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val paramsTitulo = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 20 }
        layoutRaiz.addView(titulo, paramsTitulo)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val containerInterno = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scrollView.addView(
            containerInterno,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val paramsScroll = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        layoutRaiz.addView(scrollView, paramsScroll)

        return Pair(layoutRaiz, containerInterno)
    }

    private fun adicionarBotaoVoltar(layout: LinearLayout) {
        val voltar = Button(this).apply {
            text = "VOLTAR"
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(corBordaBranca)
                cornerRadius = 14f
            }
            setOnClickListener {
                mostrarTelaInicial()
            }
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 15
            bottomMargin = 15
        }

        layout.addView(voltar, params)
    }

    private fun exportarBrigadaExcel(
        diasMaximos: Int,
        registros: List<RegistroBrigada>
    ) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val nomeArquivo = "Brigada_${diasMaximos}_dias_" +
                    SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) +
                    ".csv"

                val csv = StringBuilder()
                csv.append("BRIGADA $diasMaximos DIAS\n")
                csv.append("Gerado em:;")
                csv.append(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()))
                csv.append("\n\n")
                csv.append("Produto;Código de barras;Quantidade;Validade;Dias para vencer\n")

                for (registro in registros) {
                    csv.append("\"${registro.produto.replace("\"", "\"\"")}\";")
                    csv.append("\"${registro.codigo}\";")
                    csv.append("${registro.quantidade};")
                    csv.append("\"${formatarDataBanco(registro.validade)}\";")
                    csv.append("${registro.diasRestantes}\n")
                }

                val bytes = ("\uFEFF" + csv.toString()).toByteArray(Charsets.UTF_8)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, nomeArquivo)
                        put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download")
                    }

                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Não foi possível criar o arquivo no MediaStore.")

                    contentResolver.openOutputStream(uri).use { saida ->
                        if (saida == null) {
                            throw Exception("Não foi possível gravar o arquivo.")
                        }
                        saida.write(bytes)
                    }

                    runOnUiThread {
                        compartilharArquivo(uri, nomeArquivo)
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

    private fun compartilharArquivo(uri: Uri, nomeArquivo: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Enviar $nomeArquivo"))
    }

    private fun formatarDataBanco(data: String): String {
        return try {
            val entrada = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            entrada.isLenient = false
            val convertida = entrada.parse(data) ?: return data
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(convertida)
        } catch (_: Exception) {
            data
        }
    }
}
