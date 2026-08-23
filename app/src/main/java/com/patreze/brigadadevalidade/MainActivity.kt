package com.patreze.brigadadevalidade

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : Activity() {

    private lateinit var codigoTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(40, 40, 40, 40)

        val titulo = TextView(this)

        titulo.text = "BRIGADA DE VALIDADE"
        titulo.textSize = 26f
        titulo.setTextColor(Color.BLACK)
        titulo.gravity = Gravity.CENTER

        layout.addView(titulo)

        codigoTextView = TextView(this)

        codigoTextView.text = "Nenhum código escaneado"
        codigoTextView.textSize = 18f
        codigoTextView.gravity = Gravity.CENTER

        val margem = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        margem.setMargins(0, 60, 0, 30)

        layout.addView(
            codigoTextView,
            margem
        )

        val botao = Button(this)

        botao.text = "ESCANEAR CÓDIGO DE BARRAS"

        botao.setOnClickListener {
            abrirScanner()
        }

        layout.addView(botao)

        setContentView(layout)
    }

    private fun abrirScanner() {

        val options =
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A,
                    com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E
                )
                .build()

        val scanner =
            GmsBarcodeScanning.getClient(
                this,
                options
            )

        scanner.startScan()
            .addOnSuccessListener { barcode ->

                val codigo = barcode.rawValue ?: ""

                codigoTextView.text =
                    "Código encontrado:\n\n$codigo"
            }
            .addOnCanceledListener {

                codigoTextView.text =
                    "Leitura cancelada"
            }
            .addOnFailureListener {

                codigoTextView.text =
                    "Não foi possível ler o código"
            }
    }
}
