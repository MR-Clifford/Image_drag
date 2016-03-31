package xml_mike.image_drag;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class MainActivity extends AppCompatActivity implements View.OnTouchListener {

    private static final int SELECT_PICTURE = 1;
    private ImageView mainImageView;
    private float frameWidth = 0f;
    private float frameHeight = 0f;

    Matrix matrix = new Matrix();
    Matrix savedMatrix = new Matrix();

    // We can be in one of these 3 states
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    // Remember some things for zooming & rotation
    PointF start = new PointF();
    PointF mid = new PointF();
    float oldDist = 1f;
    float[] lastEvent = null;
    float direction = 0f;
    float newRot = 0f;
    int currentRotation = 0;

    // Remember some things for image
    BitmapDrawable drawableBitmap;

    //should replace with Rectf
    float currentImageWidth = 0;
    float currentImageHeight = 0;
    float currentX = 0;
    float currentY = 0;

    float[] matrixValues = new float[9];
    float currentScale = 1.0f;
    float minScale = 1f;
    float maxScale = 2.5f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainImageView = (ImageView) findViewById(R.id.imageView);

        mainImageView.setOnTouchListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.openGallery:
                showGalleryForResult();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    //start android activiy start
    public void showGalleryForResult(){
        //start gallery activity to show images stored in android device
        Intent i = new Intent(Intent.ACTION_PICK,android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, SELECT_PICTURE);
    }

    //setup & initialise activity after image is selected.
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {

                Uri selectedImageUri = data.getData();
                mainImageView.setImageURI(selectedImageUri);

                drawableBitmap = ((BitmapDrawable) mainImageView.getDrawable());
                Drawable drawable =  mainImageView.getDrawable();
                currentImageWidth =  drawable.getIntrinsicWidth();
                currentImageHeight = drawable.getIntrinsicHeight();
                currentRotation = 0;

                matrix = new Matrix();

                if(currentImageHeight > currentImageWidth){
                    mainImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    minScale = (float) mainImageView.getWidth() / currentImageWidth;
                } else {
                    mainImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    minScale = (float) mainImageView.getHeight() / currentImageHeight;
                }

                mainImageView.requestLayout();
            }
        }
    }

    //main workhorse of application, the touch functionality for imageview.
    public boolean onTouch(View v, MotionEvent event) {
        ImageView view = (ImageView) v;
        // make the image scalable as a matrix
        view.setScaleType(ImageView.ScaleType.MATRIX);
        float scale = 1.0f;
        if(drawableBitmap != null) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {

                case MotionEvent.ACTION_DOWN: //first finger down only
                    savedMatrix.set(matrix);
                    start.set(event.getX(), event.getY());
                    mode = DRAG;
                    lastEvent = null;
                    break;
                case MotionEvent.ACTION_UP: //first finger lifted

                    adjustTranslateInBounds();

                    break;
                case MotionEvent.ACTION_POINTER_UP: //second finger lifted
                    mode = NONE;
                    fixRotation(view);
                    break;
                case MotionEvent.ACTION_POINTER_DOWN: //second finger down

                    //assumption made on it being landscape images
                    if (currentRotation / 90 == 1 || currentRotation / 90 == 3) {
                        setMinScale(mainImageView.getHeight(), Math.round(currentImageHeight));
                    } else {
                        setMinScale(mainImageView.getWidth(), Math.round(currentImageHeight));
                    }

                    oldDist = spacing(event); // calculates the distance between two points where user touched.
                    // minimal distance between both the fingers
                    if (oldDist > 5f) {
                        savedMatrix.set(matrix);
                        midPoint(mid, event); // sets the mid-point of the straight line between two points where user touched.
                        mode = ZOOM;
                    }
                    lastEvent = new float[4];
                    lastEvent[0] = event.getX(0);
                    lastEvent[1] = event.getX(1);
                    lastEvent[2] = event.getY(0);
                    lastEvent[3] = event.getY(1);
                    direction = rotation(event);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG) { //movement of first finger
                        matrix.set(savedMatrix);
                        if (view.getLeft() >= 0) {
                            float mx = event.getX() - start.x;
                            float my = event.getY() - start.y;
                            currentX += mx;
                            currentY += my;
                            matrix.postTranslate(mx, my);
                        }
                    } else if (mode == ZOOM) { //pinch zooming && rotate
                        float newDist = spacing(event);
                        if (newDist > 5f) {
                            matrix.set(savedMatrix);
                            scale = newDist / oldDist;
                            matrix.getValues(matrixValues);
                            if (oldDist != 0) {
                                matrix.preScale(scale, scale, mid.x, mid.y);
                            }

                            float scalex = matrixValues[Matrix.MSCALE_X];
                            float skewy = matrixValues[Matrix.MSKEW_Y];
                            currentScale = (float) Math.sqrt(scalex * scalex + skewy * skewy); // get real scale regardless of angle

                            //Meant to prevent scale, needs work
                            if (currentScale <= minScale) {
                                matrix.postScale((minScale) / currentScale, (minScale) / currentScale, mid.x, mid.y);
                            } else if (currentScale >= maxScale) {
                                matrix.postScale((maxScale) / currentScale, (maxScale) / currentScale, mid.x, mid.y);
                            }

                        }
                        if (lastEvent != null) {
                            newRot = rotation(event);
                            float r = newRot - direction;
                            matrix.postRotate(r, view.getMeasuredWidth() / 2, view.getMeasuredHeight() / 2);
                        }
                    }
                    break;

            }

            // Perform the transformation
            view.setImageMatrix(matrix);
        }

        return true; // indicate event was handled
    }

    // determine the distance between two events
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float)  Math.sqrt(x * x + y * y); // using math instead of FloatMath as deprecated in api 23
    }

    //mid point of two touch events
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    //used to to set minimum zoom/scale
    private void setMinScale(int viewImageWidth, int length){
        minScale = (float) viewImageWidth / length;
    }

    //detect what angle the event is.
    private float rotation(MotionEvent event) {
        double delta_x = (event.getX(0) - event.getX(1));
        double delta_y = (event.getY(0) - event.getY(1));
        double radians = Math.atan2(delta_y, delta_x);

        return (float) Math.toDegrees(radians);
    }

    //get real scale regardless of angle
    private float getRealScale(float scalex, float skewy){
        return currentScale = (float) Math.sqrt(scalex * scalex + skewy * skewy);
    }

    //ensure that all rotations are 90 degrees
    private float fixRotation(View view){
        float angle = (newRot - direction);
        int rotate = 0;

        //if angle is above 45 or less than 45 than
        if(angle >= 45  && angle <= 360) {
            rotate = 90;
            if(angle >= 135) rotate += 90;
            if(angle >= 225) rotate += 90;
            matrix.postRotate(rotate, view.getMeasuredWidth() / 2, view.getMeasuredHeight() / 2);
        } else if(angle <= -45 && angle >= -360 ) {
            rotate = - 90;
            if(angle <= -135) rotate -= 90;
            if(angle <= -225) rotate -= 90;
            matrix.postRotate(rotate, view.getMeasuredWidth() / 2, view.getMeasuredHeight() / 2);
        }

        currentRotation +=rotate;

        //ensure that current rotation is in between 0 && 360
        if(currentRotation < 0){
            currentRotation += 360;
        } else if (currentRotation > 360) {
            currentRotation -= 360;
        }

        //remove any additional angle/skew
        matrix.postRotate(-angle, view.getMeasuredWidth() / 2, view.getMeasuredHeight() / 2); // returns rotation to the closest 90 degrees

        // calculate the degree of rotation
        float fAngle = (int) Math.round(Math.atan2(matrixValues[Matrix.MSKEW_X], matrixValues[Matrix.MSCALE_X]) * (180 / Math.PI));

        //Attempt to fix corner use cases where odd finger movement causes postRotate not to function as intended.
        if(Math.round(fAngle) % 90 != 0 ){
            fAngle = (90 * (Math.round((fAngle+45)/90))) - fAngle; //closest 90 degrees
            matrix.postRotate(-fAngle, view.getMeasuredWidth() / 2, view.getMeasuredHeight() / 2);
        }

        return angle;
    }

    //if image is outside these bounds then translate to relative position
    private void adjustTranslateInBounds(){
        matrix.getValues(matrixValues);

        float scalex = matrixValues[Matrix.MSCALE_X];
        float skewy = matrixValues[Matrix.MSKEW_Y];
        currentScale = getRealScale(scalex, skewy);

        currentImageHeight =  mainImageView.getDrawable().getIntrinsicHeight() * currentScale;
        currentImageWidth =  mainImageView.getDrawable().getIntrinsicWidth() * currentScale;
        currentY = -(matrixValues[Matrix.MTRANS_Y]) ;
        currentX = -(matrixValues[Matrix.MTRANS_X]);
        float frameWidth = mainImageView.getWidth();
        float frameHeight = mainImageView.getHeight();
        int rotation = currentRotation;

        //depending on rotation preform action to keep in bounds
        switch (rotation){
            case 0:

                if(currentY < 0) matrixValues[Matrix.MTRANS_Y] = 0;
                else if(currentImageHeight-currentY<frameHeight) matrixValues[Matrix.MTRANS_Y] = -(currentImageHeight - (frameHeight));
                if(currentX < 0) matrixValues[Matrix.MTRANS_X] = 0;
                else if(currentImageWidth-currentX<frameWidth)  matrixValues[Matrix.MTRANS_X] = -(currentImageWidth - (frameWidth));

                break;

            case 90:

                if(currentX < -currentImageHeight) matrixValues[Matrix.MTRANS_X] = currentImageHeight;
                else if(currentX > -frameWidth) matrixValues[Matrix.MTRANS_X] = frameWidth;
                if(currentY < 0) matrixValues[Matrix.MTRANS_Y] = 0;
                else if(currentY > (currentImageWidth - frameHeight)) matrixValues[Matrix.MTRANS_Y] = -(currentImageWidth - frameHeight);

                break;

            case 180:

                if(currentY < -currentImageHeight) matrixValues[Matrix.MTRANS_Y] = currentImageHeight;
                else if(currentY > -frameHeight) matrixValues[Matrix.MTRANS_Y] = frameHeight;
                if(currentX < -currentImageWidth) matrixValues[Matrix.MTRANS_X] = currentImageWidth;
                else if(currentX > -frameWidth) matrixValues[Matrix.MTRANS_X] = frameWidth;

                break;

            case 270:
                if(currentX > (currentImageHeight-frameWidth)) matrixValues[Matrix.MTRANS_X] = -(currentImageHeight-frameWidth);
                else if(currentX < 0) matrixValues[Matrix.MTRANS_X] = 0;
                if(currentY < -currentImageWidth) matrixValues[Matrix.MTRANS_Y] = currentImageWidth;
                else if(currentY > -frameHeight) matrixValues[Matrix.MTRANS_Y] = frameHeight;

                break;

        }

        matrix.setValues(matrixValues);
    }

    //Prevent ANR issues by placing running I/O code in separate thread.
    public void saveImage(View view){
        frameHeight = mainImageView.getHeight();
        frameWidth = mainImageView.getWidth();
        drawableBitmap = ((BitmapDrawable) mainImageView.getDrawable());

        Toast.makeText(MainActivity.this, "Saving Image", Toast.LENGTH_SHORT).show();

        new SaveToExternalMemory().execute();
    }

    //asyncTask using local
    private class SaveToExternalMemory extends AsyncTask<Void, Void , Integer> {
        protected Integer doInBackground(Void... none) {


            String root = Environment.getExternalStorageDirectory().getPath();
            File myDir = new File(root);
            myDir.mkdirs();
            File mypath=new File(myDir,"Touchnote.jpg");

            if(mypath.exists()) mypath.delete();

            matrix.getValues(matrixValues);

            float scalex = matrixValues[Matrix.MSCALE_X];
            float skewy = matrixValues[Matrix.MSKEW_Y];

            currentScale = getRealScale(scalex, skewy);

            if(drawableBitmap != null) {
                try {
                    FileOutputStream fos = new FileOutputStream(mypath);
                    //we adjust the scale as the intrinsic width we used before for scale
                    int drawableWidth = drawableBitmap.getBitmap().getWidth();
                    int drawableHeight = drawableBitmap.getBitmap().getHeight();

                    //need to perfect scale for
                    float newScaleX = (drawableWidth / (frameWidth))/currentScale;
                    float newScaleY = (drawableHeight / (frameHeight))/currentScale;

                    //average
                    float nsX = (int) (newScaleX*currentScale) / 2;
                    float nsY = (int) (newScaleX*currentScale) / 2 ;

                    //need to
                    int saveX = Math.round(Math.abs(matrixValues[Matrix.MTRANS_X]) / nsX);
                    int saveY = Math.round(Math.abs(matrixValues[Matrix.MTRANS_Y]) / nsY);

                    int saveWidth =  Math.round(Math.abs( ( frameWidth *  nsX ) ) );//Math.round(Math.abs(matrixValues[Matrix.MTRANS_X]) ));
                    int saveHeight = Math.round(Math.abs( ( frameHeight * nsY ) ) );//Math.round(Math.abs(matrixValues[Matrix.MTRANS_Y]) ));

                    Log.e("QQ","X:"+saveX+" Y:"+saveY+" W:"+saveWidth+ " H:" + saveHeight);
                    Log.e("scale","X:"+currentScale+" Y:"+currentScale+" W:"+frameWidth+ " H:" + frameHeight);
                    Log.e("scale","X:"+newScaleX+" Y:"+newScaleY+" W:"+drawableBitmap.getBitmap().getWidth()+ " H:" + drawableBitmap.getBitmap().getHeight());

                    Bitmap saveBitmap = Bitmap.createBitmap(drawableBitmap.getBitmap(), saveX, saveY, saveWidth, saveHeight, matrix, true );
                    saveBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos); //save to 90% Quality
                    //added to media library for user
                    MediaStore.Images.Media.insertImage(getContentResolver(), mypath.getAbsolutePath(), mypath.getName(), mypath.getName());
                    fos.flush();
                    fos.close();
                    saveBitmap = null;
                    System.gc();


                } catch (IOException | IllegalArgumentException | OutOfMemoryError e ) {
                    e.printStackTrace();
                    return 2;
                }
            } else {
                Toast.makeText(MainActivity.this, "Please select an image before saving.", Toast.LENGTH_SHORT).show();
            }

            return 1;
        }

        protected void onPostExecute(Integer code) {
            if(code == 1)
                Toast.makeText(MainActivity.this, "Your file was saved", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(MainActivity.this, "There was an error processing your image", Toast.LENGTH_SHORT).show();
        }
    }
}
