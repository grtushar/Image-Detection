package lab.itsoul.com.imagedetection;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static final int GET_FROM_GALLERY = 3;
    private static final String TAG = "Main Activity";
    private ImageView photo;
    private TextView barcodeValue;
    private TextView noOfPerson;
    private TextView smileing;
    private TextView eyesOpened;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        FirebaseApp.initializeApp(MainActivity.this);
        photo = findViewById(R.id.photoIV);
        barcodeValue = findViewById(R.id.txtBarcode);
        noOfPerson = findViewById(R.id.txtNumPeople);
        smileing = findViewById(R.id.txtSmile);
        eyesOpened = findViewById(R.id.txtEyes);
    }

    public void pickPhoto(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, GET_FROM_GALLERY);
//        startActivityForResult(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI), GET_FROM_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GET_FROM_GALLERY && resultCode == Activity.RESULT_OK) {
            Uri selectedImage = data.getData();
            Bitmap bitmap;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                photo.setImageBitmap(Bitmap.createScaledBitmap(bitmap, (int) (photo.getWidth() * 1.5), (int) (photo.getHeight() * 1.5), true));
                resetValues();
                extractImageInfo(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void resetValues() {
        barcodeValue.setText("no");
        noOfPerson.setText("0");
        smileing.setText("no");
        eyesOpened.setText("no");
    }

    private void extractImageInfo(Bitmap bitmap) {
        FirebaseApp.initializeApp(this);

        final FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
        detectBarcode(image).addOnCompleteListener(new OnCompleteListener<List<FirebaseVisionBarcode>>() {
            @Override
            public void onComplete(@NonNull Task<List<FirebaseVisionBarcode>> task) {
                if(task.getResult().size() == 0) {
                    Log.d(TAG, "barcode detection failed, trying face detection!");
                    detectFace(image);
                } else {
                    Log.d(TAG, "barcode detected!");
                    barcodeValue.setText("yes");
                    noOfPerson.setText("0");
                    smileing.setText("no");
                    eyesOpened.setText("no");
                }
            }
        });
    }

    private void detectFace(FirebaseVisionImage image) {
        FirebaseVisionFaceDetectorOptions faceDetectorOptions =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setModeType(FirebaseVisionFaceDetectorOptions.ACCURATE_MODE)
                        .setLandmarkType(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationType(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .setMinFaceSize(0.15f)
                        .setTrackingEnabled(false)
                        .build();

        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance()
                .getVisionFaceDetector(faceDetectorOptions);

        Task<List<FirebaseVisionFace>> faceDetectionResult =
                faceDetector.detectInImage(image)
                        .addOnSuccessListener(
                                new OnSuccessListener<List<FirebaseVisionFace>>() {
                                    @Override
                                    public void onSuccess(List<FirebaseVisionFace> faces) {

                                        if(faces.size() == 0) {
                                            Log.d(TAG, "no face detected!");
                                            return;
                                        } else Log.d(TAG, "face detected!");

                                        barcodeValue.setText("no");
                                        noOfPerson.setText(Integer.toString(faces.size()));

                                        double smileProb = 0.0,
                                                eyeOpenProb = 0.0;

                                        for (FirebaseVisionFace face : faces) {
                                            smileProb += face.getSmilingProbability();
                                            eyeOpenProb += (face.getLeftEyeOpenProbability() + face.getRightEyeOpenProbability()) / 2.0;
                                        }

                                        smileProb /= faces.size();
                                        eyeOpenProb /= faces.size();

                                        smileing.setText(smileProb >= 0.5 ? "yes" : "no");
                                        eyesOpened.setText(eyeOpenProb >= 0.5 ? "yes" : "no");
                                    }
                                })
                        .addOnFailureListener(
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.d(TAG, "onFailure: face detection failed!");
                                    }
                                });
    }

    private Task<List<FirebaseVisionBarcode>> detectBarcode(FirebaseVisionImage image) {
        FirebaseVisionBarcodeDetectorOptions barcodeDetectorOptions =
                new FirebaseVisionBarcodeDetectorOptions.Builder()
                        .setBarcodeFormats(
                                FirebaseVisionBarcode.FORMAT_CODE_128,
                                FirebaseVisionBarcode.FORMAT_CODE_39,
                                FirebaseVisionBarcode.FORMAT_CODE_93,
                                FirebaseVisionBarcode.FORMAT_CODABAR,
                                FirebaseVisionBarcode.FORMAT_EAN_13,
                                FirebaseVisionBarcode.FORMAT_EAN_8,
                                FirebaseVisionBarcode.FORMAT_ITF,
                                FirebaseVisionBarcode.FORMAT_UPC_A,
                                FirebaseVisionBarcode.FORMAT_UPC_E,
                                FirebaseVisionBarcode.FORMAT_PDF417,
                                FirebaseVisionBarcode.FORMAT_QR_CODE,
                                FirebaseVisionBarcode.FORMAT_AZTEC)
                        .build();

        FirebaseVisionBarcodeDetector barcodeDetector = FirebaseVision.getInstance()
                .getVisionBarcodeDetector(barcodeDetectorOptions);

        Task<List<FirebaseVisionBarcode>> barcodeDetectionResult = barcodeDetector.detectInImage(image)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionBarcode>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionBarcode> barcodes) {

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {

                    }
                });

        return barcodeDetectionResult;
    }
}
