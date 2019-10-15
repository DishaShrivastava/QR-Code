package com.example.o4s;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import org.ddogleg.struct.FastQueue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import boofcv.abst.fiducial.QrCodeDetector;
import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.android.camera2.VisualizeCamera2Activity;
import boofcv.factory.fiducial.FactoryFiducial;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import georegression.struct.point.Point2D_F64;
import georegression.struct.shapes.Polygon2D_F64;

public class QrcodeActivity extends VisualizeCamera2Activity
{

    // QR Code detector. Use default configuration
    private QrCodeDetector<GrayU8> detector = FactoryFiducial.qrcode(null,GrayU8.class);


    // Used to display text info on the display
    private Paint paintText = new Paint();

    private Paint colorDetected = new Paint();


    private static final int NO_COORINATE = 2;
    private static final int NO_VERTICES = 4;
    private static final int NO_OF_QR = 2;
    private static boolean debug = true;

    // Create JSON Array for Data for all QR
    private static JSONObject FinalJsonData;
    //  private String message=""; // most recently decoded QR code
    private static Set<String> message=new TreeSet<>();


    // Storage for bounds of found QR Codes
    private final FastQueue<Polygon2D_F64> foundQR = new FastQueue<>(Polygon2D_F64.class,true);

    // work space for display
    Path path = new Path();

    public QrcodeActivity()
    {
        // The default behavior for selecting the camera's resolution is to
        // find the resolution which comes the closest to having this many
        // pixels.
        targetResolution = 1024*768;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.qrcode);
        FrameLayout surface = findViewById(R.id.camera_frame);

        //  process images in a gray scale format.
        setImageType(ImageType.single(GrayU8.class));

