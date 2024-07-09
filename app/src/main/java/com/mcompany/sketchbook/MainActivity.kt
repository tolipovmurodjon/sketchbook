package com.mcompany.sketchbook

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var imageButtonCurrentPaint : ImageButton? = null
    private var progressDialog : Dialog? = null

    val openGalleryLauncher : ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){

        result ->
        if (result.resultCode == RESULT_OK && result.data != null){
            val imageBG : ImageView = findViewById(R.id.imageViewBG)

            imageBG.setImageURI(result.data?.data)


        }


    }

    private val requestPermission : ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){

        permissions ->
        permissions.entries.forEach{

            val permissionName = it.key
            val isGranted = it.value

            if (isGranted){

                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                openGalleryLauncher.launch(intent)


            } else{

                if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){

                    Toast.makeText(this, "Permission is denied for Storage!", Toast.LENGTH_SHORT).show()

                }

            }



        }

    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.linearLayout)
        imageButtonCurrentPaint = linearLayoutPaintColors[0] as ImageButton
        imageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))



        val brushSizeSelector : ImageButton = findViewById(R.id.imageBrushSizeSelector)
        brushSizeSelector.setOnClickListener{
            showBrushSizeChooserDialog()

        }

        val undo : ImageButton = findViewById(R.id.imageUndo)
        undo.setOnClickListener{
            drawingView?.onClickUndo()
        }

        val redo : ImageButton = findViewById(R.id.imageRedo)
        redo.setOnClickListener{
            drawingView?.onClickRedo()
        }

        val save : ImageButton = findViewById(R.id.imageSave)
        save.setOnClickListener{

            if (isReadStorageAllowed()){


                lifecycleScope.launch {
                    showProgressDialog()

                    delay(3000)
                    val flDrawingView : FrameLayout = findViewById(R.id.frame_layout_container)

                    saveBitmap(flDrawingView)

                    cancelProgressDialog()
                }



            }




        }




        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val gallery : ImageButton = findViewById(R.id.imageGallery)
        gallery.setOnClickListener{

            requestStoragePermission()

        }

    }

    private fun requestStoragePermission(){

        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){

            showRationalDialog("Permission is needed for this application", "If you don't provide it, application will crash!")


        } else{
            requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }



    }

    private fun showBrushSizeChooserDialog(){

        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")

        val buttonSmall = brushDialog.findViewById<ImageButton>(R.id.small_brush)
        buttonSmall.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()

        }

        val buttonMedium = brushDialog.findViewById<ImageButton>(R.id.medium_brush)
        buttonMedium.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()

        }

        val buttonBig = brushDialog.findViewById<ImageButton>(R.id.big_brush)
        buttonBig.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()

        }

        brushDialog.show()

    }

    fun paintClicked(view : View){

        if (view !== imageButtonCurrentPaint){

            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_pressed))
            imageButtonCurrentPaint!!.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.pallet_normal))

            imageButtonCurrentPaint = view



        }


    }



    private fun getBitmapFromView(view: View) :Bitmap{

        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background

        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        } else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap




    }

    private fun saveBitmap(view : View) {
        lifecycleScope.launch {
            val bitmap = getBitmapFromView(view) // replace with your view

            // save bitmap to gallery based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = applicationContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "DrawingApp_${System.currentTimeMillis() / 1000}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                }

                var imageUri: Uri? = null
                resolver.run {
                    imageUri = insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    imageUri?.let {
                        openOutputStream(it)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                        }
                    }
                }

                if (imageUri != null) {
                    Toast.makeText(this@MainActivity, "File saved successfully: $imageUri", Toast.LENGTH_SHORT).show()
                    shareImage(imageUri!!)
                } else {
                    Toast.makeText(this@MainActivity, "Something went wrong while saving the file", Toast.LENGTH_SHORT).show()
                }

            } else {
                val filePath = saveBitmapFile(bitmap)
                if (filePath.isNotEmpty()) {

                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    val file = File(filePath)
                    val contentUri = Uri.fromFile(file)
                    mediaScanIntent.data = contentUri
                    sendBroadcast(mediaScanIntent)
                    cancelProgressDialog()

                    Toast.makeText(this@MainActivity, "File saved successfully: $filePath", Toast.LENGTH_SHORT).show()





                } else {
                    Toast.makeText(this@MainActivity, "Something went wrong while saving the file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun saveBitmapFile(bitmap: Bitmap): String{

        var result = ""

        withContext(Dispatchers.IO){

            if (bitmap != null){

                try {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString() + File.separator + "Sketchbook"+ System.currentTimeMillis() /1000 + ".png")

                    val fo = FileOutputStream(f)

                    fo.write(bytes.toByteArray())
                    fo.close()


                    result = f.absolutePath

                    runOnUiThread{





                        if (result.isNotEmpty()){
                            Toast.makeText(this@MainActivity.applicationContext, "Image saved to $result", Toast.LENGTH_SHORT).show()
                            val uri = FileProvider.getUriForFile(baseContext, "com.mcompany.sketchbook.fileprovider", f)
                            shareImage(uri)



                        } else{
                            Toast.makeText(applicationContext, "Retry later!", Toast.LENGTH_SHORT).show()

                        }



                    }

                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }

            }

        }

        return result





    }
    private fun shareImage(uri: Uri){
        val shareIntent = Intent()
        shareIntent.action = Intent.ACTION_SEND
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = "image/jpeg"
        startActivity(Intent.createChooser(shareIntent,"Share"))
    }





    private fun cancelProgressDialog() {
        if (progressDialog != null) {
            progressDialog?.dismiss()
            progressDialog = null
        }
    }


    private fun showProgressDialog(){


        progressDialog = Dialog(this)

        progressDialog?.setContentView(R.layout.progress_dialog)

        progressDialog?.show()




    }







    private fun isReadStorageAllowed() : Boolean{

        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED

    }






    private fun showRationalDialog(
        title : String,
        message : String
    ){

        val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message).setPositiveButton("Cancel"){dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()




    }





}