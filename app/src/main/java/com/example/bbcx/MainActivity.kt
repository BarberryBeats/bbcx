package com.example.bbcx

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.bbcx.databinding.ActivityMainBinding
import com.googlecode.tesseract.android.TessBaseAPI
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*


class MainActivity : AppCompatActivity() {

    private val path = Environment.getExternalStorageDirectory().absolutePath + "/com.example.bbcx/"
    private val trainedData = "tessdata"
    private var trainedDataPath = ""
    private lateinit var binding: ActivityMainBinding

    private var tesseractFolder = ""

    private val getContentFromGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { imageUri ->
                // Suppose you have an ImageView that should contain the image:
                binding.img.setImageURI(imageUri)
                binding.progressBar.visibility = VISIBLE
                binding.tvRecognize.text = ""
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        uriToBitmap(uri)?.let { bitmap ->
                            val result = extractText(bitmap)
                            withContext(Dispatchers.Main) {
                                binding.tvRecognize.text = result
                                binding.progressBar.visibility = GONE

                            }
                        }
                    }
                }

            }
        }
    private val getContentFromCamera = registerForActivityResult(takePicture()) { uri ->
        binding.img.setImageURI(uri)
        binding.tvRecognize.text = ""

        binding.progressBar.visibility = VISIBLE

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                uriToBitmap(uri)?.let { bitmap ->
                    val result = extractText(bitmap)
                    withContext(Dispatchers.Main) {
                        binding.tvRecognize.text = result
                        binding.progressBar.visibility = GONE
                    }
                }
            }
        }

    }


   private fun takePicture(): ActivityResultContract<Unit, Uri> {
        return object : ActivityResultContract<Unit, Uri>() {
            override fun createIntent(context: Context, input: Unit): Intent {
                return Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri {
                return intent?.data!!
            }

        }
    }
    private fun uriToBitmap(uri: Uri): Bitmap? {
        val inputStream = contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeStream(inputStream, null, options)
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()
        createTesseract()
        prepareDirectory(path + trainedData)
        binding.btnGallery.setOnClickListener {
            getContentFromGallery.launch("image/*")
        }
        binding.btnCamera.setOnClickListener {
            getContentFromCamera.launch(Unit)
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

    private suspend fun extractText(bitmap: Bitmap?): String {
        return withContext(Dispatchers.Default) {
            val tessBaseApi = TessBaseAPI()
            tessBaseApi.setDebug(true)
            tessBaseApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
            tessBaseApi.init(tesseractFolder, "eng+rus")
            if (bitmap != null) {
                tessBaseApi.setImage(bitmap)
            } else {
            }
            val result = tessBaseApi.utF8Text ?: "Произошла некая ошибка"
            tessBaseApi.end()
            result

        }
    }

    /*private fun extractText(bitmap: Bitmap) {
        val tessBaseApi = TessBaseAPI()
        tessBaseApi.setDebug(true)
        tessBaseApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD
        tessBaseApi.init(tesseractFolder, "eng+rus")
        tessBaseApi.setImage(bitmap)
        binding.tvRecognize.text = tessBaseApi.utF8Text ?: "Произошла некая ошибка"
        tessBaseApi.end()

    }*/

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
            writeRawFile(subfolder, R.raw.eng_traineddata, "eng.traineddata")
            writeRawFile(subfolder, R.raw.rus, "rus.traineddata")
        }

    }

    private fun writeRawFile(subfolder: File, rawDataId: Int, rawName: String) {

        val file = File(subfolder, rawName)
        trainedDataPath = file.absolutePath
        Log.d(
            "Ray",
            "Trained data filepath: $trainedDataPath"
        )

        if (!file.exists()) {
            try {
                val bytesRus: ByteArray = readRawTrainingData(this, rawDataId) ?: return
                val fileOutputStream = FileOutputStream(file)
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


    private fun readRawTrainingData(context: Context, rawDataId: Int): ByteArray? {
        try {
            val fileInputStream = context.resources
                .openRawResource(rawDataId)

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