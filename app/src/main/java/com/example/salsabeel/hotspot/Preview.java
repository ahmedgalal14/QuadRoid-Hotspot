package com.example.salsabeel.hotspot;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

class Preview extends SurfaceView implements SurfaceHolder.Callback { 

    private static final String TAG = "com.example.salsabeel.HotSpot.Preview";
    SurfaceHolder mHolder;
    public Camera camera;
    public Parameters param;
    public Size size;

    ServerSocket server;   // 8000
    Socket video_receiver_socket;
    DataOutputStream outStream =null;
    InputStream inputStream;
    DataInputStream dataInputStream;

    TextView tv,text;
    ProgressDialog pd;

    String message;
    boolean serverRunning = false;
    boolean halt = false;
    public int count=0;

    Activity mainActivity;

    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    UsbManager usbManager;
    UsbDevice usbDevice;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    boolean granted;


    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            String data = null;
            try {
                data = new String(arg0, "UTF-8");
                data.concat("/n");
                //tvAppend(textView, data);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                //Toast.makeText(context, "USB PERMISSION ALLOWED", Toast.LENGTH_SHORT).show();
                if (granted) {
                    connection = usbManager.openDevice(usbDevice);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(usbDevice, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            Toast.makeText(context, "SERIAL PORT OPEN", Toast.LENGTH_SHORT).show();
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

                        } else {
                            Toast.makeText(context, "SERIAL PORT NOT OPEN", Toast.LENGTH_SHORT).show();
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                startSerial();
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                try {
                    serialPort.close();
                }
                catch (Exception e)
                {
                    Log.i("error","serial port not open to close it.");
                }
            }
        }
    };


    Preview(Context context, Activity activity) {
        super(context);
        mHolder = getHolder();// initialize the Surface Holder
        mHolder.addCallback(this); // add the call back to the surface holder
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        this.mainActivity = activity;


        text = (TextView) mainActivity.findViewById(R.id.msgLabelView);
        tv = (TextView) mainActivity.findViewById(R.id.msgView);


        usbManager = (UsbManager) mainActivity.getSystemService(mainActivity.USB_SERVICE);

        //setUiEnabled(false);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mainActivity.registerReceiver(broadcastReceiver, filter);


    }
    public static boolean takepic=true;
    // Called once the holder is ready
    public void surfaceCreated(SurfaceHolder holder) {
    // The Surface has been created, acquire the camera and tell it where
    // to draw.

        if ( camera == null)
            camera = Camera.open();// activate the camera
            param = camera.getParameters();// acquire the parameters for the camera
            size = param.getPreviewSize();// get the size of each frame captured by the camera
            holder.setSizeFromLayout();
            camera.setParameters(param);// setting the parameters to the camera but this line is not required
            mHolder = holder;

            new Thread(new Runnable() {
            @Override
            public void run() {
                   try {
                       server = new ServerSocket(8000); //opening tcp sockets at port 8000
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
                   try {
                       video_receiver_socket = server.accept();// accepting incoming connections i.e. the video sender socket
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
                   mainActivity.runOnUiThread(new Runnable() {
                       @Override
                       public void run() {
                           startSerial();
                           new receiveDataTask().execute();
                       }
                   });
                   try {
                       outStream=new DataOutputStream(video_receiver_socket.getOutputStream());//open an outputstream to the socket for sending the image data
                   }
                   catch (IOException e)
                   {
                       e.printStackTrace();
                   }

                   try {
                       camera.setPreviewDisplay(mHolder);

                       camera.setPreviewCallback(new PreviewCallback() {
                           // Called for each frame previewed
                           public void onPreviewFrame(byte[] data, Camera camera) {

                               try {
                                       YuvImage yuv_image = new YuvImage(data, param.getPreviewFormat(), size.width, size.height, null); // all bytes are in YUV format therefore to use the YUV helper functions we are putting in a YUV object
                                       Rect rect = new Rect(0, 0, size.width, size.height); // size of image sent to the server
                                       Log.i("size", String.valueOf(size.width) + "," + String.valueOf(size.height));
                                       Log.i("image", "rect");
                                       ByteArrayOutputStream output_stream = new ByteArrayOutputStream();
                                       yuv_image.compressToJpeg(rect, 80, output_stream);// image has now been converted to the jpg format and bytes have been written to the output_stream object

                                       byte[] tmp = output_stream.toByteArray();//getting the byte array
                                       outStream.writeInt(tmp.length);// sending the size of the array
                                       Log.i("stream", "write");
                                       outStream.write(tmp);// writing the array to the socket output stream
                                       outStream.flush();
                                       System.gc();

                                       Log.d(TAG, "onPreviewFrame - wrote bytes: " + data.length);

                                   } catch (Exception e) {
                                       e.printStackTrace();
                                   }
                           }
                       });

                   } catch (IOException e) {
                       e.printStackTrace();
                   }
               }
         }).start();
  }


  // Called when the holder is destroyed
  public void surfaceDestroyed(SurfaceHolder holder) {  
        camera.stopPreview(); // preview will stop once user exits the application screen
        camera.setPreviewCallback(null);
        camera.release();
        camera = null;
      try {
          server.close();
      } catch (IOException e) {
          e.printStackTrace();
      }
      Log.i("preview","closed");
  }

  // Called when holder has changed
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { 
       camera.startPreview();// camera frame preview starts when user launches application screen;
  }


    class receiveDataTask extends AsyncTask<Integer,String,Integer>
    {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //pd = ProgressDialog.show(getContext(), "Connection", "Waiting for the controller to connect", true, false);
        }
        @Override
        protected Integer doInBackground(Integer... params) {

            try {
                serialPort.open();
            }
            catch (Exception e)
            {
                doInBackground();
            }
            if (!serverRunning) {
                // get input stream
                try {
                    inputStream = video_receiver_socket.getInputStream();
                    Log.i("quad","input stream found");
                } catch (IOException e) {
                    Log.i("quad","input stream not found");
                }

                // create data input stream
                dataInputStream = new DataInputStream(inputStream);

                mainActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        text.setText("Received Message");
                        text.setVisibility(View.VISIBLE);
                        tv.setVisibility(View.VISIBLE);
                        //pd.dismiss();
                    }
                });



                // while client is still open, read and display message
                while (!video_receiver_socket.isClosed()) {
                    try {
                        message = dataInputStream.readUTF();
                        serialPort.write(message.getBytes());
                        publishProgress(message);
                    } catch (IOException e) {
                        break;
                    }
                    System.out.println("message send from client " + message);
                }

                // if out of loop, close server and client socket
                //try {
                    //server.close();
                    //video_receiver_socket.close();
                    //serverRunning = false;
                //} catch (IOException e) {
                    //e.printStackTrace();
                //}

                // if i didn't close the server and client is disconnected, try to accept again
                //if (!halt)
                //{
                    //mainActivity.runOnUiThread(new Runnable() {
                        //@Override
                        //public void run() {
                            //text.setText("Controller disconnected");
                            //tv.setVisibility(View.INVISIBLE);
                        //}
                    //});
                    //doInBackground();
                //}

                serverRunning = true;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            tv.setText(progress[0]);
            Toast.makeText(mainActivity.getApplicationContext(),"sent",Toast.LENGTH_LONG).show();
        }

    }


    public void startSerial() {
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                usbDevice = entry.getValue();
                //int deviceVID = usbDevice.getVendorId();

                PendingIntent pi = PendingIntent.getBroadcast(mainActivity, 0, new Intent(ACTION_USB_PERMISSION), 0);
                usbManager.requestPermission(usbDevice, pi);
                keep = false;
                if (!keep)
                    break;
            }
        }
    }


}