package com.sainivik.blurimageusingtenserflow;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.databinding.DataBindingUtil;

import com.sainivik.blurimageusingtenserflow.databinding.ActivityMainBinding;
import com.sainivik.blurimageusingtenserflow.utils.DeeplabProcessor;
import com.sainivik.blurimageusingtenserflow.utils.ImageUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private int blurPer = 0;
    private String beforeBlurPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        setClickListener();
    }

    private void setClickListener() {
        binding.btnPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkAndRequestPermissions()) {
                    selectImage();
                } else {

                }


            }
        });
        binding.btnConvertBlur.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AsyncTaskRunner().execute();
            }
        });
        binding.seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                blurPer = seekBar.getProgress();
                binding.tvPer.setText("" + blurPer + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }


    private File createImageFile() throws IOException {
        // Create an image file name
        String file_path =
                getExternalFilesDir(null).getAbsolutePath() +
                        "/BlurImages";
        File dir = new File(file_path);
        if (!dir.exists()) dir.mkdirs();
        OutputStream outFile = null;
        File file = new File(dir, String.valueOf(System.currentTimeMillis()) + ".jpg");
        beforeBlurPath = file.getAbsolutePath();
        return file;
    }

    /*method is used to get blur image data*/
    private Bitmap getBlurImage() {
        if (!DeeplabProcessor.isInitialized())
            DeeplabProcessor.initialize(getBaseContext());
        Bitmap bitmap = ImageUtils.decodeBitmapFromFile(beforeBlurPath, DeeplabProcessor.INPUT_SIZE, DeeplabProcessor.INPUT_SIZE);
        if (bitmap == null)
            Log.e("PotraitBlur", "Null Bitmap");
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        float resizeRatio = (float) DeeplabProcessor.INPUT_SIZE / Math.max(bitmap.getWidth(), bitmap.getHeight());
        int rw = Math.round(w * resizeRatio);
        int rh = Math.round(h * resizeRatio);

        bitmap = ImageUtils.tfResizeBilinear(bitmap, rw, rh);
        int ar[] = DeeplabProcessor.GetBlurredImage(bitmap);
        //Radius out of range (0 < r <= 25).
        int bRad = 2;
        int eRad = 1;
        if (blurPer > 10) {
            bRad = blurPer / 4;
            eRad = (blurPer / 5);
        }
        bitmap = getBitmap(ar, bitmap, bRad, eRad);
        return bitmap;


    }

    /*method is used to get blur bitmap*/
    public Bitmap getBitmap(int mOutputs[], Bitmap bitmap, int bRad, int eRad) {
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Bitmap blur = ImageUtils.RenderBlur(this, bitmap, bRad);
        Bitmap softBlur = ImageUtils.RenderBlur(this, bitmap, eRad);

        int imgMatrixEroded[][] = new int[w][h];
        int imgMatrixDilated[][] = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                imgMatrixEroded[x][y] = imgMatrixDilated[x][y] = mOutputs[y * w + x];
            }
        }
        imgMatrixDilated = ImageUtils.dilate(imgMatrixDilated, 1);
        imgMatrixEroded = ImageUtils.erode(imgMatrixEroded, 2);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                output.setPixel(x, y, imgMatrixDilated[x][y] == 1 ? (imgMatrixEroded[x][y] == 1 ? bitmap.getPixel(x, y) : softBlur.getPixel(x, y)) : blur.getPixel(x, y));
            }
        }
        return output;
    }


    /*dialog to give option to choose photo*/
    private void selectImage() {
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Add Photo!");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                if (options[item].equals("Take Photo")) {

                    try {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        File photoFile = createImageFile();
                        // Continue only if the File was successfully created
                        if (photoFile != null) {
                            Uri photoURI = FileProvider.getUriForFile(
                                    MainActivity.this,
                                    getPackageName() + ".file_provider",
                                    photoFile
                            );
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                            startActivityForResult(takePictureIntent, 1);
                        }
                    } catch (Exception ex) {
                        // Error occurred while creating the File
                        Toast.makeText(MainActivity.this, ex.getMessage(), Toast.LENGTH_SHORT).show();

                    }

                } else if (options[item].equals("Choose from Gallery")) {
                    Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(intent, 2);
                } else if (options[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            binding.llBluDetails.setVisibility(View.VISIBLE);
            if (requestCode == 1) {

                try {
                    Bitmap bitmap;
                    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();

                    try {
                        bitmap = BitmapFactory.decodeFile(beforeBlurPath, bitmapOptions);
                        binding.ivBeforeBlur.setImageBitmap(bitmap);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (requestCode == 2) {
                Uri selectedImage = data.getData();
                String[] filePath = {MediaStore.Images.Media.DATA};
                Cursor c = getContentResolver().query(selectedImage, filePath, null, null, null);
                c.moveToFirst();
                int columnIndex = c.getColumnIndex(filePath[0]);
                String picturePath = c.getString(columnIndex);
                c.close();
                beforeBlurPath = picturePath;
                Bitmap thumbnail = (BitmapFactory.decodeFile(picturePath));
                Log.w("path of", picturePath + "");
                binding.ivBeforeBlur.setImageBitmap(thumbnail);
            }
        }
    }

    private class AsyncTaskRunner extends AsyncTask<String, String, Bitmap> {

        private String resp;
        ProgressDialog progressDialog;

        @Override
        protected Bitmap doInBackground(String... params) {

            return getBlurImage();
        }


        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                binding.ivAfterBlur.setImageBitmap(result);
            } else {
                Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
            }
            // execution of result of Long time consuming operation
            progressDialog.dismiss();

        }

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(MainActivity.this,
                    "ProgressDialog",
                    "Please wait.....");
        }


        @Override
        protected void onProgressUpdate(String... text) {


        }
    }

    public Boolean checkAndRequestPermissions() {
        int ExtstorePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
        );
        int cameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        );
        ArrayList<String> listPermissionsNeeded = new ArrayList<String>();
        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (ExtstorePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded
                    .add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, listPermissionsNeeded.toArray(new String[0]), 1234

            );
            return false;
        }
        return true;
    }

}