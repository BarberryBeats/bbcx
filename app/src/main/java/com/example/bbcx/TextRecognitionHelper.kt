package com.example.bbcx

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import android.content.Context
import com.googlecode.leptonica.android.Pixa
import com.googlecode.tesseract.android.TessBaseAPI


class TextRecognitionHelper(private val applicationContext: Context) {

    companion object {
        private const val TAG = "TextRecognitionHelper"
        private const val TESSERACT_TRAINED_DATA_FOLDER = "tessdata"
        private val TESSERACT_PATH = "${Environment.getExternalStorageDirectory().absolutePath}/tess_ocr_example/"
    }

    private val tessBaseApi: TessBaseAPI = TessBaseAPI()

    /**
     * Initialize tesseract engine.
     *
     * @param language Language code in ISO-639-3 format.
     */
    fun prepareTesseract(language: String) {
        try {
            prepareDirectory("$TESSERACT_PATH$TESSERACT_TRAINED_DATA_FOLDER")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        copyTessDataFiles(TESSERACT_TRAINED_DATA_FOLDER)
        tessBaseApi.init(TESSERACT_PATH, language)
    }

    private fun prepareDirectory(path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "ERROR: Creation of directory $path failed, check does Android Manifest have permission to write to external storage.")
            }
        } else {
            Log.i(TAG, "Created directory $path")
        }
    }

    private fun copyTessDataFiles(path: String) {
        try {
            val fileList = applicationContext.assets.list(path) ?: return
            for (fileName in fileList) {
                val pathToDataFile = "$TESSERACT_PATH$path/$fileName"
                if (!File(pathToDataFile).exists()) {
                    applicationContext.assets.open("$path/$fileName").use { inputStream ->
                        FileOutputStream(pathToDataFile).use { outputStream ->
                            val buf = ByteArray(1024)
                            var length: Int
                            while (inputStream.read(buf).also { length = it } > 0) {
                                outputStream.write(buf, 0, length)
                            }
                        }
                    }
                    Log.d(TAG, "Copied $fileName to tessdata")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Unable to copy files to tessdata ${e.message}")
        }
    }

    /**
     * Set image for recognition.
     *
     * @param bitmap Image data.
     */
    fun setBitmap(bitmap: Bitmap) {
        tessBaseApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        tessBaseApi.setImage(bitmap)
    }

    /**
     * Get recognized words regions for image.
     *
     * @return List of words regions.
     */
    fun getTextRegions(): List<Rect> {
        val regions = tessBaseApi.words
        val lineRects = ArrayList(regions.boxRects)
        regions.recycle()
        return lineRects
    }

    /**
     * Get recognized text for image.
     *
     * @return Recognized text string.
     */
    fun getText(): String {
        return tessBaseApi.utF8Text
    }

    /**
     * Clear tesseract data.
     */
    fun stop() {
        tessBaseApi.clear()
    }
}