        // Configure paint used to display FPS
        paintText.setStrokeWidth(displayMetrics.density);
        paintText.setTextSize(displayMetrics.density);
        paintText.setTextAlign(Paint.Align.LEFT);
        paintText.setARGB(0xFF,0xFF,0xB0,0);
        paintText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        // The camera stream will now start after this function is called.
        startCamera(surface,null);

    }


    @Override
    protected void processImage(ImageBase image)
    {
        detector.process((GrayU8)image);

        synchronized (foundQR)
        {
            foundQR.reset();
            List<QrCode> found = detector.getDetections();
            for(QrCode qrCode:found){
                foundQR.grow().set(qrCode.bounds);
                message.add(qrCode.message);
                if(debug)
                    System.out.println("Message-------->" + message);
            }

        }
    }

    protected void onDrawFrame(SurfaceView view, Canvas canvas)
    {
        super.onDrawFrame(view, canvas);

        // Display info on the image being process and how fast input camera
        // stream is converted into a BoofCV format
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        canvas.drawText(String.format(Locale.getDefault(),
                "%d x %d Convert: %4.1f ",
                width,height,periodConvert.getAverage()),
                0,120,paintText);

        // correctly convert the coordinate system from
        // image pixels that were processed into display coordinates
        canvas.concat(imageToView);

        // Draw the bounding squares around the QR Codes
        synchronized (foundQR)
        {
            // Initialize all global objects
            init();
            if (debug) System.out.println(" foundQR :" + foundQR.size() +"\n");
            // If there is mote than Two QRs we can not calculate angel.
            // This error case is cached here
            if (foundQR.size() > NO_OF_QR) {
                System.out.println(" ERROR ERROR : foundQR :" + foundQR.size() +"\n");
                try {
                    FinalJsonData.put("Error: Found QR Greater than 2. Actual Number of QR :",foundQR.size());
                }catch (Exception e){

                    System.out.println("\n\n Error in Json ----------->>" + e.getMessage());
                }

                visitActivity();
                return;
            }
            // Scan till we get two QRs in camera focus
            if (foundQR.size() == NO_OF_QR) {
                // Create JSON Array for Center for all QR
                JSONArray jsonCenterArr = new JSONArray();
                //Construct Array of Json obj for both QRs
                JSONArray jsonQRArr = new JSONArray();
                for (int foundIdx = 0; foundIdx < NO_OF_QR ; foundIdx++) {

                    // Add data of each QR to QR array
                    String qrData = String.valueOf(message);
                    if (debug)
                        System.out.println(" \n QR Result ---> " + foundIdx + "Message: " + message.toString());

                    // calculation starts here
                    renderPolygon(foundQR.get(foundIdx), path, canvas, colorDetected, qrData, jsonCenterArr, jsonQRArr);

                }

                // Calculate the angle between two QR center and store in Angle array
                double angle = calculateAngle(jsonCenterArr);

                if (debug) {
                    System.out.println(" CENTER Array of QR -------> \n  " + jsonCenterArr.toString() + "\n");
                //    System.out.println(" QRs Data n Vertices Array -------> \n  " + jsonCenterArr.toString() + "\n");
                    System.out.println(" Angle of QR  -------> \n  " + angle + "\n");
                }
                // Now we got everything ( center, vertices, angle)
                // Construct the final Json
                constructFinalJsonObject(jsonQRArr, angle);

                visitActivity();
            }
        }


    }

    protected static void init(){
        FinalJsonData = new JSONObject();

    }

    protected static void renderPolygon(Polygon2D_F64 s,
                                        Path path ,
                                        Canvas canvas ,
                                        Paint paint,
                                        String qrData,
                                        JSONArray jsonCenterArr,
                                        JSONArray jsonQRArr) {

        JSONObject jsonCenter;
        JSONArray jsonVertexArr = new JSONArray();
        double[][] verticesQR1 = new double[NO_VERTICES][NO_COORINATE];
        path.reset();
        FastQueue<Point2D_F64> vertexes;
        vertexes = new FastQueue<Point2D_F64>(s.size(), Point2D_F64.class, true);
        for (int i = 0; i < s.size(); i++) {
            vertexes.grow().set(s.get(i));
        }

        Point2D_F64 p=vertexes.get(0);
        //First qr vertices
        for (int i = 0; i < NO_VERTICES; i++) {
            p = vertexes.get(i);
            for (int j = 0; j < NO_COORINATE; j++) {
                if (j % 2 == 0) {
                    verticesQR1[i][j] = p.x;
                }
                else {
                    verticesQR1[i][j] = p.y;
                }
            }
        }

        //interchange 3rd row 1st column with 4th row 1st column
        verticesQR1[2][0] = verticesQR1[2][0] + verticesQR1[3][0];
        verticesQR1[3][0] = verticesQR1[2][0] - verticesQR1[3][0];
        verticesQR1[2][0] = verticesQR1[2][0] - verticesQR1[3][0];

        //interchange 3rd row 2nd column with 4th row 2nd column
        verticesQR1[2][1] = verticesQR1[2][1] + verticesQR1[3][1];
        verticesQR1[3][1] = verticesQR1[2][1] - verticesQR1[3][1];
        verticesQR1[2][1] = verticesQR1[2][1] - verticesQR1[3][1];
        try {
            for (int i = 0; i < NO_VERTICES; i++) {
                JSONObject vertexJson = new JSONObject();
                for (int j = 0; j < NO_COORINATE; j++) {
                    if (j % 2 == 0)
                        vertexJson.put("X", verticesQR1[i][j]);
                    else
                        vertexJson.put("Y", verticesQR1[i][j]);
                }
                jsonVertexArr.put(vertexJson);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        jsonCenter = pass_center_coord(verticesQR1);


        jsonCenterArr.put(jsonCenter);
        constructJsonObject(jsonVertexArr,jsonCenter,qrData,jsonQRArr);
        System.out.println("Final Json Data:" + FinalJsonData.toString());
        path.lineTo((float) p.x, (float) p.y);
        path.close();
        canvas.drawPath(path, paint);

    }


    // Driver method
    protected static JSONObject pass_center_coord(double vertices[][])
    {

        double[][] centroid = new double[NO_VERTICES][NO_COORINATE];
        // Formula to calculate centroid1 when base is x1,x3
        centroid[0][0] = (vertices[0][0] + vertices[1][0] + vertices[2][0]) / 3; //upper//A
        centroid[0][1] = (vertices[0][1] + vertices[1][1] + vertices[2][1]) / 3;

        // Formula to calculate centroid2 when base is x1,x3
        centroid[1][0] = (vertices[1][0] + vertices[3][0] + vertices[2][0]) / 3; //lower//B
        centroid[1][1] = (vertices[1][1] + vertices[3][1] + vertices[2][1]) / 3;

        // Formula to calculate centroid2 when base is x1,x4
        centroid[2][0] = (vertices[0][0] + vertices[3][0] + vertices[2][0]) / 3; //left//C
        centroid[2][1] = (vertices[0][1] + vertices[3][1] + vertices[2][1]) / 3;
        // Formula to calculate centroid2 when base is x1,x4
        centroid[3][0] = (vertices[0][0] + vertices[3][0] + vertices[1][0]) / 3; //right//D
        centroid[3][1] = (vertices[0][1] + vertices[3][1] + vertices[1][1]) / 3;

        return lineLineIntersection(centroid);

    }

    protected static JSONObject lineLineIntersection( double[][] centroid )
    {

        // Line AB represented as a1x + b1y = c1//A,B,C,D
        double a1 = centroid[1][1] - centroid[0][1];
        double b1 = centroid[0][0] - centroid[1][0];
        double c1 = a1*(centroid[0][0]) + b1*(centroid[0][1]);

        // Line CD represented as a2x + b2y = c2
        double a2 = centroid[3][1] - centroid[2][1];
        double b2 = centroid[2][0] - centroid[3][0];
        double c2 = a2*(centroid[2][0])+ b2*(centroid[2][1]);

        double determinant = a1*b2 - a2*b1;

        // Create JSON Obj for center point
        JSONObject jsonCenter = new JSONObject();

        if (determinant != 0) {

            double x = (b2*c1 - b1*c2) / determinant;
            double y = (a1*c2 - a2*c1) / determinant;

            try {
                jsonCenter.put("X", x);
                jsonCenter.put("Y", y);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (debug)
                System.out.println(" Center -------> \n  "+ jsonCenter.toString() + "\n \n");

        } else {
            // The lines are parallel.
            System.out.print("The lines are parallel" +Double.MAX_VALUE+ "  " + Double.MAX_VALUE);
        }
        return jsonCenter;

    }

    protected static double calculateAngle( JSONArray jsonCenterArr)
    {
        // Store  angle of two QR
        double angle = 0 ;
        try {
            JSONObject jsonCenterQR1 = jsonCenterArr.getJSONObject(0);
            JSONObject jsonCenterQR2 = jsonCenterArr.getJSONObject(1);

            angle = (float) Math.toDegrees(
                    Math.atan2(
                            jsonCenterQR2.getDouble("Y") - jsonCenterQR1.getDouble("Y") ,
                            jsonCenterQR2.getDouble("X") - jsonCenterQR1.getDouble("X") )
            );

            if ( angle < 0 )
                angle = 360 + angle;
            else if ( angle > 180 )
                angle = 360 - angle;
            else
                angle = 180 - angle;

            if (debug)
                System.out.println(" Angle -------> \n  "+ angle + "\n \n");

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return angle;
    }


    protected static void constructJsonObject(JSONArray jsonVertexArr,
                                              JSONObject jsonCenter,
                                              String qrData,
                                              JSONArray jsonQRArr){

        try {

            // Create JSON Obj for Actual boundary Box ( TBD: Actual vs Projected Box)
            JSONObject boundingBoxActual = new JSONObject();
            boundingBoxActual.put("vertices", jsonVertexArr);

            // Create JSON Obj for Projected boundary Box  ( TBD: Actual vs Projected Box)
            JSONObject boundingBoxProjected = new JSONObject();
            boundingBoxProjected.put("vertices", jsonVertexArr);

            // Create JSON Obj optional data in QR for future use
            JSONObject jsonOthers = new JSONObject();
            jsonOthers.put("Others", "Others");

            // Create JSON Obj of all data in a QR
            JSONObject jsonQrData = new JSONObject();
            jsonQrData.put("data",qrData);
            jsonQrData.put("boundingBoxActual", boundingBoxActual);
            jsonQrData.put("boundingBoxProjected", boundingBoxProjected);
            jsonQrData.put("Center", jsonCenter);
            jsonQrData.put("otherData", jsonOthers);

            // Add to JSON Array of each QR
            jsonQRArr.put(jsonQrData);

            if (debug)
                System.out.println("QR Json Data:" + jsonQrData.toString());

        }catch (JSONException e) {
            e.printStackTrace();
        }

    }
    protected static void constructFinalJsonObject(JSONArray jsonQRArr, double angle){

        try {
            FinalJsonData.put("items",jsonQRArr);
            FinalJsonData.put("angleOfIncidence",angle);
            if (debug)
                System.out.println("Final Json Data:" + FinalJsonData.toString());

        }catch (JSONException e) {
            e.printStackTrace();
        }

    }

    protected void visitActivity() {
        Bundle basket= new Bundle();
        basket.putString("buffer",FinalJsonData.toString());
        Intent a=new Intent(this, ResponseActivity.class);
        a.putExtras(basket);
        startActivity(a);

    }
    @Override
    public void onBackPressed() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        this.startActivity(mainIntent);
    }

}