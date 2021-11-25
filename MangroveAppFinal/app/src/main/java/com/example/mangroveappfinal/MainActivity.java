package com.example.mangroveappfinal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.auth.User;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.TensorOperator;
import org.tensorflow.lite.support.common.TensorProcessor;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {


    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1000;
    private static final int CAMERA_REQUEST_CODE = 1001;

    protected Interpreter tflite;
    private TensorImage inputImageBuffer;
    private  int imageSizeX;
    private  int imageSizeY;
    private  TensorBuffer outputProbabilityBuffer;
    private  TensorProcessor probabilityProcessor;
    private static final float IMAGE_MEAN = 0.0f;
    private static final float IMAGE_STD = 1.0f;
    private static final float PROBABILITY_MEAN = 0.0f;
    private static final float PROBABILITY_STD = 255.0f;
    private Bitmap bitmap;
    private List<String> labels;

    private int counter1 = 0;
    private int counter2 = 0;
    private int counter3 = 0;


    private ImageView imageView;
    private EditText editname;
    Button buclassify, opengallery, takepicture, info, savename,showdata;
    TextView classitext, cpagatpat, cbakhaw, cbungalon;
    Uri imageuri;


    private FirebaseDatabase db = FirebaseDatabase.getInstance();
    private DatabaseReference root = db.getReference().child("Users");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        initializeUIElements();
    }

    //--------------------------------------------------------------------------------------------------------------

    private void initializeUIElements(){
        imageView = findViewById(R.id.image_view);
        classitext = findViewById(R.id.species_name);
        buclassify= findViewById(R.id.classify);
        takepicture = findViewById(R.id.open_camera);
        opengallery = findViewById(R.id.open_gallery);
        info = findViewById(R.id.next);
        cpagatpat = findViewById(R.id.pagatpat_counter);
        cbakhaw = findViewById(R.id.bakhaw_counter);
        cbungalon = findViewById(R.id.bungalon_counter);
        savename = findViewById(R.id.save);
        showdata = findViewById(R.id.data);




        opengallery.setOnClickListener(v -> {
            Intent intent=new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent,"Select Picture"),12);
        });

        takepicture.setOnClickListener(v -> {
            if (hasPermission()){
                openCamera();
            }else{
                requestPermission();
            }
        });

        info.setOnClickListener(v -> openInfo());



        try{
            tflite=new Interpreter(loadmodelfile(this));
        }catch (Exception e) {
            e.printStackTrace();
        }

        buclassify.setOnClickListener(v -> {

            int imageTensorIndex = 0;
            int[] imageShape = tflite.getInputTensor(imageTensorIndex).shape(); // {1, height, width, 3}
            imageSizeY = imageShape[1];
            imageSizeX = imageShape[2];
            DataType imageDataType = tflite.getInputTensor(imageTensorIndex).dataType();

            int probabilityTensorIndex = 0;
            int[] probabilityShape =
                    tflite.getOutputTensor(probabilityTensorIndex).shape(); // {1, NUM_CLASSES}
            DataType probabilityDataType = tflite.getOutputTensor(probabilityTensorIndex).dataType();

            inputImageBuffer = new TensorImage(imageDataType);
            outputProbabilityBuffer = TensorBuffer.createFixedSize(probabilityShape, probabilityDataType);
            probabilityProcessor = new TensorProcessor.Builder().add(getPostprocessNormalizeOp()).build();

            inputImageBuffer = loadImage(bitmap);

            tflite.run(inputImageBuffer.getBuffer(),outputProbabilityBuffer.getBuffer().rewind());
            showresult();

        });


    }

    //----------------------------------Load Image from trained model ----------------------------------------------------------------------------

    private TensorImage loadImage(final Bitmap bitmap) {
        // Loads bitmap into a TensorImage.
        inputImageBuffer.load(bitmap);

        // Creates processor for the TensorImage.
        int cropSize = Math.min(bitmap.getWidth(), bitmap.getHeight());
        // TODO(b/143564309): Fuse ops inside ImageProcessor.
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeWithCropOrPadOp(cropSize, cropSize))
                        .add(new ResizeOp(imageSizeX, imageSizeY, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
                        .add(getPreprocessNormalizeOp())
                        .build();
        return imageProcessor.process(inputImageBuffer);
    }

        private TensorOperator getPreprocessNormalizeOp() {
             return new NormalizeOp(IMAGE_MEAN, IMAGE_STD);
    }
        private TensorOperator getPostprocessNormalizeOp(){
             return new NormalizeOp(PROBABILITY_MEAN, PROBABILITY_STD);
    }

    //-------------------------------- Load tflite model -----------------------------------------------------------------------------


    private MappedByteBuffer loadmodelfile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor=activity.getAssets().openFd("model.tflite");
        FileInputStream inputStream=new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startoffset = fileDescriptor.getStartOffset();
        long declaredLength=fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startoffset,declaredLength);
    }



    //--------------------------------- Show Result -----------------------------------------------------------------------------


    private void showresult(){

        try{
            labels = FileUtil.loadLabels(this,"labels.txt");
        }catch (Exception e){
            e.printStackTrace();
        }
        Map<String, Float> labeledProbability =
                new TensorLabel(labels, probabilityProcessor.process(outputProbabilityBuffer))
                        .getMapWithFloatValue();

        float maxValueInMap =(Collections.max(labeledProbability.values()));
        float minProbabilityThreshold = (float) 0.90;
        for (Map.Entry<String, Float> entry : labeledProbability.entrySet()) {
            Log.d("MyThreshold", String.valueOf(minProbabilityThreshold));
            if (entry.getValue()==maxValueInMap && maxValueInMap > minProbabilityThreshold) {
                classitext.setText(entry.getKey());
                makeToast("Success..");
            } if (entry.getValue()==maxValueInMap && maxValueInMap < minProbabilityThreshold){
                classitext.setText(" ");
                makeToast("Unknown Image.");
            }

        }
        enabledDisabled();
        passData();
    }
    //------------------- Pass Data ---------------------

    public void passData(){
        savename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String bakhaw,pagatpat,bungalon;

                pagatpat = cpagatpat.getText().toString();
                bakhaw = cbakhaw.getText().toString();
                bungalon = cbungalon.getText().toString();

                HashMap<String, String> userMap = new HashMap<>();


                userMap.put("pagatpat", pagatpat);
                userMap.put("bakhaw", bakhaw);
                userMap.put("bungalon", bungalon);





                root.push().setValue(userMap).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(getApplicationContext(), "Data Saved", Toast.LENGTH_SHORT).show();
                    }
                });
                showdata.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(new Intent(MainActivity.this, ShowActivity.class));
                    }
                });

            }
        });

        if (classitext.getText().toString().equals("Pagatpat")){
            counter3++;
            cpagatpat.setText(Integer.toString(counter3));
        }
        else if (classitext.getText().toString().equals("Bakhaw")){
            counter2++;
            cbakhaw.setText(Integer.toString(counter2));
        }
        else if (classitext.getText().toString().equals("Bungalon")){
            counter1++;
            cbungalon.setText(Integer.toString(counter1));
        }
    }
    //------------------------ Enable button -----------------------------------

    public void enabledDisabled(){
        if (classitext.getText().toString().equals("Pagatpat")){
            info.setEnabled(true);

        }
        else if(classitext.getText().toString().equals("Bakhaw")){
            info.setEnabled(true);

        }

        else if (classitext.getText().toString().equals("Bungalon")){
            info.setEnabled(true);
        }
        else { info.setEnabled(false);}

    }


    Toast t;
    private void makeToast(String s){
        if(t !=null)
            t.cancel();

        t = Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT);
        t.show();
    }

    //--------------------------------------------------------------------------------------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST_CODE){
            bitmap = (Bitmap) Objects.requireNonNull(Objects.requireNonNull(data).getExtras()).get("data");
            imageView.setImageBitmap(bitmap);
        }

        if(requestCode==12 && resultCode==RESULT_OK && data!=null) {
            imageuri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageuri);
                imageView.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        buclassify.setEnabled(true);

    }

    //--------------------------------------------------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CAMERA_PERMISSION_REQUEST_CODE){
            if (hasAllPermissions(grantResults)){
                openCamera();
            }else{
                requestPermission();
            }
        }
    }

    private boolean hasAllPermissions(int[] grantResults) {
        for (int result: grantResults){
            if (result == PackageManager.PERMISSION_DENIED)
                return false;
        }
        return true;
    }

    @SuppressLint("NewApi")
    private void requestPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
            Toast.makeText(this, "Camera Permission Required", Toast.LENGTH_SHORT).show();
        }
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @SuppressLint("NewApi")
    private boolean hasPermission() {
        return  checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    //--------------------------------------------------------------------------------------------------------------


    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
    }

    private void openInfo() {
        if (classitext.getText().toString().equals("Pagatpat")) {
            Intent i = new Intent(MainActivity.this, PagatpatInfo.class);
            startActivity(i);
        }
        else if (classitext.getText().toString().equals("Bungalon")) {
            Intent i = new Intent(MainActivity.this, BungalonInfo.class);
            startActivity(i);
        }
        else if (classitext.getText().toString().equals("Bakhaw")) {
            Intent i = new Intent(MainActivity.this, BakhawInfo.class);
            startActivity(i);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

}