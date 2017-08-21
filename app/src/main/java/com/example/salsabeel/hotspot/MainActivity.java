package com.example.salsabeel.hotspot;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.spec.ECField;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity {

    Button startServer, stopServer ;
    TextView ipView, tv,text;

    WifiManager wifiManager;
    WifiApControl apControl;
    Switch hotspot;

    Preview preview;

    @SuppressLint("WifiManagerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preview = new Preview(getApplicationContext(),MainActivity.this);
        //((FrameLayout) findViewById(R.id.preview)).addView(preview);

        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        apControl = WifiApControl.getApControl(wifiManager);

        text = (TextView) findViewById(R.id.msgLabelView);
        tv = (TextView) findViewById(R.id.msgView);

        ipView = (TextView) findViewById(R.id.ipView);
        startServer = (Button) findViewById(R.id.servetBtn);
        startServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // get my hotspot name
                //Toast.makeText(getApplicationContext() ,apControl.getWifiApConfiguration().SSID,Toast.LENGTH_LONG).show();
                // get my hotspot password
                //Toast.makeText(getApplicationContext() ,apControl.getWifiApConfiguration().preSharedKey,Toast.LENGTH_LONG).show();

                // display server ip address
                //Toast.makeText(getApplicationContext(),getIpAddress(),Toast.LENGTH_LONG).show();
                ipView.setText(getIpAddress());

                //((FrameLayout) findViewById(R.id.preview)).addView(preview);
                // start receiving messages from the controller
                ((FrameLayout) findViewById(R.id.preview)).addView(preview);
                //new receiveDataTask().execute();



                v.setEnabled(false);
                stopServer.setEnabled(true);

            }

        });

        stopServer = (Button) findViewById(R.id.closeBtn);
        stopServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                startServer.setEnabled(true);
                ((FrameLayout) findViewById(R.id.preview)).removeView(preview);
            }
        });


        hotspot = (Switch) findViewById(R.id.switch1);
        if (apControl.isWifiApEnabled())
        {
            hotspot.setChecked(true);

            startServer.setEnabled(true);
        }
        hotspot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                {
                    // open settings for user to allow permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if ( ! Settings.System.canWrite(getApplicationContext())) {
                            Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                            goToSettings.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                            startActivity(goToSettings);
                        }
                    }
                    if (!apControl.isWifiApEnabled())
                        if ( wifiManager.isWifiEnabled() )
                        {
                            Toast.makeText(getApplicationContext(),"Please, turn OFF WIFI",Toast.LENGTH_LONG).show();
                            buttonView.setChecked(false);
                        }
                        else
                        {
                            // turn on hotspot
                            turnOnOffHotspot(getApplicationContext(),true);

                            startServer.setEnabled(true);
                        }

                }
                else
                {
                    // turn off hotspot
                    turnOnOffHotspot(getApplicationContext(),false);

                    startServer.setEnabled(false);

                    text.setVisibility(View.INVISIBLE);
                    tv.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    public String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress
                            .nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "Connect Controller to ip at: "
                                + inetAddress.getHostAddress();
                    }
                }
            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }
        return ip;
    }
    /**
     * Turn on or off Hotspot.
     *
     * @param context
     * @param isTurnToOn
     */
    public void turnOnOffHotspot(Context context, boolean isTurnToOn) {
        if (apControl != null) {
            apControl.setWifiApEnabled(apControl.getWifiApConfiguration(),
                    isTurnToOn);
        }
    }


}
