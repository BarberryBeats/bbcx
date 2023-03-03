package com.example.bbcx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Handler
import java.util.concurrent.atomic.AtomicBoolean

class OCRThread(context: Context) : Thread() {

    private val handler: Handler = Handler()
    private val textRecognitionHelper: TextRecognitionHelper = TextRecognitionHelper(context)
    private val bitmapChanged: AtomicBoolean = AtomicBoolean()

    private var runFlag: Boolean = false
    private var bitmap: Bitmap? = null
    private var regionsListener: TextRegionsListener? = null
    private var textRecognitionListener: TextRecognitionListener? = null

    /**
     * Update image data for recognition.
     *
     * @param bitmap camera frame data.
     */
    fun updateBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        bitmapChanged.set(true)
    }

    override fun start() {
        runFlag = true
        super.start()
    }

    /**
     * Stop thread execution.
     */
    fun cancel() {
        runFlag = false
        regionsListener = null
        textRecognitionListener = null
    }

    /**
     * Setter for recognized text region updates listener.
     *
     * @param regionsListener Listener for recognized text regions updates.
     */
    fun setRegionsListener(regionsListener: TextRegionsListener) {
        this.regionsListener = regionsListener
    }

    /**
     * Setter for recognized text updates listener.
     *
     * @param textRecognitionListener Listener for recognized text updates.
     */
    fun setTextRecognitionListener(textRecognitionListener: TextRecognitionListener) {
        this.textRecognitionListener = textRecognitionListener
    }

    /**
     * Perform text recognition.
     */
    override fun run() {
        textRecognitionHelper.prepareTesseract("eng")
        while (runFlag) {
            if (bitmapChanged.compareAndSet(true, false)) {
                val matrix = Matrix()
                matrix.postRotate(90f)
                val rotatedBitmap = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                textRecognitionHelper.setBitmap(rotatedBitmap)
                updateTextRegions()
                updateOCRText()
                rotatedBitmap.recycle()
            }
        }
        textRecognitionHelper.stop()
    }

    private fun updateTextRegions() {
        if (regionsListener != null) {
            val regions = textRecognitionHelper.getTextRegions()
            handler.post {
                regionsListener!!.onTextRegionsRecognized(regions)
            }
        }
    }

    private fun updateOCRText() {
        if (textRecognitionListener != null) {
            val text = textRecognitionHelper.getText()
            handler.post {
                textRecognitionListener!!.onTextRecognized(text)
                textRecognitionListener = null
            }
        }
    }

    /**
     * Listener for recognized text regions updates.
     */
    interface TextRegionsListener {

        /**
         * Notify about recognized text regions update.
         *
         * @param textRegions list of recognized text regions.
         */
        fun onTextRegionsRecognized(textRegions: List<Rect>)
    }

    /**
     * Listener for recognized text updates.
     */
    interface TextRecognitionListener {

        /**
         * Notify text recognized.
         *
         * @param text Recognized text.
         */
        fun onTextRecognized(text: String)
    }
}