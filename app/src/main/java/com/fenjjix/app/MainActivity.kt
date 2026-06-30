package com.fenjjix.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private val httpClient = OkHttpClient()
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val sourceLanguages = arrayOf(
        "Francés",
        "Inglés",
        "Alemán",
        "Italiano",
        "Portugués",
        "Árabe",
        "Chino"
    )
    private val destinationLanguages = arrayOf(
        "Español",
        "Francés",
        "Inglés",
        "Alemán",
        "Italiano",
        "Portugués",
        "Árabe",
        "Chino"
    )
    private val langCodeMap = mapOf(
        "Francés" to "fr",
        "Inglés" to "en",
        "Alemán" to "de",
        "Italiano" to "it",
        "Portugués" to "pt",
        "Árabe" to "ar",
        "Chino" to "zh",
        "Español" to "es"
    )

    private lateinit var spinnerOrigen: Spinner
    private lateinit var spinnerDestino: Spinner
    private lateinit var tvOrigen: TextView
    private lateinit var tvDestino: TextView
    private lateinit var etTexto: EditText
    private lateinit var btnTraducir: Button
    private lateinit var btnVoz: Button
    private lateinit var btnCopiar: Button
    private lateinit var btnCerrar: Button
    private lateinit var btnPegar: Button   // Nuevo botón

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val btnActivar = findViewById<Button>(R.id.btn_activar)
        btnActivar.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                mostrarBurbuja()
            }
        }
    }

    private fun mostrarBurbuja() {
        if (bubbleView == null) {
            val sizeInDp = 150
            val scale = resources.displayMetrics.density
            val sizeInPx = (sizeInDp * scale + 0.5f).toInt()

            val imageView = ImageView(this)
            imageView.setBackgroundColor(Color.parseColor("#2196F3"))
            imageView.layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx)

            val params = WindowManager.LayoutParams(
                sizeInPx,
                sizeInPx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 100

            imageView.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var startX = 0f
                private var startY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            startX = event.rawX
                            startY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            params.x = initialX + (event.rawX - initialTouchX).toInt()
                            params.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(imageView, params)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val distance = abs(event.rawX - startX) + abs(event.rawY - startY)
                            if (distance < 10) {
                                if (panelView == null) {
                                    crearPanel()
                                } else {
                                    removerPanel()
                                }
                            }
                            // Si la distancia es mayor, se consideró arrastre; no hacemos nada extra.
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager.addView(imageView, params)
            bubbleView = imageView
        }
    }

    private fun crearPanel() {
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.WHITE)
        panel.setPadding(30, 30, 30, 30)
        panel.layoutParams = LinearLayout.LayoutParams(700, LinearLayout.LayoutParams.WRAP_CONTENT)

        // Parámetros comunes para los hijos
        val childParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        childParams.setMargins(10, 10, 10, 10)

        // Spinners
        spinnerOrigen = Spinner(this)
        spinnerOrigen.minimumHeight = 80
        spinnerOrigen.layoutParams = childParams

        spinnerDestino = Spinner(this)
        spinnerDestino.minimumHeight = 80
        spinnerDestino.layoutParams = childParams

        val adapterOrigen = ArrayAdapter(this, android.R.layout.simple_spinner_item, sourceLanguages)
        adapterOrigen.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerOrigen.adapter = adapterOrigen

        val adapterDestino = ArrayAdapter(this, android.R.layout.simple_spinner_item, destinationLanguages)
        adapterDestino.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDestino.adapter = adapterDestino
        spinnerDestino.setSelection(destinationLanguages.indexOf("Español"))

        // TextViews
        tvOrigen = TextView(this)
        tvOrigen.text = ""
        tvOrigen.setTextColor(Color.BLACK)
        tvOrigen.minimumHeight = 60
        tvOrigen.setPadding(20, 20, 20, 20)
        tvOrigen.setBackgroundColor(Color.parseColor("#EEEEEE"))
        tvOrigen.layoutParams = childParams

        tvDestino = TextView(this)
        tvDestino.text = ""
        tvDestino.setTextColor(Color.BLACK)
        tvDestino.minimumHeight = 60
        tvDestino.setPadding(20, 20, 20, 20)
        tvDestino.setBackgroundColor(Color.parseColor("#EEEEEE"))
        tvDestino.layoutParams = childParams

        // EditText
        etTexto = EditText(this)
        etTexto.hint = "Pega aquí el texto"
        etTexto.minimumHeight = 100
        etTexto.setPadding(20, 20, 20, 20)
        etTexto.layoutParams = childParams

        // Botón Pegar (nuevo)
        btnPegar = Button(this)
        btnPegar.text = "Pegar"
        btnPegar.layoutParams = childParams
        btnPegar.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val texto = clipData.getItemAt(0).coerceToText(this).toString()
                etTexto.setText(texto)
            }
        }

        // Buttons
        btnTraducir = Button(this)
        btnTraducir.text = "Traducir"
        btnTraducir.layoutParams = childParams

        btnVoz = Button(this)
        btnVoz.text = "Voz"
        btnVoz.layoutParams = childParams

        btnCopiar = Button(this)
        btnCopiar.text = "Copiar"
        btnCopiar.layoutParams = childParams

        btnCerrar = Button(this)
        btnCerrar.text = "Cerrar"
        btnCerrar.layoutParams = childParams

        // Añadir vistas al panel
        panel.addView(spinnerOrigen)
        panel.addView(spinnerDestino)
        panel.addView(tvOrigen)
        panel.addView(tvDestino)
        panel.addView(etTexto)
        panel.addView(btnPegar)          // Añadido antes de Traducir
        panel.addView(btnTraducir)
        panel.addView(btnVoz)
        panel.addView(btnCopiar)
        panel.addView(btnCerrar)

        val params = WindowManager.LayoutParams(
            700,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.x = 0
        params.y = 300

        windowManager.addView(panel, params)
        panelView = panel

        // Ocultar la burbuja mientras el panel está visible
        bubbleView?.visibility = View.GONE

        btnTraducir.setOnClickListener {
            val texto = etTexto.text.toString()
            if (texto.isNotBlank()) {
                traducir(texto)
            }
        }

        btnVoz.setOnClickListener {
            iniciarReconocimientoVoz()
        }

        btnCopiar.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("traducción", tvDestino.text)
            clipboard.setPrimaryClip(clip)
        }

        btnCerrar.setOnClickListener {
            removerPanel()
        }
    }

    private fun removerPanel() {
        panelView?.let {
            windowManager.removeView(it)
            panelView = null
        }
        // Volver a mostrar la burbuja al cerrar el panel
        bubbleView?.visibility = View.VISIBLE
        detenerReconocimientoVoz()
    }

    private fun traducir(texto: String) {
        val origenCodigo = langCodeMap[spinnerOrigen.selectedItem.toString()] ?: "es"
        val destinoCodigo = langCodeMap[spinnerDestino.selectedItem.toString()] ?: "en"
        val url = "https://api.mymemory.translated.net/get?q=${URLEncoder.encode(texto, "UTF-8")}&langpair=$origenCodigo|$destinoCodigo"

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val request = Request.Builder().url(url).build()
                val response = withContext(Dispatchers.IO) { httpClient.newCall(request).execute() }
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    val responseData = json.getJSONObject("responseData")
                    val traducido = responseData.getString("translatedText")
                    tvOrigen.text = texto
                    tvDestino.text = traducido
                }
            } catch (e: Exception) {
                tvDestino.text = "Error: ${e.message}"
            }
        }
    }

    private fun iniciarReconocimientoVoz() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (isListening) {
                        Handler(Looper.getMainLooper()).postDelayed({ iniciarReconocimientoVoz() }, 500)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        etTexto.setText(matches[0])
                    }
                    if (isListening) {
                        iniciarReconocimientoVoz()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
            speechRecognizer?.setRecognitionListener(listener)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val idiomaOrigen = langCodeMap[spinnerOrigen.selectedItem.toString()] ?: "es"
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, idiomaOrigen)
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            isListening = true
            speechRecognizer?.startListening(intent)
        }
    }

    private fun detenerReconocimientoVoz() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}