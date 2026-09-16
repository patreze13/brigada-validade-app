package com.patreze.brigadadevalidade

import android.app.Activity
import android.app.AlertDialog
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val REQ_IMPORTAR_JSON = 1001

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
        val tela = criarEstruturaRolavel("PATREZE BRIGADA DE VALIDADE")
        val layout = tela.second

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

        adicionarBotaoPrincipal(layout, "EXPORTAR BACKUP (JSON)") {
            exportarBackupJson()
        }

        adicionarBotaoPrincipal(layout, "IMPORTAR BACKUP (JSON)") {
            abrirSeletorImportarJson()
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
            topMargin = 25
            bottomMargin = 20
        }
        layout.addView(status, paramsStatus)

        setContentView(tela.first)
    }

    private fun adicionarBotaoPrincipal(
        layout: LinearLayout,
        texto: String,
        acao: () -> Unit
    ) {
        val botao = Button(this).apply {
            text = texto
            textSize = 15f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(corBordaBranca)
                cornerRadius = 14f
            }
            setOnClickListener { acao() }
        }

        val parametros = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 10, 0, 10)
        }

        layout.addView(botao, parametros)
    }

    // Validador matemático de Checksum para EAN-13, EAN-8 e UPC-A
    private fun codigoBarrasValido(codigo: String): Boolean {
        if (!codigo.all { it.isDigit() }) return false
        if (codigo.length !in listOf(8, 12, 13)) return false

        val digitos = codigo.map { it.toString().toInt() }
        val checkEsperado = digitos.last()
        val corpo = digitos.dropLast(1).reversed()

        var soma = 0
        for (i in corpo.indices) {
            val peso = if (i % 2 == 0) 3 else 1
            soma += corpo[i] * peso
        }
        val resto = soma % 10
        val calculado = if (resto == 0) 0 else 10 - resto

        return calculado == checkEsperado
    }

    private fun abrirScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A
            )
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val lido = barcode.rawValue?.trim() ?: ""

                if (lido.isBlank()) {
                    Toast.makeText(this, "Nenhum código detectado.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Proteção contra leituras cortadas
                if (!codigoBarrasValido(lido)) {
                    AlertDialog.Builder(this)
                        .setTitle("Leitura Incompleta")
                        .setMessage("O código \"$lido\" parece estar incompleto ou cortado. Deseja tentar escanear novamente ou digitar?")
                        .setPositiveButton("ESCANEAR NOVAMENTE") { _, _ -> abrirScanner() }
                        .setNegativeButton("EDITAR MANUAL") { _, _ ->
                            codigoAtual = lido
                            mostrarCadastro("")
                        }
                        .show()
                    return@addOnSuccessListener
                }

                // Pop-up obrigatório de confirmação do código
                confirmarCodigoEscaneado(lido)
            }
            .addOnCanceledListener {
                if (::status.isInitialized) {
                    status.text = "Leitura cancelada"
                }
            }
            .addOnFailureListener { e ->
                if (::status.isInitialized) {
                    status.text = "Erro no scanner: ${e.message}"
                }
            }
    }

    private fun confirmarCodigoEscaneado(codigo: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Código")
            .setMessage("CÓDIGO ESCANEADO:\n\n$codigo\n\nConfirma o número?")
            .setCancelable(false)
            .setPositiveButton("CONFIRMAR") { _, _ ->
                codigoAtual = codigo
                consultarProduto(codigo)
            }
            .setNegativeButton("LER NOVAMENTE") { _, _ ->
                abrirScanner()
            }
            .setNeutralButton("DIGITAR OUTRO") { _, _ ->
                pedirCodigoManualmente()
            }
            .show()
    }

    private fun pedirCodigoManualmente() {
        val input = EditText(this).apply {
            hint = "Digite os números do código"
            inputType = 2
            setTextColor(corTextoPrincipal)
        }

        AlertDialog.Builder(this)
            .setTitle("Inserir Código Manual")
            .setView(input)
            .setPositiveButton("AVANÇAR") { _, _ ->
                val digitado = input.text.toString().trim()
                if (digitado.isNotBlank()) {
                    codigoAtual = digitado
                    consultarProduto(digitado)
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun consultarProduto(codigo: String) {
        if (::status.isInitialized) {
            status.text = "Consultando catálogo e API..."
        }

        Executors.newSingleThreadExecutor().execute {
            val nomeCatalogo = consultarCatalogoLocal(codigo)
            if (nomeCatalogo.isNotBlank()) {
                runOnUiThread {
                    if (::status.isInitialized) status.text = ""
                    mostrarCadastro(nomeCatalogo)
                }
                return@execute
            }

            var nomeFinal = consultarApiAberta("https://world.openfoodfacts.org/api/v2/product/$codigo.json")

            if (nomeFinal.isBlank()) {
                nomeFinal = consultarApiAberta("https://world.openbeautyfacts.org/api/v2/product/$codigo.json")
            }

            if (nomeFinal.isBlank()) {
                nomeFinal = consultarApiAberta("https://world.openproductsfacts.org/api/v2/product/$codigo.json")
            }

            runOnUiThread {
                if (::status.isInitialized) status.text = ""
                if (nomeFinal.isNotBlank()) {
                    salvarCatalogo(codigo, nomeFinal)
                    mostrarCadastro(nomeFinal)
                } else {
                    Toast.makeText(this, "Produto não encontrado na base. Preencha manualmente.", Toast.LENGTH_LONG).show()
                    mostrarCadastro("")
                }
            }
        }
    }

    private fun consultarApiAberta(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conexao = url.openConnection() as HttpURLConnection
            conexao.requestMethod = "GET"
            conexao.connectTimeout = 5000
            conexao.readTimeout = 5000
            conexao.setRequestProperty("User-Agent", "PatrezeBrigadaDeValidade/2.0 (Android)")

            if (conexao.responseCode == HttpURLConnection.HTTP_OK) {
                val resposta = conexao.inputStream.bufferedReader().use { it.readText() }
                conexao.disconnect()

                val json = JSONObject(resposta)
                if (json.optInt("status", 0) == 1) {
                    val produto = json.optJSONObject("product")
                    if (produto != null) {
                        var nome = produto.optString("product_name_pt", "")
                        if (nome.isBlank()) {
                            nome = produto.optString("product_name", "")
                        }
                        val marca = produto.optString("brands", "")
                        return when {
                            nome.isNotBlank() && marca.isNotBlank() -> "$nome - $marca"
                            nome.isNotBlank() -> nome
                            else -> ""
                        }
                    }
                }
            } else {
                conexao.disconnect()
            }
            ""
        } catch (_: Exception) {
            ""
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

    private fun mostrarCadastro(nomeEncontrado: String) {
        val tela = criarEstruturaRolavel(if (nomeEncontrado.isNotBlank()) "PRODUTO IDENTIFICADO" else "NOVO CADASTRO")
        val raiz = tela.second

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
            text = "Código de Barras: $codigoAtual"
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
        configurarMascaraData(validade)
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
        setContentView(tela.first)
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

    private fun configurarMascaraData(campoData: EditText) {
        campoData.addTextChangedListener(object : TextWatcher {
            private var alterando = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (alterando || s == null) return

                val numeros = s.toString().replace("/", "").filter { it.isDigit() }
                if (numeros.length > 8) return

                val formatado = StringBuilder()
                for (i in numeros.indices) {
                    if (i == 2 || i == 4) formatado.append("/")
                    formatado.append(numeros[i])
                }

                val novoTexto = formatado.toString()
                if (novoTexto != s.toString()) {
                    alterando = true
                    campoData.setText(novoTexto)
                    campoData.setSelection(novoTexto.length)
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

        Toast.makeText(this, "Produto cadastrado com sucesso", Toast.LENGTH_SHORT).show()
        mostrarTelaInicial()
    }

    private fun mostrarProdutosCadastrados() {
        val tela = criarEstruturaRolavel("PRODUTOS CADASTRADOS")
        val containerCards = tela.second

        val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
        val cursor = db.rawQuery(
            """
            SELECT
                id,
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
                val id = cursor.getInt(0)
                val produto = cursor.getString(1)
                val codigo = cursor.getString(2)
                val qtd = cursor.getInt(3)
                val validade = cursor.getString(4)

                adicionarCardProduto(
                    containerCards,
                    id,
                    produto,
                    codigo,
                    qtd,
                    formatarDataBanco(validade),
                    null
                ) {
                    mostrarProdutosCadastrados()
                }
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        adicionarBotaoVoltar(containerCards)
        setContentView(tela.first)
    }

    private data class RegistroBrigada(
        val id: Int,
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
                    registro.id,
                    registro.produto,
                    registro.codigo,
                    registro.quantidade,
                    formatarDataBanco(registro.validade),
                    registro.diasRestantes
                ) {
                    mostrarBrigada(diasMaximos)
                }
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
                id,
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
                val id = cursor.getInt(0)
                val produto = cursor.getString(1)
                val codigo = cursor.getString(2)
                val quantidade = cursor.getInt(3)
                val validade = cursor.getString(4)

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
                                    id,
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
        id: Int,
        nome: String,
        codigo: String,
        quantidade: Int,
        validadeTexto: String,
        diasRestantes: Long?,
        aoAtualizar: () -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(corCardFundo)
                setStroke(2, corBordaBranca)
                cornerRadius = 14f
            }
            setPadding(30, 24, 30, 24)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                abrirMenuOpcoesProduto(id, nome, codigo, quantidade, validadeTexto, aoAtualizar)
            }
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

    private fun abrirMenuOpcoesProduto(
        id: Int,
        nome: String,
        codigo: String,
        qtd: Int,
        validadeFormatada: String,
        aoAtualizar: () -> Unit
    ) {
        val opcoes = arrayOf("Editar Produto", "Excluir Produto")
        AlertDialog.Builder(this)
            .setTitle(nome)
            .setItems(opcoes) { _, qual ->
                when (qual) {
                    0 -> mostrarTelaEdicao(id, nome, codigo, qtd, validadeFormatada, aoAtualizar)
                    1 -> confirmarExclusao(id, nome, aoAtualizar)
                }
            }
            .show()
    }

    private fun confirmarExclusao(id: Int, nome: String, aoAtualizar: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Excluir Produto")
            .setMessage("Deseja realmente remover \"$nome\" da lista?")
            .setPositiveButton("EXCLUIR") { _, _ ->
                val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
                db.execSQL("DELETE FROM produtos WHERE id = ?", arrayOf(id))
                db.close()
                Toast.makeText(this, "Produto excluído com sucesso", Toast.LENGTH_SHORT).show()
                aoAtualizar()
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun mostrarTelaEdicao(
        id: Int,
        nomeAtual: String,
        codigo: String,
        qtdAtual: Int,
        validadeAtual: String,
        aoAtualizar: () -> Unit
    ) {
        val tela = criarEstruturaRolavel("EDITAR PRODUTO")
        val raiz = tela.second

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(corCardFundo)
                setStroke(3, corBordaBranca)
                cornerRadius = 16f
            }
            setPadding(30, 30, 30, 30)
        }

        val txtCodigo = TextView(this).apply {
            text = "Código: $codigo"
            textSize = 15f
            setTextColor(corTextoSecundario)
            typeface = Typeface.DEFAULT_BOLD
        }
        card.addView(txtCodigo)

        val editNome = criarCampoTexto("Nome do produto", nomeAtual, 1)
        card.addView(editNome)

        val editQtd = criarCampoTexto("Quantidade", qtdAtual.toString(), 2)
        card.addView(editQtd)

        val editValidade = criarCampoTexto("Validade (DD/MM/AAAA)", validadeAtual, 2).apply {
            filters = arrayOf(InputFilter.LengthFilter(10))
        }
        configurarMascaraData(editValidade)
        card.addView(editValidade)

        val paramsCard = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 25 }
        raiz.addView(card, paramsCard)

        val botaoSalvar = Button(this).apply {
            text = "ATUALIZAR PRODUTO"
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(corBordaBranca)
                cornerRadius = 14f
            }
            setOnClickListener {
                val novoNome = editNome.text.toString().trim()
                val novaQtdTexto = editQtd.text.toString().trim()
                val novaValidadeTexto = editValidade.text.toString().trim()

                if (novoNome.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Digite o nome do produto", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val novaQtd = novaQtdTexto.toIntOrNull()
                if (novaQtd == null || novaQtd <= 0) {
                    Toast.makeText(this@MainActivity, "Quantidade inválida", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val novaDataFormatada = converterData(novaValidadeTexto)
                if (novaDataFormatada == null) {
                    Toast.makeText(this@MainActivity, "Data inválida. Use DD/MM/AAAA", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
                db.execSQL(
                    """
                    UPDATE produtos
                    SET produto = ?, quantidade = ?, validade = ?
                    WHERE id = ?
                    """.trimIndent(),
                    arrayOf(novoNome, novaQtd, novaDataFormatada, id)
                )
                salvarCatalogo(codigo, novoNome)
                db.close()

                Toast.makeText(this@MainActivity, "Produto atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                aoAtualizar()
            }
        }
        val paramsSalvar = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 15 }
        raiz.addView(botaoSalvar, paramsSalvar)

        val botaoCancelar = Button(this).apply {
            text = "CANCELAR"
            textSize = 16f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CCCCCC"))
                cornerRadius = 14f
            }
            setOnClickListener { aoAtualizar() }
        }
        raiz.addView(botaoCancelar)

        setContentView(tela.first)
    }

    // ==========================================
    // BACKUP: EXPORTAR E IMPORTAR JSON
    // ==========================================

    private fun exportarBackupJson() {
        Executors.newSingleThreadExecutor().execute {
            try {
                val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
                val cursor = db.rawQuery(
                    "SELECT codigo_barras, produto, quantidade, validade, criado_em FROM produtos WHERE situacao_geral = 'ATIVO'",
                    null
                )

                val array = JSONArray()
                if (cursor.moveToFirst()) {
                    do {
                        val obj = JSONObject().apply {
                            put("codigo_barras", cursor.getString(0))
                            put("produto", cursor.getString(1))
                            put("quantidade", cursor.getInt(2))
                            put("validade", cursor.getString(3))
                            put("criado_em", cursor.getString(4))
                        }
                        array.put(obj)
                    } while (cursor.moveToNext())
                }
                cursor.close()
                db.close()

                val nomeArquivo = "Backup_Brigada_" +
                        SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) + ".json"
                val bytes = array.toString(2).toByteArray(Charsets.UTF_8)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, nomeArquivo)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download")
                    }

                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Erro ao criar arquivo no MediaStore")

                    contentResolver.openOutputStream(uri).use { saida ->
                        saida?.write(bytes)
                    }

                    runOnUiThread {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Compartilhar Backup JSON"))
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Requer Android 10 ou superior para salvar em Downloads", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Falha ao exportar backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun abrirSeletorImportarJson() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Selecione o arquivo de Backup JSON"), REQ_IMPORTAR_JSON)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMPORTAR_JSON && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importarBackupJson(uri)
        }
    }

    private fun importarBackupJson(uri: Uri) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                    ?: throw Exception("Arquivo vazio ou ilegível")

                val array = JSONArray(jsonString)
                val db = openOrCreateDatabase("validade.db", MODE_PRIVATE, null)
                var importados = 0

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val codigo = obj.optString("codigo_barras", "")
                    val produto = obj.optString("produto", "")
                    val qtd = obj.optInt("quantidade", 1)
                    val valData = obj.optString("validade", "")
                    val criadoEm = obj.optString("criado_em", Date().toString())

                    if (codigo.isNotBlank() && produto.isNotBlank() && valData.isNotBlank()) {
                        db.execSQL(
                            """
                            INSERT INTO produtos (codigo_barras, produto, quantidade, validade, criado_em)
                            VALUES (?, ?, ?, ?, ?)
                            """.trimIndent(),
                            arrayOf(codigo, produto, qtd, valData, criadoEm)
                        )
                        salvarCatalogo(codigo, produto)
                        importados++
                    }
                }
                db.close()

                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Backup Restaurado")
                        .setMessage("$importados produtos foram importados com sucesso!")
                        .setPositiveButton("OK") { _, _ -> mostrarTelaInicial() }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Erro ao importar: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun criarEstruturaRolavel(tituloTexto: String): Pair<LinearLayout, LinearLayout> {
        val layoutRaiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(corFundoApp)
            setPadding(30, 40, 30, 20)
        }

        val titulo = TextView(this).apply {
            text = tituloTexto
            textSize = 21f
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
