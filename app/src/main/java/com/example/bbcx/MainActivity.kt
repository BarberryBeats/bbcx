package com.example.bbcx

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bbcx.databinding.ActivityMainBinding
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.*


class MainActivity : AppCompatActivity() {

    val path = Environment.getExternalStorageDirectory().absolutePath + "/com.example.bbcx/"
    val trainedData = "tessdata"
    var trainedDataPath = ""
    private lateinit var binding: ActivityMainBinding

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { imageUri ->
                // Suppose you have an ImageView that should contain the image:
                binding.img.setImageURI(imageUri)
                uriToBitmap(uri)?.let { extractText(it) }
            }
        }

    fun uriToBitmap(uri: Uri): Bitmap? {
        val inputStream = contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeStream(inputStream, null, options)
    }

    var tesseractFolder = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
        createTesseract()
        prepareDirectory(path + trainedData)
        binding.btn.setOnClickListener {

            getContent.launch("image/*")


        }
    }

    private fun prepareDirectory(path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(
                    "Ray",
                    "ERROR: Creation of directory $path failed, check does Android Manifest have permission to write to external storage."
                )
            }
        } else {
            Log.d("Ray", "Created directory $path")
        }
    }


    private fun extractText(bitmap: Bitmap) {
        val tessBaseApi = TessBaseAPI()
        tessBaseApi.setDebug(true)
        tessBaseApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
        tessBaseApi.init(tesseractFolder, "eng+rus")
        tessBaseApi.setImage(bitmap)
        binding.tvRecognize.text = tessBaseApi.utF8Text ?: "Произошла некая ошибка"
        tessBaseApi.end()

    }

    private fun createBitmapWithText(text: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Заливаем фон белым цветом
        canvas.drawColor(Color.WHITE)

        // Рисуем текст
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        val x = width / 2f
        val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)

        return bitmap
    }

    private fun copyTessDataFiles(path1: String) {
        try {
            val fileList = assets.list(path1)
            for (fileName in fileList!!) {
                Log.d("Ray", fileName)

                // open file within the assets folder
                // if it is not already there copy it to the sdcard
                val pathToDataFile: String = path + trainedData
                if (!File(pathToDataFile).exists()) {
                    val `in` = assets.open("$path1/$fileName")
                    val out: OutputStream = FileOutputStream(pathToDataFile)
                    Log.d("Ray", "pathhh $pathToDataFile")
                    // Transfer bytes from in to out
                    val buf = ByteArray(1024)
                    var len: Int
                    while (`in`.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                    `in`.close()
                    out.close()
                    Log.d("Ray", "Copied " + fileName + "to tessdata")
                }
            }
        } catch (e: IOException) {
            Log.e("Ray", "Unable to copy files to tessdata $e")
        }
    }

    private fun createTesseract() {
        val appFolder: File = filesDir

        val folder = File(appFolder, "tesseract")
        if (!folder.exists()) {
            folder.mkdir()
        }
        tesseractFolder = folder.absolutePath
        val subfolder = File(folder, "tessdata")
        if (!subfolder.exists()) {
            subfolder.mkdir()

            val file = File(subfolder, "eng.traineddata")
            trainedDataPath = file.absolutePath
            Log.d(
               "Ray",
                "Trained data filepath: " + trainedDataPath
            )

            if (!file.exists()) {
                try {
                    val fileOutputStream: FileOutputStream
                    val bytes: ByteArray = readRawTrainingData(this) ?: return
                    val bytesRus: ByteArray = readRawTrainingDataRus(this) ?: return
                    fileOutputStream = FileOutputStream(file)
                    fileOutputStream.write(bytes)
                    fileOutputStream.write(bytesRus)
                    fileOutputStream.close()
                    Log.d(
                        "Ray",
                        "Prepared training data file"
                    )
                } catch (e: FileNotFoundException) {
                    Log.e(
                        "Ray",
                        """
                Error opening training data file
                ${e.message}
                """.trimIndent()
                    )
                } catch (e: IOException) {
                    Log.e(
                        "Ray",
                        """
                Error opening training data file
                ${e.message}
                """.trimIndent()
                    )
                }
            } else {
            }
        }
        val fileRus = File(subfolder, "rus.traineddata")
        Log.d(
            "Ray",
            "Trained data filepath: " + trainedDataPath
        )

        if (!fileRus.exists()) {
            try {
                val fileOutputStream: FileOutputStream
                val bytesRus: ByteArray = readRawTrainingDataRus(this) ?: return
                fileOutputStream = FileOutputStream(fileRus)
                fileOutputStream.write(bytesRus)
                fileOutputStream.close()
                Log.d(
                    "Ray",
                    "Prepared training data file"
                )
            } catch (e: FileNotFoundException) {
                Log.e(
                    "Ray",
                    """
                Error opening training data file
                ${e.message}
                """.trimIndent()
                )
            } catch (e: IOException) {
                Log.e(
                    "Ray",
                    """
                Error opening training data file
                ${e.message}
                """.trimIndent()
                )
            }
        } else {
        }
    }


    private fun readRawTrainingData(context: Context): ByteArray? {
        try {
            val fileInputStream = context.resources
                .openRawResource(R.raw.eng_traineddata)

            val bos = ByteArrayOutputStream()
            val b = ByteArray(1024)
            var bytesRead: Int
            while (fileInputStream.read(b).also { bytesRead = it } != -1) {
                bos.write(b, 0, bytesRead)
            }
            fileInputStream.close()
            return bos.toByteArray()
        } catch (e: FileNotFoundException) {
            Log.e(
                "Ray",
                """
                Error reading raw training data file
                ${e.message}
                """.trimIndent()
            )
            return null
        } catch (e: IOException) {
            Log.e(
                "Ray",
                """
                Error reading raw training data file
                ${e.message}
                """.trimIndent()
            )
        }
        return null
    }

    private fun readRawTrainingDataRus(context: Context): ByteArray? {
        try {
            val fileInputStream = context.resources
                .openRawResource(R.raw.rus)

            val bos = ByteArrayOutputStream()
            val b = ByteArray(1024)
            var bytesRead: Int
            while (fileInputStream.read(b).also { bytesRead = it } != -1) {
                bos.write(b, 0, bytesRead)
            }
            fileInputStream.close()
            return bos.toByteArray()
        } catch (e: FileNotFoundException) {
            Log.e(
                "Ray",
                """
                Error reading raw training data file
                ${e.message}
                """.trimIndent()
            )
            return null
        } catch (e: IOException) {
            Log.e(
                "Ray",
                """
                Error reading raw training data file
                ${e.message}
                """.trimIndent()
            )
        }
        return null
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val permissionsToRequest = mutableListOf<String>()

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                100
            )
        } else {
        }
    }

}