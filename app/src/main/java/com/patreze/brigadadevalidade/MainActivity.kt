package com.patreze.brigadadevalidade

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var codigoAtual: String
    private lateinit var nomeProduto: EditText
    private lateinit var quantidade: EditText
    private lateinit var validade: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarTelaInicial()
    }

    private fun mostrarTelaInicial() {

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(40, 40, 40, 40)

        val titulo = TextView(this)
        titulo.text = "BRIGADA DE VALIDADE"
        titulo.textSize = 26f
        titulo.setTypeface(null, Typeface.BOLD)
        titulo.gravity = Gravity.CENTER

        layout.addView(titulo)

        val botao = Button(this)
        botao.text = "ESCANEAR CÓDIGO DE BARRAS"

        botao.setOnClickListener {
            abrirScanner()
        }

        layout.addView(botao)

        status = TextView(this)
        status.text = ""
        status.textSize = 16f
        status.gravity = Gravity.CENTER

        layout.addView(status)

        setContentView(layout)
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
            GmsBarcodeScanning.getClient(this, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->

                codigoAtual = barcode.rawValue ?: ""

                consultarProduto(codigoAtual)
            }
            .addOnCanceledListener {

                status.text = "Leitura cancelada"
            }
            .addOnFailureListener {

                status.text = "Erro ao ler código"
            }
    }

    private fun consultarProduto(codigo: String) {

        status.text = "Consultando produto..."

        Executors.newSingleThreadExecutor().execute {

            try {

                val url =
                    URL("https://world.openfoodfacts.org/api/v2/product/$codigo.json")

                val conexao =
                    url.openConnection() as HttpURLConnection

                conexao.requestMethod = "GET"
                conexao.connectTimeout = 10000
                conexao.readTimeout = 10000

                val resposta =
                    conexao.inputStream.bufferedReader().readText()

                val json = JSONObject(resposta)

                val encontrado =
                    json.optInt("status", 0) == 1

                var nome = ""

                if (encontrado) {
                    val produto =
                        json.optJSONObject("product")

                    nome =
                        produto?.optString("product_name", "") ?: ""
                }

                runOnUiThread {

                    if (nome.isNotBlank()) {
                        mostrarCadastro(nome)
                    } else {
                        mostrarCadastro("")
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {
                    mostrarCadastro("")
                }
            }
        }
    }

    private fun mostrarCadastro(nomeEncontrado: String) {

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 30, 40, 30)

        val titulo = TextView(this)

        titulo.text =
            if (nomeEncontrado.isNotBlank())
                "PRODUTO ENCONTRADO"
            else
                "PRODUTO NÃO ENCONTRADO"

        titulo.textSize = 22f
        titulo.setTypeface(null, Typeface.BOLD)

        layout.addView(titulo)

        val codigo = TextView(this)

        codigo.text =
            "Código: $codigoAtual"

        codigo.textSize = 16f

        layout.addView(codigo)

        nomeProduto = EditText(this)

        nomeProduto.hint = "Nome do produto"
        nomeProduto.setText(nomeEncontrado)

        layout.addView(nomeProduto)

        quantidade = EditText(this)

        quantidade.hint = "Quantidade"
        quantidade.inputType = 2

        layout.addView(quantidade)

        validade = EditText(this)

        validade.hint = "Validade (DD/MM/AAAA)"
        validade.inputType = 1

        layout.addView(validade)

        val salvar = Button(this)

        salvar.text = "SALVAR PRODUTO"

        salvar.setOnClickListener {
            salvarProduto()
        }

        layout.addView(salvar)

        setContentView(layout)
    }

    private fun salvarProduto() {

        val nome = nomeProduto.text.toString().trim()
        val qtdTexto = quantidade.text.toString().trim()
        val validadeTexto = validade.text.toString().trim()

        if (nome.isEmpty()) {
            Toast.makeText(
                this,
                "Digite o nome do produto",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (qtdTexto.isEmpty()) {
            Toast.makeText(
                this,
                "Digite a quantidade",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (validadeTexto.isEmpty()) {
            Toast.makeText(
                this,
                "Digite a validade",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val quantidadeInt =
            qtdTexto.toIntOrNull()

        if (quantidadeInt == null || quantidadeInt <= 0) {
            Toast.makeText(
                this,
                "Quantidade inválida",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val validadeFormatada =
            converterData(validadeTexto)

        if (validadeFormatada == null) {
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

    private fun converterData(data: String): String? {

        return try {

            val entrada =
                SimpleDateFormat(
                    "dd/MM/yyyy",
                    Locale.US
                )

            entrada.isLenient = false

            val dataConvertida =
                entrada.parse(data)

            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).format(dataConvertida!!)

        } catch (e: Exception) {
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
            INSERT INTO produtos
            (codigo_barras, produto, quantidade, validade, criado_em)
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
}
